package com.thelightphone.games.sudoku

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewModelScope
import com.thelightphone.games.ActivePuzzleStore
import com.thelightphone.games.DailyLimitStore
import com.thelightphone.games.GameKeys
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class SudokuUiState {
    object CheckingLimit : SudokuUiState()
    object LimitReached : SudokuUiState()

    data class Playing(
        val puzzle: IntArray,
        val solution: IntArray,
        val entries: IntArray,
        val selectedIndex: Int?,
        val isSolved: Boolean,
        val remainingToday: Int,
        val showTossConfirm: Boolean = false,
    ) : SudokuUiState()
}

class SudokuScreenViewModel(
    private val dailyLimitStore: DailyLimitStore,
    private val activePuzzleStore: ActivePuzzleStore,
) : LightViewModel<Unit>() {

    private val _state = MutableStateFlow<SudokuUiState>(SudokuUiState.CheckingLimit)
    val state: StateFlow<SudokuUiState> = _state

    private var hasStarted = false

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        if (!hasStarted) {
            hasStarted = true
            resumeOrStartNewPuzzle()
        }
    }

    /**
     * If a puzzle was left unfinished, resume it for free (no daily-limit check at all -
     * you already paid for this puzzle when it was first generated). Only when there's
     * nothing to resume do we check the limit and generate a brand new one.
     */
    private fun resumeOrStartNewPuzzle() {
        viewModelScope.launch {
            val saved = activePuzzleStore.load(GameKeys.SUDOKU)?.let { decode(it) }
            if (saved != null) {
                val (puzzle, entries, solution) = saved
                _state.value = SudokuUiState.Playing(
                    puzzle = puzzle,
                    solution = solution,
                    entries = entries,
                    selectedIndex = null,
                    isSolved = entries.contentEquals(solution),
                    remainingToday = dailyLimitStore.remainingPlays(GameKeys.SUDOKU),
                )
                return@launch
            }

            generateAndShowNewPuzzle()
        }
    }

    private suspend fun generateAndShowNewPuzzle() {
        val allowed = dailyLimitStore.tryConsumePlay(GameKeys.SUDOKU)
        if (!allowed) {
            _state.value = SudokuUiState.LimitReached
            return
        }
        val remaining = dailyLimitStore.remainingPlays(GameKeys.SUDOKU)
        val board = withContext(Dispatchers.Default) {
            SudokuBoard.generate(SudokuDifficulty.entries.random())
        }
        val newState = SudokuUiState.Playing(
            puzzle = board.puzzle,
            solution = board.solution,
            entries = board.puzzle.copyOf(),
            selectedIndex = null,
            isSolved = false,
            remainingToday = remaining,
        )
        _state.value = newState
        persistActivePuzzle(newState)
    }

    /** Shows the "are you sure" prompt - doesn't toss anything yet. */
    fun requestToss() {
        val current = _state.value as? SudokuUiState.Playing ?: return
        if (current.isSolved) return
        _state.value = current.copy(showTossConfirm = true)
    }

    fun cancelToss() {
        val current = _state.value as? SudokuUiState.Playing ?: return
        _state.value = current.copy(showTossConfirm = false)
    }

    /**
     * The attempt stays spent either way - you already used one of today's plays the moment
     * this puzzle was generated, same as if you'd solved it. Since there's a resume feature,
     * it's fine to hand over a fresh puzzle immediately rather than sending the player back
     * to the home screen - they can always back out and return to it later if they want.
     */
    fun confirmToss() {
        val current = _state.value as? SudokuUiState.Playing ?: return
        if (current.isSolved) return
        viewModelScope.launch {
            activePuzzleStore.clear(GameKeys.SUDOKU)
            generateAndShowNewPuzzle()
        }
    }

    fun selectCell(index: Int) {
        val current = _state.value as? SudokuUiState.Playing ?: return
        if (current.puzzle[index] != 0) return // givens aren't editable
        _state.value = current.copy(selectedIndex = index)
    }

    fun enterDigit(digit: Int) {
        val current = _state.value as? SudokuUiState.Playing ?: return
        val index = current.selectedIndex ?: return
        if (current.puzzle[index] != 0) return

        val newEntries = current.entries.copyOf()
        newEntries[index] = digit
        val solved = newEntries.contentEquals(current.solution)
        val newState = current.copy(entries = newEntries, isSolved = solved)
        _state.value = newState

        if (solved) clearActivePuzzle() else persistActivePuzzle(newState)
    }

    fun clearSelected() {
        val current = _state.value as? SudokuUiState.Playing ?: return
        val index = current.selectedIndex ?: return
        if (current.puzzle[index] != 0) return

        val newEntries = current.entries.copyOf()
        newEntries[index] = 0
        val newState = current.copy(entries = newEntries, isSolved = false)
        _state.value = newState
        persistActivePuzzle(newState)
    }

    /**
     * Resets every entry back to the puzzle's original clues - a clean slate on the same
     * puzzle, distinct from tossing it for a different one entirely. Doesn't touch the daily
     * limit at all, since it's still the same puzzle you already paid an attempt for.
     */
    fun clearAllEntries() {
        val current = _state.value as? SudokuUiState.Playing ?: return
        if (current.isSolved) return

        val newState = current.copy(
            entries = current.puzzle.copyOf(),
            isSolved = false,
            selectedIndex = null,
        )
        _state.value = newState
        persistActivePuzzle(newState)
    }

    private fun persistActivePuzzle(state: SudokuUiState.Playing) {
        viewModelScope.launch { activePuzzleStore.save(GameKeys.SUDOKU, encode(state)) }
    }

    private fun clearActivePuzzle() {
        viewModelScope.launch { activePuzzleStore.clear(GameKeys.SUDOKU) }
    }

    private fun encode(state: SudokuUiState.Playing): String {
        fun IntArray.toDigits() = joinToString("") { it.toString() }
        return "${state.puzzle.toDigits()}|${state.entries.toDigits()}|${state.solution.toDigits()}"
    }

    /** Returns (puzzle, entries, solution), or null if the saved blob is missing/corrupt. */
    private fun decode(blob: String): Triple<IntArray, IntArray, IntArray>? {
        val parts = blob.split("|")
        if (parts.size != 3) return null
        fun String.toBoardOrNull(): IntArray? {
            if (length != 81) return null
            return try {
                IntArray(81) { i -> this[i].digitToInt() }
            } catch (e: NumberFormatException) {
                null
            }
        }
        val puzzle = parts[0].toBoardOrNull() ?: return null
        val entries = parts[1].toBoardOrNull() ?: return null
        val solution = parts[2].toBoardOrNull() ?: return null
        return Triple(puzzle, entries, solution)
    }
}

class SudokuScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, SudokuScreenViewModel>(sealedActivity) {

    override val viewModelClass: Class<SudokuScreenViewModel>
        get() = SudokuScreenViewModel::class.java

    override fun createViewModel(): SudokuScreenViewModel =
        SudokuScreenViewModel(
            dailyLimitStore = DailyLimitStore(lightContext.dataStore),
            activePuzzleStore = ActivePuzzleStore(lightContext.dataStore),
        )

    @Composable
    override fun Content() {
        val state by viewModel.state.collectAsState()
        val themeColors by LightThemeController.colors.collectAsState()

        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                val tossButton = (state as? SudokuUiState.Playing)?.let { s ->
                    if (s.isSolved) null else LightBarButton.LightIcon(icon = LightIcons.TRASH, sizeUnits = 1.7f, onClick = { viewModel.requestToss() })
                }
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(icon = LightIcons.BACK, onClick = { goBack() }),
                    center = LightTopBarCenter.Text("Sudoku"),
                    rightButton = tossButton,
                )

                when (val s = state) {
                    is SudokuUiState.CheckingLimit -> LoadingMessage("Loading...")
                    is SudokuUiState.LimitReached -> LimitReachedMessage()
                    is SudokuUiState.Playing -> PlayingContent(s, viewModel)
                }
            }
        }
    }
}

@Composable
private fun LoadingMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        LightText(text = text, variant = LightTextVariant.Copy, lighten = true)
    }
}

@Composable
private fun LimitReachedMessage() {
    Box(
        modifier = Modifier.fillMaxSize().padding(2f.gridUnitsAsDp()),
        contentAlignment = Alignment.Center,
    ) {
        Column {
            LightText(
                text = "That's today's ${DailyLimitStore.DEFAULT_DAILY_LIMIT} puzzles!",
                variant = LightTextVariant.Heading,
                align = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            LightText(
                text = "Come back tomorrow for more.",
                variant = LightTextVariant.Detail,
                lighten = true,
                align = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 0.5f.gridUnitsAsDp()),
            )
        }
    }
}

@Composable
private fun PlayingContent(state: SudokuUiState.Playing, viewModel: SudokuScreenViewModel) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 1f.gridUnitsAsDp())) {
            LightText(
                text = if (state.isSolved) "Solved!" else "${state.remainingToday} of ${DailyLimitStore.DEFAULT_DAILY_LIMIT} left today",
                variant = LightTextVariant.Detail,
                lighten = !state.isSolved,
                modifier = Modifier.padding(vertical = 0.5f.gridUnitsAsDp()),
            )

            // Weighting this box means it only gets whatever height is left over after the
            // number pad below claims its own (fixed) height - so the board always shrinks
            // to leave the pad visible and tappable, even on a near-square screen.
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                SudokuGrid(
                    state = state,
                    onCellClick = { index -> viewModel.selectCell(index) },
                    modifier = Modifier.fillMaxHeight(),
                )
            }

            if (!state.isSolved) {
                NumberPad(
                    onDigit = { digit -> viewModel.enterDigit(digit) },
                    onClear = { viewModel.clearSelected() },
                    onClearAll = { viewModel.clearAllEntries() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (state.showTossConfirm) {
            TossConfirmOverlay(
                onConfirm = { viewModel.confirmToss() },
                onCancel = { viewModel.cancelToss() },
            )
        }
    }
}

@Composable
private fun TossConfirmOverlay(onConfirm: () -> Unit, onCancel: () -> Unit) {
    val colors = LightThemeTokens.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LightText(
                text = "Toss this puzzle?",
                variant = LightTextVariant.Heading,
                align = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            LightText(
                text = "You won't get this attempt back.",
                variant = LightTextVariant.Detail,
                lighten = true,
                align = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 0.5f.gridUnitsAsDp()),
            )
            LightText(
                text = "Toss it",
                variant = LightTextVariant.Copy,
                align = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2f.gridUnitsAsDp())
                    .lightClickable(onClick = onConfirm)
                    .padding(vertical = 1f.gridUnitsAsDp()),
            )
            LightText(
                text = "Keep trying",
                variant = LightTextVariant.Copy,
                lighten = true,
                align = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 0.5f.gridUnitsAsDp())
                    .lightClickable(onClick = onCancel)
                    .padding(vertical = 1f.gridUnitsAsDp()),
            )
        }
    }
}

@Composable
private fun SudokuGrid(
    state: SudokuUiState.Playing,
    onCellClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LightThemeTokens.colors
    val givenColor = colors.content.toArgb()
    val enteredColor = colors.contentSecondary.toArgb()

    Box(
        modifier = modifier
            .aspectRatio(1f, matchHeightConstraintsFirst = true)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val cell = size.width / 9f
                    val col = (offset.x / cell).toInt().coerceIn(0, 8)
                    val row = (offset.y / cell).toInt().coerceIn(0, 8)
                    onCellClick(row * 9 + col)
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cell = size.width / 9f

            state.selectedIndex?.let { index ->
                val row = index / 9
                val col = index % 9
                drawRect(
                    color = colors.contentSecondary.copy(alpha = 0.30f),
                    topLeft = Offset(col * cell, row * cell),
                    size = Size(cell, cell),
                )
            }

            for (i in 0..9) {
                val thick = i % 3 == 0
                val strokeWidth = if (thick) 4f else 1.2f
                val lineColor = if (thick) colors.content else colors.contentSecondary
                drawLine(lineColor, Offset(0f, i * cell), Offset(size.width, i * cell), strokeWidth)
                drawLine(lineColor, Offset(i * cell, 0f), Offset(i * cell, size.height), strokeWidth)
            }

            val paint = android.graphics.Paint().apply {
                textAlign = android.graphics.Paint.Align.CENTER
                textSize = cell * 0.55f
                isAntiAlias = true
            }
            for (row in 0 until 9) {
                for (col in 0 until 9) {
                    val index = row * 9 + col
                    val value = state.entries[index]
                    if (value == 0) continue
                    val isGiven = state.puzzle[index] != 0
                    paint.color = if (isGiven) givenColor else enteredColor
                    drawContext.canvas.nativeCanvas.drawText(
                        value.toString(),
                        col * cell + cell / 2,
                        row * cell + cell * 0.7f,
                        paint,
                    )
                }
            }
        }
    }
}

@Composable
private fun NumberPad(
    onDigit: (Int) -> Unit,
    onClear: () -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(vertical = 0.75f.gridUnitsAsDp())) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            (1..5).forEach { digit ->
                LightText(
                    text = digit.toString(),
                    variant = LightTextVariant.Heading,
                    modifier = Modifier
                        .lightClickable { onDigit(digit) }
                        .padding(0.5f.gridUnitsAsDp()),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 0.5f.gridUnitsAsDp()),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            (6..9).forEach { digit ->
                LightText(
                    text = digit.toString(),
                    variant = LightTextVariant.Heading,
                    modifier = Modifier
                        .lightClickable { onDigit(digit) }
                        .padding(0.5f.gridUnitsAsDp()),
                )
            }
            LightIcon(
                icon = LightIcons.DELETE,
                modifier = Modifier
                    .combinedClickable(
                        interactionSource = null,
                        indication = null,
                        onClick = onClear,
                        onLongClick = onClearAll,
                    )
                    .padding(0.5f.gridUnitsAsDp()),
            )
        }
    }
}

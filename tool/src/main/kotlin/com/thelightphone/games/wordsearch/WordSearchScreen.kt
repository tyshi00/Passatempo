package com.thelightphone.games.wordsearch

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewModelScope
import com.thelightphone.games.ActivePuzzleStore
import com.thelightphone.games.DailyLimitStore
import com.thelightphone.games.GameKeys
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.rememberLightHapticTrigger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

sealed class WordSearchUiState {
    object CheckingLimit : WordSearchUiState()
    object LimitReached : WordSearchUiState()

    data class Playing(
        val puzzle: WordSearchPuzzle,
        val foundWords: Set<String>,
        val selection: List<Pair<Int, Int>>,
        val remainingToday: Int,
    ) : WordSearchUiState() {
        val isSolved: Boolean get() = foundWords.size == puzzle.words.size
    }
}

class WordSearchScreenViewModel(
    private val dailyLimitStore: DailyLimitStore,
    private val activePuzzleStore: ActivePuzzleStore,
    private val historyStore: WordSearchHistoryStore,
) : LightViewModel<Unit>() {

    private val _state = MutableStateFlow<WordSearchUiState>(WordSearchUiState.CheckingLimit)
    val state: StateFlow<WordSearchUiState> = _state

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
            val saved = activePuzzleStore.load(GameKeys.WORD_SEARCH)?.let { decode(it) }
            if (saved != null) {
                val (puzzle, foundWords) = saved
                _state.value = WordSearchUiState.Playing(
                    puzzle = puzzle,
                    foundWords = foundWords,
                    selection = emptyList(),
                    remainingToday = dailyLimitStore.remainingPlays(GameKeys.WORD_SEARCH),
                )
                return@launch
            }

            val allowed = dailyLimitStore.tryConsumePlay(GameKeys.WORD_SEARCH)
            if (!allowed) {
                _state.value = WordSearchUiState.LimitReached
                return@launch
            }
            val remaining = dailyLimitStore.remainingPlays(GameKeys.WORD_SEARCH)
            val puzzle = withContext(Dispatchers.Default) {
                val recentWords = historyStore.getRecentWords()
                WordSearchGenerator.generate(excludeWords = recentWords)
            }
            historyStore.recordUsedWords(puzzle.words.map { it.word })
            val newState = WordSearchUiState.Playing(
                puzzle = puzzle,
                foundWords = emptySet(),
                selection = emptyList(),
                remainingToday = remaining,
            )
            _state.value = newState
            persistActivePuzzle(newState)
        }
    }

    fun updateSelection(cells: List<Pair<Int, Int>>) {
        val current = _state.value as? WordSearchUiState.Playing ?: return
        _state.value = current.copy(selection = cells)
    }

    fun commitSelection() {
        val current = _state.value as? WordSearchUiState.Playing ?: return
        val cells = current.selection
        if (cells.size < 2) {
            _state.value = current.copy(selection = emptyList())
            return
        }
        val match = current.puzzle.words.firstOrNull { placed ->
            placed.cells == cells || placed.cells == cells.reversed()
        }
        val newFound = if (match != null) current.foundWords + match.word else current.foundWords
        val newState = current.copy(foundWords = newFound, selection = emptyList())
        _state.value = newState

        if (match != null) {
            if (newFound.size == current.puzzle.words.size) clearActivePuzzle() else persistActivePuzzle(newState)
        }
    }

    private fun persistActivePuzzle(state: WordSearchUiState.Playing) {
        viewModelScope.launch { activePuzzleStore.save(GameKeys.WORD_SEARCH, encode(state)) }
    }

    private fun clearActivePuzzle() {
        viewModelScope.launch { activePuzzleStore.clear(GameKeys.WORD_SEARCH) }
    }

    private fun encode(state: WordSearchUiState.Playing): String {
        val puzzle = state.puzzle
        val wordsPart = puzzle.words.joinToString(";") { w ->
            "${w.word}:${w.startRow},${w.startCol},${w.dRow},${w.dCol}"
        }
        val gridPart = puzzle.grid.joinToString("") { row -> String(row) }
        val foundPart = state.foundWords.joinToString(",")
        return listOf(puzzle.size.toString(), wordsPart, gridPart, foundPart).joinToString("\n")
    }

    /** Returns (puzzle, foundWords), or null if the saved blob is missing/corrupt. */
    private fun decode(blob: String): Pair<WordSearchPuzzle, Set<String>>? {
        return try {
            val lines = blob.split("\n")
            if (lines.size != 4) return null

            val size = lines[0].toInt()
            val words = if (lines[1].isEmpty()) {
                emptyList()
            } else {
                lines[1].split(";").map { entry ->
                    val (word, meta) = entry.split(":")
                    val (startRow, startCol, dRow, dCol) = meta.split(",").map { it.toInt() }
                    PlacedWord(word, startRow, startCol, dRow, dCol)
                }
            }

            val gridChars = lines[2]
            if (gridChars.length != size * size) return null
            val grid = Array(size) { r -> CharArray(size) { c -> gridChars[r * size + c] } }

            val foundWords = if (lines[3].isEmpty()) emptySet() else lines[3].split(",").toSet()

            WordSearchPuzzle(size, grid, words) to foundWords
        } catch (e: Exception) {
            null
        }
    }
}

class WordSearchScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, WordSearchScreenViewModel>(sealedActivity) {

    override val viewModelClass: Class<WordSearchScreenViewModel>
        get() = WordSearchScreenViewModel::class.java

    override fun createViewModel(): WordSearchScreenViewModel =
        WordSearchScreenViewModel(
            dailyLimitStore = DailyLimitStore(lightContext.dataStore),
            activePuzzleStore = ActivePuzzleStore(lightContext.dataStore),
            historyStore = WordSearchHistoryStore(lightContext.dataStore),
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
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(icon = LightIcons.BACK, onClick = { goBack() }),
                    center = LightTopBarCenter.Text("Word Search"),
                )

                when (val s = state) {
                    is WordSearchUiState.CheckingLimit -> LoadingMessage()
                    is WordSearchUiState.LimitReached -> LimitReachedMessage()
                    is WordSearchUiState.Playing -> PlayingContent(s, viewModel)
                }
            }
        }
    }
}

@Composable
private fun LoadingMessage() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        LightText(text = "Loading...", variant = LightTextVariant.Copy, lighten = true)
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
private fun PlayingContent(state: WordSearchUiState.Playing, viewModel: WordSearchScreenViewModel) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 1f.gridUnitsAsDp())) {
        LightText(
            text = if (state.isSolved) {
                "All words found!"
            } else {
                "${state.remainingToday} of ${DailyLimitStore.DEFAULT_DAILY_LIMIT} left today"
            },
            variant = LightTextVariant.Detail,
            lighten = !state.isSolved,
            modifier = Modifier.padding(vertical = 0.5f.gridUnitsAsDp()),
        )

        WordList(
            words = state.puzzle.words.map { it.word },
            foundWords = state.foundWords,
            modifier = Modifier.fillMaxWidth().padding(bottom = 0.5f.gridUnitsAsDp()),
        )

        // Weighting this box means the grid only gets whatever height is left over after
        // the status line and word list above claim their own (fixed) height - so it always
        // shrinks to fit rather than overflowing the bottom of the screen. The bottom padding
        // leaves a small margin so the grid never touches the very edge of the screen.
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(bottom = 1f.gridUnitsAsDp()),
            contentAlignment = Alignment.Center,
        ) {
            WordSearchGrid(
                state = state,
                onSelectionChange = { cells -> viewModel.updateSelection(cells) },
                onSelectionEnd = { viewModel.commitSelection() },
                modifier = Modifier.fillMaxHeight(),
            )
        }
    }
}

private const val WORD_LIST_COLUMNS = 4

@Composable
private fun WordList(words: List<String>, foundWords: Set<String>, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        words.chunked(WORD_LIST_COLUMNS).forEach { rowWords ->
            Row(modifier = Modifier.fillMaxWidth()) {
                rowWords.forEach { word ->
                    val found = word in foundWords
                    LightText(
                        text = word,
                        variant = LightTextVariant.Superfine,
                        lighten = found,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 0.15f.gridUnitsAsDp()),
                    )
                }
                // Pad out a short last row so earlier rows stay aligned instead of stretching wide.
                repeat(WORD_LIST_COLUMNS - rowWords.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun WordSearchGrid(
    state: WordSearchUiState.Playing,
    onSelectionChange: (List<Pair<Int, Int>>) -> Unit,
    onSelectionEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LightThemeTokens.colors
    val size = state.puzzle.size
    var startCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val letterColor = colors.content.toArgb()
    val triggerHaptic = rememberLightHapticTrigger()

    Canvas(
        modifier = modifier
            .aspectRatio(1f, matchHeightConstraintsFirst = true)
            .pointerInput(state.puzzle, triggerHaptic) {
                detectDragGestures(
                    onDragStart = { offset ->
                        triggerHaptic()
                        val cell = this.size.width / size.toFloat()
                        val col = (offset.x / cell).toInt().coerceIn(0, size - 1)
                        val row = (offset.y / cell).toInt().coerceIn(0, size - 1)
                        startCell = row to col
                        onSelectionChange(listOf(row to col))
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val start = startCell ?: return@detectDragGestures
                        val cell = this.size.width / size.toFloat()
                        val col = (change.position.x / cell).toInt().coerceIn(0, size - 1)
                        val row = (change.position.y / cell).toInt().coerceIn(0, size - 1)
                        onSelectionChange(straightLine(start, row to col))
                    },
                    onDragEnd = {
                        onSelectionEnd()
                        startCell = null
                    },
                )
            },
    ) {
        val cell = this.size.width / size

        state.puzzle.words.filter { it.word in state.foundWords }.forEach { word ->
            word.cells.forEach { (r, c) ->
                drawRect(
                    color = colors.contentSecondary.copy(alpha = 0.35f),
                    topLeft = Offset(c * cell, r * cell),
                    size = Size(cell, cell),
                )
            }
        }

        state.selection.forEach { (r, c) ->
            drawRect(
                color = colors.content.copy(alpha = 0.22f),
                topLeft = Offset(c * cell, r * cell),
                size = Size(cell, cell),
            )
        }

        for (i in 0..size) {
            drawLine(colors.contentSecondary, Offset(0f, i * cell), Offset(this.size.width, i * cell), 1f)
            drawLine(colors.contentSecondary, Offset(i * cell, 0f), Offset(i * cell, this.size.height), 1f)
        }

        val paint = android.graphics.Paint().apply {
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = cell * 0.5f
            isAntiAlias = true
            color = letterColor
        }
        for (row in 0 until size) {
            for (col in 0 until size) {
                drawContext.canvas.nativeCanvas.drawText(
                    state.puzzle.grid[row][col].toString(),
                    col * cell + cell / 2,
                    row * cell + cell * 0.68f,
                    paint,
                )
            }
        }
    }
}

/** Snaps a drag to the nearest straight line (horizontal, vertical, or diagonal) from [start]. */
private fun straightLine(start: Pair<Int, Int>, end: Pair<Int, Int>): List<Pair<Int, Int>> {
    val (r0, c0) = start
    val (r1, c1) = end
    val rowDiff = r1 - r0
    val colDiff = c1 - c0

    if (rowDiff == 0 && colDiff == 0) return listOf(start)

    val absRow = abs(rowDiff)
    val absCol = abs(colDiff)

    // Only straight 8-directional lines are valid selections; otherwise keep just the anchor cell.
    if (absRow != 0 && absCol != 0 && absRow != absCol) return listOf(start)

    val dr = if (rowDiff == 0) 0 else rowDiff / absRow
    val dc = if (colDiff == 0) 0 else colDiff / absCol
    val steps = maxOf(absRow, absCol)

    return (0..steps).map { i -> (r0 + dr * i) to (c0 + dc * i) }
}

package com.thelightphone.games.tictactoe

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewModelScope
import com.thelightphone.games.DailyPlaytimeStore
import com.thelightphone.games.GameBudgets
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val BUDGET_TICK_MS = 1000L

sealed class TicTacToeUiState {
    object CheckingBudget : TicTacToeUiState()
    object TimeUp : TicTacToeUiState()

    data class Playing(
        val board: List<TicTacToePlayer?>,
        val currentPlayer: TicTacToePlayer,
        val winner: TicTacToePlayer?,
        val isDraw: Boolean,
        val remainingSeconds: Int,
    ) : TicTacToeUiState()
}

class TicTacToeScreenViewModel(
    private val dailyPlaytimeStore: DailyPlaytimeStore,
) : LightViewModel<Unit>() {

    private val game = TicTacToeGame()
    private var budgetJob: Job? = null
    private var hasStarted = false

    private val _state = MutableStateFlow<TicTacToeUiState>(TicTacToeUiState.CheckingBudget)
    val state: StateFlow<TicTacToeUiState> = _state

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        if (!hasStarted) {
            hasStarted = true
            viewModelScope.launch {
                val remaining = dailyPlaytimeStore.remainingSeconds(GameKeys.TIC_TAC_TOE, GameBudgets.TIC_TAC_TOE_SECONDS)
                if (remaining <= 0) {
                    _state.value = TicTacToeUiState.TimeUp
                } else {
                    game.reset()
                    _state.value = snapshot(remaining)
                    startBudgetTicker(remaining)
                }
            }
        } else {
            val current = _state.value as? TicTacToeUiState.Playing
            if (current != null) startBudgetTicker(current.remainingSeconds)
        }
    }

    override fun onScreenHide(screen: SimpleLightScreen<Unit>) {
        super.onScreenHide(screen)
        budgetJob?.cancel()
    }

    override fun onAppPause() {
        super.onAppPause()
        budgetJob?.cancel()
    }

    private fun startBudgetTicker(startRemaining: Int) {
        budgetJob?.cancel()
        budgetJob = viewModelScope.launch {
            var remaining = startRemaining
            while (isActive && remaining > 0) {
                delay(BUDGET_TICK_MS)
                remaining = dailyPlaytimeStore.addUsage(
                    GameKeys.TIC_TAC_TOE,
                    elapsedSeconds = 1,
                    dailyBudgetSeconds = GameBudgets.TIC_TAC_TOE_SECONDS,
                )
                val current = _state.value as? TicTacToeUiState.Playing ?: continue
                _state.value = current.copy(remainingSeconds = remaining)
            }
            if (remaining <= 0) {
                _state.value = TicTacToeUiState.TimeUp
            }
        }
    }

    fun makeMove(index: Int) {
        val current = _state.value as? TicTacToeUiState.Playing ?: return
        if (current.winner != null || current.isDraw) {
            restart()
            return
        }
        if (!game.makeMove(index)) return
        _state.value = snapshot(current.remainingSeconds)
    }

    fun restart() {
        val current = _state.value as? TicTacToeUiState.Playing ?: return
        game.reset()
        _state.value = snapshot(current.remainingSeconds)
    }

    private fun snapshot(remainingSeconds: Int) = TicTacToeUiState.Playing(
        board = game.board.toList(),
        currentPlayer = game.currentPlayer,
        winner = game.winner,
        isDraw = game.isDraw,
        remainingSeconds = remainingSeconds,
    )
}

class TicTacToeScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, TicTacToeScreenViewModel>(sealedActivity) {

    override val viewModelClass: Class<TicTacToeScreenViewModel>
        get() = TicTacToeScreenViewModel::class.java

    override fun createViewModel(): TicTacToeScreenViewModel =
        TicTacToeScreenViewModel(DailyPlaytimeStore(lightContext.dataStore))

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
                val rightLabel = when (val s = state) {
                    is TicTacToeUiState.Playing -> formatClock(s.remainingSeconds)
                    else -> ""
                }
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(icon = LightIcons.BACK, onClick = { goBack() }),
                    center = LightTopBarCenter.Text("Tic-Tac-Toe"),
                    rightButton = LightBarButton.Text(text = rightLabel, onClick = null),
                )

                when (val s = state) {
                    is TicTacToeUiState.CheckingBudget -> LoadingMessage()
                    is TicTacToeUiState.TimeUp -> TimeUpMessage()
                    is TicTacToeUiState.Playing -> PlayingContent(s, viewModel)
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
private fun TimeUpMessage() {
    val minutes = GameBudgets.TIC_TAC_TOE_SECONDS / 60
    Box(
        modifier = Modifier.fillMaxSize().padding(2f.gridUnitsAsDp()),
        contentAlignment = Alignment.Center,
    ) {
        Column {
            LightText(
                text = "That's $minutes minutes of Tic-Tac-Toe for today!",
                variant = LightTextVariant.Heading,
                align = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            LightText(
                text = "Come back tomorrow for more.",
                variant = LightTextVariant.Detail,
                lighten = true,
                align = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 0.5f.gridUnitsAsDp()),
            )
        }
    }
}

@Composable
private fun PlayingContent(state: TicTacToeUiState.Playing, viewModel: TicTacToeScreenViewModel) {
    val gameOver = state.winner != null || state.isDraw

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 1f.gridUnitsAsDp())) {
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 1f.gridUnitsAsDp()),
            contentAlignment = Alignment.Center,
        ) {
            TicTacToeBoard(
                state = state,
                modifier = Modifier.fillMaxHeight(),
                onCellTap = { index -> viewModel.makeMove(index) },
            )
        }

        // Pass-and-play: both players can see the board, so no ongoing "X's turn" prompt is
        // needed - only surface a message once the game actually ends.
        LightText(
            text = if (gameOver) endGameText(state) else " ",
            variant = LightTextVariant.Detail,
            lighten = true,
            align = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 1f.gridUnitsAsDp()),
        )
    }
}

private fun endGameText(state: TicTacToeUiState.Playing): String = when {
    state.winner != null -> "${state.winner.name} wins! Tap the board to play again"
    state.isDraw -> "Draw - tap the board to play again"
    else -> ""
}

@Composable
private fun TicTacToeBoard(
    state: TicTacToeUiState.Playing,
    onCellTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LightThemeTokens.colors
    val gameOver = state.winner != null || state.isDraw
    val triggerHaptic = rememberLightHapticTrigger()

    Box(
        modifier = modifier
            .aspectRatio(1f, matchHeightConstraintsFirst = true)
            .pointerInput(gameOver, triggerHaptic) {
                detectTapGestures { offset ->
                    triggerHaptic()
                    if (gameOver) {
                        onCellTap(-1)
                        return@detectTapGestures
                    }
                    val cell = size.width / 3f
                    val col = (offset.x / cell).toInt().coerceIn(0, 2)
                    val row = (offset.y / cell).toInt().coerceIn(0, 2)
                    onCellTap(row * 3 + col)
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cell = size.width / 3f

            for (i in 1..2) {
                drawLine(colors.contentSecondary, Offset(i * cell, 0f), Offset(i * cell, size.height), 2f)
                drawLine(colors.contentSecondary, Offset(0f, i * cell), Offset(size.width, i * cell), 2f)
            }

            state.board.forEachIndexed { index, player ->
                if (player == null) return@forEachIndexed
                val row = index / 3
                val col = index % 3
                val cx = col * cell + cell / 2
                val cy = row * cell + cell / 2
                val mark = cell * 0.28f

                if (player == TicTacToePlayer.X) {
                    drawLine(colors.content, Offset(cx - mark, cy - mark), Offset(cx + mark, cy + mark), 5f)
                    drawLine(colors.content, Offset(cx + mark, cy - mark), Offset(cx - mark, cy + mark), 5f)
                } else {
                    drawCircle(colors.content, radius = mark, center = Offset(cx, cy), style = Stroke(width = 5f))
                }
            }
        }
    }
}

private fun formatClock(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

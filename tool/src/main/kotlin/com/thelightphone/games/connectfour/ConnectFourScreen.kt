package com.thelightphone.games.connectfour

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

sealed class ConnectFourUiState {
    object CheckingBudget : ConnectFourUiState()
    object TimeUp : ConnectFourUiState()

    data class Playing(
        val grid: List<List<ConnectFourPlayer?>>, // grid[row][col], row 0 = bottom
        val currentPlayer: ConnectFourPlayer,
        val winner: ConnectFourPlayer?,
        val isDraw: Boolean,
        val remainingSeconds: Int,
    ) : ConnectFourUiState()
}

class ConnectFourScreenViewModel(
    private val dailyPlaytimeStore: DailyPlaytimeStore,
) : LightViewModel<Unit>() {

    private val game = ConnectFourGame()
    private var budgetJob: Job? = null
    private var hasStarted = false

    private val _state = MutableStateFlow<ConnectFourUiState>(ConnectFourUiState.CheckingBudget)
    val state: StateFlow<ConnectFourUiState> = _state

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        if (!hasStarted) {
            hasStarted = true
            viewModelScope.launch {
                val remaining = dailyPlaytimeStore.remainingSeconds(GameKeys.CONNECT_FOUR, GameBudgets.CONNECT_FOUR_SECONDS)
                if (remaining <= 0) {
                    _state.value = ConnectFourUiState.TimeUp
                } else {
                    game.reset()
                    _state.value = snapshot(remaining)
                    startBudgetTicker(remaining)
                }
            }
        } else {
            val current = _state.value as? ConnectFourUiState.Playing
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
                    GameKeys.CONNECT_FOUR,
                    elapsedSeconds = 1,
                    dailyBudgetSeconds = GameBudgets.CONNECT_FOUR_SECONDS,
                )
                val current = _state.value as? ConnectFourUiState.Playing ?: continue
                _state.value = current.copy(remainingSeconds = remaining)
            }
            if (remaining <= 0) {
                _state.value = ConnectFourUiState.TimeUp
            }
        }
    }

    fun dropPiece(col: Int) {
        val current = _state.value as? ConnectFourUiState.Playing ?: return
        if (current.winner != null || current.isDraw) {
            restart()
            return
        }
        if (!game.dropPiece(col)) return
        _state.value = snapshot(current.remainingSeconds)
    }

    fun restart() {
        val current = _state.value as? ConnectFourUiState.Playing ?: return
        game.reset()
        _state.value = snapshot(current.remainingSeconds)
    }

    private fun snapshot(remainingSeconds: Int) = ConnectFourUiState.Playing(
        grid = game.grid.map { it.toList() },
        currentPlayer = game.currentPlayer,
        winner = game.winner,
        isDraw = game.isDraw,
        remainingSeconds = remainingSeconds,
    )
}

class ConnectFourScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, ConnectFourScreenViewModel>(sealedActivity) {

    override val viewModelClass: Class<ConnectFourScreenViewModel>
        get() = ConnectFourScreenViewModel::class.java

    override fun createViewModel(): ConnectFourScreenViewModel =
        ConnectFourScreenViewModel(DailyPlaytimeStore(lightContext.dataStore))

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
                    is ConnectFourUiState.Playing -> formatClock(s.remainingSeconds)
                    else -> ""
                }
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(icon = LightIcons.BACK, onClick = { goBack() }),
                    center = LightTopBarCenter.Text("Connect Four"),
                    rightButton = LightBarButton.Text(text = rightLabel, onClick = null),
                )

                when (val s = state) {
                    is ConnectFourUiState.CheckingBudget -> LoadingMessage()
                    is ConnectFourUiState.TimeUp -> TimeUpMessage()
                    is ConnectFourUiState.Playing -> PlayingContent(s, viewModel)
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
    val minutes = GameBudgets.CONNECT_FOUR_SECONDS / 60
    Box(
        modifier = Modifier.fillMaxSize().padding(2f.gridUnitsAsDp()),
        contentAlignment = Alignment.Center,
    ) {
        Column {
            LightText(
                text = "That's $minutes minutes of Connect Four for today!",
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
private fun PlayingContent(state: ConnectFourUiState.Playing, viewModel: ConnectFourScreenViewModel) {
    val gameOver = state.winner != null || state.isDraw

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 1f.gridUnitsAsDp())) {
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 1f.gridUnitsAsDp()),
            contentAlignment = Alignment.Center,
        ) {
            ConnectFourBoard(
                state = state,
                modifier = Modifier.fillMaxHeight(),
                onColumnTap = { col -> viewModel.dropPiece(col) },
            )
        }

        // Pass-and-play: both players can see the board, so no ongoing "Red's turn" prompt is
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

private fun endGameText(state: ConnectFourUiState.Playing): String {
    val winnerName = state.winner?.name?.lowercase()?.replaceFirstChar { it.uppercase() }
    return when {
        winnerName != null -> "$winnerName wins! Tap the board to play again"
        state.isDraw -> "Draw - tap the board to play again"
        else -> ""
    }
}

@Composable
private fun ConnectFourBoard(
    state: ConnectFourUiState.Playing,
    onColumnTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LightThemeTokens.colors
    val columns = state.grid.firstOrNull()?.size ?: 7
    val rows = state.grid.size
    val gameOver = state.winner != null || state.isDraw
    val triggerHaptic = rememberLightHapticTrigger()

    Box(
        modifier = modifier
            .aspectRatio(columns / rows.toFloat(), matchHeightConstraintsFirst = true)
            .pointerInput(gameOver, triggerHaptic) {
                detectTapGestures { offset ->
                    triggerHaptic()
                    if (gameOver) {
                        onColumnTap(-1)
                        return@detectTapGestures
                    }
                    val cellWidth = size.width / columns
                    val col = (offset.x / cellWidth).toInt().coerceIn(0, columns - 1)
                    onColumnTap(col)
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cellWidth = size.width / columns
            val cellHeight = size.height / rows
            val pieceRadius = minOf(cellWidth, cellHeight) * 0.38f

            for (i in 0..columns) {
                drawLine(colors.contentSecondary, Offset(i * cellWidth, 0f), Offset(i * cellWidth, size.height), 1.5f)
            }
            for (i in 0..rows) {
                drawLine(colors.contentSecondary, Offset(0f, i * cellHeight), Offset(size.width, i * cellHeight), 1.5f)
            }

            for (row in 0 until rows) {
                for (col in 0 until columns) {
                    val piece = state.grid[row][col] ?: continue
                    val displayRow = rows - 1 - row
                    val cx = col * cellWidth + cellWidth / 2
                    val cy = displayRow * cellHeight + cellHeight / 2

                    if (piece == ConnectFourPlayer.WHITE) {
                        drawCircle(colors.content, radius = pieceRadius, center = Offset(cx, cy))
                    } else {
                        drawCircle(colors.content, radius = pieceRadius, center = Offset(cx, cy), style = Stroke(width = 3f))
                    }
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

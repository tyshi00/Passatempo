package com.thelightphone.games.pong

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TICK_MS = 30L
private const val BUDGET_TICK_MS = 1000L

sealed class PongUiState {
    object CheckingBudget : PongUiState()
    object TimeUp : PongUiState()

    data class Playing(
        val playerPaddleX: Float,
        val aiPaddleX: Float,
        val ballX: Float,
        val ballY: Float,
        val fieldWidth: Float,
        val fieldHeight: Float,
        val playerScore: Int,
        val aiScore: Int,
        val remainingSeconds: Int,
        val gameStarted: Boolean = true,
    ) : PongUiState()
}

class PongScreenViewModel(
    private val dailyPlaytimeStore: DailyPlaytimeStore,
) : LightViewModel<Unit>() {

    private val game = PongGame()
    private var loopJob: Job? = null
    private var budgetJob: Job? = null
    private var hasStarted = false

    private val _state = MutableStateFlow<PongUiState>(PongUiState.CheckingBudget)
    val state: StateFlow<PongUiState> = _state

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        if (!hasStarted) {
            hasStarted = true
            viewModelScope.launch {
                val remaining = dailyPlaytimeStore.remainingSeconds(GameKeys.PONG, GameBudgets.PONG_SECONDS)
                if (remaining <= 0) {
                    _state.value = PongUiState.TimeUp
                } else {
                    _state.value = snapshot(remaining, gameStarted = false)
                    // Loop and budget ticker start on the first tap, not here - see nudgePaddle().
                }
            }
        } else {
            val current = _state.value
            if (current is PongUiState.Playing && current.gameStarted) {
                startLoop()
                startBudgetTicker(current.remainingSeconds)
            }
        }
    }

    override fun onScreenHide(screen: SimpleLightScreen<Unit>) {
        super.onScreenHide(screen)
        loopJob?.cancel()
        budgetJob?.cancel()
    }

    override fun onAppPause() {
        super.onAppPause()
        loopJob?.cancel()
        budgetJob?.cancel()
    }

    private fun startLoop() {
        loopJob?.cancel()
        loopJob = viewModelScope.launch {
            while (isActive) {
                val current = _state.value as? PongUiState.Playing
                if (current != null) {
                    game.tick()
                    _state.value = snapshot(current.remainingSeconds)
                }
                delay(TICK_MS)
            }
        }
    }

    private fun startBudgetTicker(startRemaining: Int) {
        budgetJob?.cancel()
        budgetJob = viewModelScope.launch {
            var remaining = startRemaining
            while (isActive && remaining > 0) {
                delay(BUDGET_TICK_MS)
                remaining = dailyPlaytimeStore.addUsage(
                    GameKeys.PONG,
                    elapsedSeconds = 1,
                    dailyBudgetSeconds = GameBudgets.PONG_SECONDS,
                )
                val current = _state.value as? PongUiState.Playing ?: continue
                _state.value = current.copy(remainingSeconds = remaining)
            }
            if (remaining <= 0) {
                loopJob?.cancel()
                _state.value = PongUiState.TimeUp
            }
        }
    }

    fun nudgePaddle(direction: PongPaddleDirection) {
        val current = _state.value as? PongUiState.Playing
        if (current != null && !current.gameStarted) {
            _state.value = current.copy(gameStarted = true)
            startLoop()
            startBudgetTicker(current.remainingSeconds)
        }
        game.nudgePlayerPaddle(direction)
    }

    private fun snapshot(remainingSeconds: Int, gameStarted: Boolean = true) = PongUiState.Playing(
        playerPaddleX = game.playerPaddleX,
        aiPaddleX = game.aiPaddleX,
        ballX = game.ballX,
        ballY = game.ballY,
        fieldWidth = game.width,
        fieldHeight = game.height,
        playerScore = game.playerScore,
        aiScore = game.aiScore,
        remainingSeconds = remainingSeconds,
        gameStarted = gameStarted,
    )
}

class PongScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, PongScreenViewModel>(sealedActivity) {

    override val viewModelClass: Class<PongScreenViewModel>
        get() = PongScreenViewModel::class.java

    override fun createViewModel(): PongScreenViewModel =
        PongScreenViewModel(DailyPlaytimeStore(lightContext.dataStore))

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
                    is PongUiState.Playing -> formatClock(s.remainingSeconds)
                    else -> ""
                }
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(icon = LightIcons.BACK, onClick = { goBack() }),
                    center = LightTopBarCenter.Text("Pong"),
                    rightButton = LightBarButton.Text(text = rightLabel, onClick = null),
                )

                when (val s = state) {
                    is PongUiState.CheckingBudget -> LoadingMessage()
                    is PongUiState.TimeUp -> TimeUpMessage()
                    is PongUiState.Playing -> PlayingContent(s, viewModel)
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
    val minutes = GameBudgets.PONG_SECONDS / 60
    Box(
        modifier = Modifier.fillMaxSize().padding(2f.gridUnitsAsDp()),
        contentAlignment = Alignment.Center,
    ) {
        Column {
            LightText(
                text = "That's $minutes minutes of Pong for today!",
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
private fun PlayingContent(state: PongUiState.Playing, viewModel: PongScreenViewModel) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 1f.gridUnitsAsDp())) {
        LightText(
            text = "${state.playerScore}-${state.aiScore}",
            variant = LightTextVariant.Detail,
            lighten = true,
            modifier = Modifier.padding(vertical = 0.5f.gridUnitsAsDp()),
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val direction = if (offset.x < size.width / 2f) PongPaddleDirection.LEFT else PongPaddleDirection.RIGHT
                        viewModel.nudgePaddle(direction)
                    }
                },
        ) {
            PongBoard(state = state)

            if (!state.gameStarted) {
                LightText(
                    text = "Tap to start",
                    variant = LightTextVariant.Heading,
                    align = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().align(Alignment.Center).offset(y = (-4f).gridUnitsAsDp()),
                )
            }
        }
    }
}

@Composable
private fun PongBoard(state: PongUiState.Playing) {
    val colors = LightThemeTokens.colors
    Canvas(modifier = Modifier.fillMaxSize()) {
        val scaleX = size.width / state.fieldWidth
        val scaleY = size.height / state.fieldHeight
        val paddleWidthUnits = 40f
        val paddleHeightUnits = 8f

        // Center line, purely decorative
        drawLine(
            color = colors.contentSecondary,
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = 1f,
        )

        // AI paddle (top)
        drawRect(
            color = colors.content,
            topLeft = Offset(state.aiPaddleX * scaleX, 12f * scaleY),
            size = Size(paddleWidthUnits * scaleX, paddleHeightUnits * scaleY),
        )

        // Player paddle (bottom)
        val playerPaddleYUnits = state.fieldHeight - 20f
        drawRect(
            color = colors.content,
            topLeft = Offset(state.playerPaddleX * scaleX, playerPaddleYUnits * scaleY),
            size = Size(paddleWidthUnits * scaleX, paddleHeightUnits * scaleY),
        )

        // Ball
        drawCircle(
            color = colors.content,
            radius = 5f * scaleX,
            center = Offset(state.ballX * scaleX, state.ballY * scaleY),
        )
    }
}

private fun formatClock(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

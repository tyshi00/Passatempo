package com.thelightphone.games.brickbreaker

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
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
import com.thelightphone.games.ControlModeStore
import com.thelightphone.games.DailyPlaytimeStore
import com.thelightphone.games.GameBudgets
import com.thelightphone.games.GameKeys
import com.thelightphone.games.PaddleControlMode
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
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TICK_MS = 30L
private const val BUDGET_TICK_MS = 1000L

sealed class BrickBreakerUiState {
    object CheckingBudget : BrickBreakerUiState()
    object TimeUp : BrickBreakerUiState()

    data class Playing(
        val paddleX: Float,
        val ballX: Float,
        val ballY: Float,
        val bricks: List<Pair<Brick, Rect>>,
        val fieldWidth: Float,
        val fieldHeight: Float,
        val score: Int,
        val isGameOver: Boolean,
        val isWon: Boolean,
        val remainingSeconds: Int,
        val gameStarted: Boolean = true,
        val controlMode: PaddleControlMode = PaddleControlMode.TAP,
    ) : BrickBreakerUiState()
}

class BrickBreakerScreenViewModel(
    private val dailyPlaytimeStore: DailyPlaytimeStore,
    private val controlModeStore: ControlModeStore,
) : LightViewModel<Unit>() {

    private val game = BrickBreakerGame()
    private var loopJob: Job? = null
    private var budgetJob: Job? = null
    private var hasStarted = false
    private var controlMode = PaddleControlMode.TAP

    private val _state = MutableStateFlow<BrickBreakerUiState>(BrickBreakerUiState.CheckingBudget)
    val state: StateFlow<BrickBreakerUiState> = _state

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        if (!hasStarted) {
            hasStarted = true
            viewModelScope.launch {
                controlMode = controlModeStore.get()
                val remaining = dailyPlaytimeStore.remainingSeconds(GameKeys.BRICK_BREAKER, GameBudgets.BRICK_BREAKER_SECONDS)
                if (remaining <= 0) {
                    _state.value = BrickBreakerUiState.TimeUp
                } else {
                    _state.value = snapshot(remaining, gameStarted = false)
                    // Loop and budget ticker start on the first tap, not here - see nudgePaddle().
                }
            }
        } else {
            viewModelScope.launch {
                controlMode = controlModeStore.get()
                val current = _state.value
                if (current is BrickBreakerUiState.Playing) {
                    _state.value = current.copy(controlMode = controlMode)
                    if (current.gameStarted) {
                        startLoop()
                        startBudgetTicker(current.remainingSeconds)
                    }
                }
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
                val current = _state.value as? BrickBreakerUiState.Playing
                if (current != null && !game.isGameOver && !game.isWon) {
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
                remaining = dailyPlaytimeStore.addUsage(GameKeys.BRICK_BREAKER, elapsedSeconds = 1, dailyBudgetSeconds = GameBudgets.BRICK_BREAKER_SECONDS)
                val current = _state.value as? BrickBreakerUiState.Playing ?: continue
                _state.value = current.copy(remainingSeconds = remaining)
            }
            if (remaining <= 0) {
                loopJob?.cancel()
                _state.value = BrickBreakerUiState.TimeUp
            }
        }
    }

    fun nudgePaddle(direction: PaddleDirection) {
        val current = _state.value as? BrickBreakerUiState.Playing
        if (current != null && !current.gameStarted) {
            _state.value = current.copy(gameStarted = true)
            startLoop()
            startBudgetTicker(current.remainingSeconds)
        }
        game.nudgePaddle(direction)
    }

    /** Drag mode: [centerX] is the touch position in game-field coordinates. */
    fun dragPaddle(centerX: Float) {
        val current = _state.value as? BrickBreakerUiState.Playing
        if (current != null && !current.gameStarted) {
            _state.value = current.copy(gameStarted = true)
            startLoop()
            startBudgetTicker(current.remainingSeconds)
        }
        game.setPaddleCenterX(centerX)
    }

    fun restart() {
        val current = _state.value as? BrickBreakerUiState.Playing ?: return
        game.reset()
        _state.value = snapshot(current.remainingSeconds)
    }

    private fun snapshot(remainingSeconds: Int, gameStarted: Boolean = true) = BrickBreakerUiState.Playing(
        paddleX = game.paddleX,
        ballX = game.ballX,
        ballY = game.ballY,
        bricks = game.standingBrickRects(),
        fieldWidth = game.width,
        fieldHeight = game.height,
        score = game.score,
        isGameOver = game.isGameOver,
        isWon = game.isWon,
        remainingSeconds = remainingSeconds,
        gameStarted = gameStarted,
        controlMode = controlMode,
    )
}

class BrickBreakerScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, BrickBreakerScreenViewModel>(sealedActivity) {

    override val viewModelClass: Class<BrickBreakerScreenViewModel>
        get() = BrickBreakerScreenViewModel::class.java

    override fun createViewModel(): BrickBreakerScreenViewModel =
        BrickBreakerScreenViewModel(
            dailyPlaytimeStore = DailyPlaytimeStore(lightContext.dataStore),
            controlModeStore = ControlModeStore(lightContext.dataStore),
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
                val rightLabel = when (val s = state) {
                    is BrickBreakerUiState.Playing -> "${s.score}  ${formatClock(s.remainingSeconds)}"
                    else -> ""
                }
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(icon = LightIcons.BACK, onClick = { goBack() }),
                    center = LightTopBarCenter.Text("Brick Breaker"),
                    rightButton = LightBarButton.Text(text = rightLabel, onClick = null),
                )

                when (val s = state) {
                    is BrickBreakerUiState.CheckingBudget -> LoadingMessage()
                    is BrickBreakerUiState.TimeUp -> TimeUpMessage()
                    is BrickBreakerUiState.Playing -> PlayingContent(s, viewModel)
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
    val minutes = GameBudgets.BRICK_BREAKER_SECONDS / 60
    Box(
        modifier = Modifier.fillMaxSize().padding(2f.gridUnitsAsDp()),
        contentAlignment = Alignment.Center,
    ) {
        Column {
            LightText(
                text = "That's $minutes minutes of Brick Breaker for today!",
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
private fun PlayingContent(state: BrickBreakerUiState.Playing, viewModel: BrickBreakerScreenViewModel) {
    val gestureModifier = if (state.controlMode == PaddleControlMode.DRAG) {
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                // awaitFirstDown fires on every touch, tap or drag alike - unlike
                // detectDragGestures' onDragStart, which only fires once real movement
                // is recognized, leaving a plain tap-and-release doing nothing at all.
                val down = awaitFirstDown(requireUnconsumed = false)
                viewModel.dragPaddle(down.position.x / size.width.toFloat() * state.fieldWidth)
                drag(down.id) { change ->
                    change.consume()
                    viewModel.dragPaddle(change.position.x / size.width.toFloat() * state.fieldWidth)
                }
            }
        }
    } else {
        Modifier.pointerInput(Unit) {
            detectTapGestures { offset ->
                val direction = if (offset.x < size.width / 2f) PaddleDirection.LEFT else PaddleDirection.RIGHT
                viewModel.nudgePaddle(direction)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(1f.gridUnitsAsDp())
            .then(gestureModifier),
    ) {
        BrickBreakerBoard(state = state)

        if (!state.gameStarted) {
            LightText(
                text = "Tap to start",
                variant = LightTextVariant.Heading,
                align = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().align(Alignment.Center).offset(y = 1.5f.gridUnitsAsDp()),
            )
        }

        if (state.isGameOver || state.isWon) {
            GameEndOverlay(won = state.isWon, score = state.score, onRestart = { viewModel.restart() })
        }
    }
}

@Composable
private fun BrickBreakerBoard(state: BrickBreakerUiState.Playing) {
    val colors = LightThemeTokens.colors
    Canvas(modifier = Modifier.fillMaxSize()) {
        val scaleX = size.width / state.fieldWidth
        val scaleY = size.height / state.fieldHeight

        // Bricks
        state.bricks.forEach { (_, rect) ->
            drawRect(
                color = colors.content,
                topLeft = Offset(rect.left * scaleX, rect.top * scaleY),
                size = Size((rect.right - rect.left) * scaleX, (rect.bottom - rect.top) * scaleY),
            )
        }

        // Paddle
        val paddleHeightUnits = 8f
        val paddleWidthUnits = 40f
        val paddleYUnits = state.fieldHeight - 20f
        drawRect(
            color = colors.content,
            topLeft = Offset(state.paddleX * scaleX, paddleYUnits * scaleY),
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

@Composable
private fun GameEndOverlay(won: Boolean, score: Int, onRestart: () -> Unit) {
    val colors = LightThemeTokens.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background.copy(alpha = 0.92f))
            .lightClickable(onClick = onRestart),
        contentAlignment = Alignment.Center,
    ) {
        Column {
            LightText(
                text = if (won) "All bricks cleared!" else "Game Over",
                variant = LightTextVariant.Heading,
                align = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            LightText(
                text = "Score: $score",
                variant = LightTextVariant.Copy,
                align = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 0.5f.gridUnitsAsDp()),
            )
            LightText(
                text = "Tap to play again",
                variant = LightTextVariant.Detail,
                lighten = true,
                align = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 1f.gridUnitsAsDp()),
            )
        }
    }
}

private fun formatClock(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

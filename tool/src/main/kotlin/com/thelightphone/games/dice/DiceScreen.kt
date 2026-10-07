package com.thelightphone.games.dice

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewModelScope
import com.thelightphone.games.DAILY_DICE_THROWS
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val ROLL_DURATION_MS = 700L
private const val ROLL_STEP_MS = 60L

sealed class DiceUiState {
    object CheckingLimit : DiceUiState()
    object LimitReached : DiceUiState()

    data class Ready(
        val face: Int,
        val isRolling: Boolean,
        val remainingToday: Int,
    ) : DiceUiState()
}

class DiceScreenViewModel(
    private val dailyLimitStore: DailyLimitStore,
) : LightViewModel<Unit>() {

    private val _state = MutableStateFlow<DiceUiState>(DiceUiState.CheckingLimit)
    val state: StateFlow<DiceUiState> = _state

    private var hasStarted = false
    private var rollJob: Job? = null

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        if (!hasStarted) {
            hasStarted = true
            viewModelScope.launch {
                val remaining = dailyLimitStore.remainingPlays(GameKeys.DICE, DAILY_DICE_THROWS)
                _state.value = if (remaining <= 0) {
                    DiceUiState.LimitReached
                } else {
                    DiceUiState.Ready(face = 1, isRolling = false, remainingToday = remaining)
                }
            }
        }
    }

    fun roll() {
        val current = _state.value as? DiceUiState.Ready ?: return
        if (current.isRolling || current.remainingToday <= 0) return

        rollJob?.cancel()
        rollJob = viewModelScope.launch {
            val allowed = dailyLimitStore.tryConsumePlay(GameKeys.DICE, DAILY_DICE_THROWS)
            if (!allowed) {
                _state.value = DiceUiState.LimitReached
                return@launch
            }
            val remaining = dailyLimitStore.remainingPlays(GameKeys.DICE, DAILY_DICE_THROWS)
            _state.value = current.copy(isRolling = true, remainingToday = remaining)

            var elapsed = 0L
            while (elapsed < ROLL_DURATION_MS) {
                delay(ROLL_STEP_MS)
                elapsed += ROLL_STEP_MS
                val spinning = _state.value as? DiceUiState.Ready ?: return@launch
                _state.value = spinning.copy(face = (1..6).random())
            }

            val settled = _state.value as? DiceUiState.Ready ?: return@launch
            _state.value = settled.copy(face = (1..6).random(), isRolling = false)
        }
    }
}

class DiceScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, DiceScreenViewModel>(sealedActivity) {

    override val viewModelClass: Class<DiceScreenViewModel>
        get() = DiceScreenViewModel::class.java

    override fun createViewModel(): DiceScreenViewModel =
        DiceScreenViewModel(DailyLimitStore(lightContext.dataStore))

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
                    center = LightTopBarCenter.Text("Dice"),
                )

                when (val s = state) {
                    is DiceUiState.CheckingLimit -> LoadingMessage()
                    is DiceUiState.LimitReached -> LimitReachedMessage()
                    is DiceUiState.Ready -> ReadyContent(s, viewModel)
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
                text = "That's today's $DAILY_DICE_THROWS throws!",
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
private fun ReadyContent(state: DiceUiState.Ready, viewModel: DiceScreenViewModel) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 1f.gridUnitsAsDp())) {
        LightText(
            text = "${state.remainingToday} of $DAILY_DICE_THROWS left today",
            variant = LightTextVariant.Detail,
            lighten = true,
            modifier = Modifier.padding(vertical = 0.5f.gridUnitsAsDp()),
        )

        val triggerHaptic = rememberLightHapticTrigger()
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(2f.gridUnitsAsDp())
                .pointerInput(state.remainingToday, triggerHaptic) {
                    detectTapGestures {
                        triggerHaptic()
                        viewModel.roll()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            DiceFace(
                face = state.face,
                modifier = Modifier.fillMaxWidth(0.6f).aspectRatio(1f),
            )
        }

        LightText(
            text = if (state.remainingToday > 0) "Tap to roll" else "No throws left today",
            variant = LightTextVariant.Detail,
            lighten = true,
            align = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 1f.gridUnitsAsDp()),
        )
    }
}

private val PIP_LAYOUTS: Map<Int, List<Pair<Int, Int>>> = mapOf(
    1 to listOf(1 to 1),
    2 to listOf(0 to 0, 2 to 2),
    3 to listOf(0 to 0, 1 to 1, 2 to 2),
    4 to listOf(0 to 0, 0 to 2, 2 to 0, 2 to 2),
    5 to listOf(0 to 0, 0 to 2, 1 to 1, 2 to 0, 2 to 2),
    6 to listOf(0 to 0, 0 to 2, 1 to 0, 1 to 2, 2 to 0, 2 to 2),
)

@Composable
private fun DiceFace(face: Int, modifier: Modifier = Modifier) {
    val colors = LightThemeTokens.colors
    Canvas(modifier = modifier) {
        val cornerRadius = size.width * 0.12f
        drawRoundRect(
            color = colors.content,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius, cornerRadius),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = size.width * 0.045f),
        )

        val cell = size.width / 3f
        val pipRadius = cell * 0.22f
        val pips = PIP_LAYOUTS[face] ?: emptyList()
        for ((row, col) in pips) {
            val cx = col * cell + cell / 2
            val cy = row * cell + cell / 2
            drawCircle(color = colors.content, radius = pipRadius, center = Offset(cx, cy))
        }
    }
}

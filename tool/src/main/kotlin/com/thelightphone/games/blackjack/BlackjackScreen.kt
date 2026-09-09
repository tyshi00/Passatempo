package com.thelightphone.games.blackjack

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.thelightphone.games.DailyPlaytimeStore
import com.thelightphone.games.GameBudgets
import com.thelightphone.games.GameKeys
import com.thelightphone.games.cards.Card
import com.thelightphone.games.cards.CardBack
import com.thelightphone.games.cards.CardView
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

private const val BUDGET_TICK_MS = 1000L
private const val HOLE_CARD_REVEAL_DELAY_MS = 500L
private const val DEALER_HIT_REVEAL_DELAY_MS = 600L
private const val RESULT_REVEAL_DELAY_MS = 500L

sealed class BlackjackUiState {
    object CheckingBudget : BlackjackUiState()
    object TimeUp : BlackjackUiState()

    data class Playing(
        val playerHand: List<Card>,
        val dealerHand: List<Card>, // already sliced to only the currently-visible dealer cards
        val dealerHoleRevealed: Boolean,
        val statusText: String, // blank while the dealer's turn is still animating in
        val isRoundOver: Boolean,
        val resultRevealed: Boolean, // true once the whole reveal sequence has finished
        val canDouble: Boolean,
        val remainingSeconds: Int,
        val stats: BlackjackStatsStore.Stats,
    ) : BlackjackUiState()
}

class BlackjackScreenViewModel(
    private val dailyPlaytimeStore: DailyPlaytimeStore,
    private val statsStore: BlackjackStatsStore,
) : LightViewModel<Unit>() {

    private var game = BlackjackGame.deal()
    private var dealerHoleRevealed = false
    private var dealerVisibleExtraCount = 0
    private var resultRevealed = true
    private var stats = BlackjackStatsStore.Stats()

    private var budgetJob: Job? = null
    private var revealJob: Job? = null
    private var hasStarted = false

    private val _state = MutableStateFlow<BlackjackUiState>(BlackjackUiState.CheckingBudget)
    val state: StateFlow<BlackjackUiState> = _state

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        if (!hasStarted) {
            hasStarted = true
            viewModelScope.launch {
                stats = statsStore.load()
                val remaining = dailyPlaytimeStore.remainingSeconds(GameKeys.BLACKJACK, GameBudgets.BLACKJACK_SECONDS)
                if (remaining <= 0) {
                    _state.value = BlackjackUiState.TimeUp
                } else {
                    startNewDeal(remaining)
                    startBudgetTicker(remaining)
                }
            }
        } else {
            val current = _state.value as? BlackjackUiState.Playing
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
                    GameKeys.BLACKJACK,
                    elapsedSeconds = 1,
                    dailyBudgetSeconds = GameBudgets.BLACKJACK_SECONDS,
                )
                val current = _state.value as? BlackjackUiState.Playing ?: continue
                _state.value = current.copy(remainingSeconds = remaining)
            }
            if (remaining <= 0) {
                revealJob?.cancel()
                _state.value = BlackjackUiState.TimeUp
            }
        }
    }

    private fun startNewDeal(remainingSeconds: Int) {
        revealJob?.cancel()
        game = BlackjackGame.deal()
        dealerHoleRevealed = false
        dealerVisibleExtraCount = 0
        resultRevealed = !game.isRoundOver
        _state.value = snapshot(remainingSeconds)
        if (game.isRoundOver) {
            beginReveal(remainingSeconds)
        }
    }

    fun hit() = applyAction { it.hit() }
    fun stand() = applyAction { it.stand() }
    fun doubleDown() = applyAction { it.doubleDown() }

    fun newDeal() {
        val remaining = (_state.value as? BlackjackUiState.Playing)?.remainingSeconds ?: return
        if (game.isRoundOver && !resultRevealed) return // don't let a tap skip the reveal animation
        startNewDeal(remaining)
    }

    private fun applyAction(action: (BlackjackGame) -> BlackjackGame) {
        val current = _state.value as? BlackjackUiState.Playing ?: return
        if (game.isRoundOver) return // round already over, only "Deal again" should act

        game = action(game)

        if (game.isRoundOver) {
            dealerHoleRevealed = false
            dealerVisibleExtraCount = 0
            resultRevealed = false
            _state.value = snapshot(current.remainingSeconds)
            beginReveal(current.remainingSeconds)
        } else {
            _state.value = snapshot(current.remainingSeconds)
        }
    }

    /**
     * The engine already computed the dealer's entire turn in one step (see
     * BlackjackGame.standInternal) - this reveals that same, already-decided outcome to the
     * player gradually instead of all at once: hole card flips first, then each of the
     * dealer's hit cards appears in turn, and only then does the win/lose/push result show.
     */
    private fun beginReveal(remainingSeconds: Int) {
        revealJob?.cancel()
        revealJob = viewModelScope.launch {
            delay(HOLE_CARD_REVEAL_DELAY_MS)
            dealerHoleRevealed = true
            _state.value = snapshot(remainingSeconds)

            val extraCount = (game.dealerHand.size - 2).coerceAtLeast(0)
            for (i in 1..extraCount) {
                delay(DEALER_HIT_REVEAL_DELAY_MS)
                dealerVisibleExtraCount = i
                _state.value = snapshot(remainingSeconds)
            }

            delay(RESULT_REVEAL_DELAY_MS)
            stats = when (game.state) {
                BlackjackState.PLAYER_BLACKJACK, BlackjackState.PLAYER_WINS -> statsStore.recordWin()
                BlackjackState.PLAYER_BUST, BlackjackState.DEALER_WINS -> statsStore.recordLoss()
                BlackjackState.PUSH -> statsStore.recordPush()
                else -> stats // round is guaranteed over here, so this branch shouldn't run
            }
            resultRevealed = true
            _state.value = snapshot(remainingSeconds)
        }
    }

    private fun snapshot(remainingSeconds: Int): BlackjackUiState.Playing {
        val visibleDealerHand = game.dealerHand.take(2) + game.dealerHand.drop(2).take(dealerVisibleExtraCount)
        return BlackjackUiState.Playing(
            playerHand = game.playerHand,
            dealerHand = visibleDealerHand,
            dealerHoleRevealed = dealerHoleRevealed,
            statusText = if (game.isRoundOver && !resultRevealed) "" else game.state.statusText(),
            isRoundOver = game.isRoundOver,
            resultRevealed = resultRevealed,
            canDouble = game.canDouble,
            remainingSeconds = remainingSeconds,
            stats = stats,
        )
    }
}

class BlackjackScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, BlackjackScreenViewModel>(sealedActivity) {

    override val viewModelClass: Class<BlackjackScreenViewModel>
        get() = BlackjackScreenViewModel::class.java

    override fun createViewModel(): BlackjackScreenViewModel = BlackjackScreenViewModel(
        dailyPlaytimeStore = DailyPlaytimeStore(lightContext.dataStore),
        statsStore = BlackjackStatsStore(lightContext.dataStore),
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
                    is BlackjackUiState.Playing -> formatClock(s.remainingSeconds)
                    else -> ""
                }
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(icon = LightIcons.BACK, onClick = { goBack() }),
                    center = LightTopBarCenter.Text("Blackjack"),
                    rightButton = LightBarButton.Text(text = rightLabel, onClick = null),
                )

                when (val s = state) {
                    is BlackjackUiState.CheckingBudget -> LoadingMessage()
                    is BlackjackUiState.TimeUp -> TimeUpMessage()
                    is BlackjackUiState.Playing -> PlayingContent(s, viewModel)
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
    val minutes = GameBudgets.BLACKJACK_SECONDS / 60
    Box(
        modifier = Modifier.fillMaxSize().padding(2f.gridUnitsAsDp()),
        contentAlignment = Alignment.Center,
    ) {
        Column {
            LightText(
                text = "That's $minutes minutes of Blackjack for today!",
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
private fun PlayingContent(state: BlackjackUiState.Playing, viewModel: BlackjackScreenViewModel) {
    val colors = LightThemeTokens.colors

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 1.5f.gridUnitsAsDp()),
    ) {
        LightText(
            text = "${state.stats.wins}W  ${state.stats.losses}L  ${state.stats.pushes}P",
            variant = LightTextVariant.Detail,
            lighten = true,
            align = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 0.5f.gridUnitsAsDp()),
        )

        // ---- Dealer ----
        LightText(
            text = "DEALER",
            variant = LightTextVariant.Detail,
            lighten = true,
            modifier = Modifier.padding(top = 1f.gridUnitsAsDp(), bottom = 0.5f.gridUnitsAsDp()),
        )
        BlackjackHand(
            cards = state.dealerHand,
            hideFirst = !state.dealerHoleRevealed,
            foreground = colors.content,
            background = colors.background,
        )
        LightText(
            text = if (state.dealerHoleRevealed) {
                "Total: ${handValue(state.dealerHand)}"
            } else {
                "Showing: ${cardValue(state.dealerHand.last())}"
            },
            variant = LightTextVariant.Detail,
            lighten = !state.dealerHoleRevealed,
            modifier = Modifier.padding(top = 0.5f.gridUnitsAsDp()),
        )

        // ---- Status ----
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            LightText(
                text = state.statusText,
                variant = if (state.isRoundOver) LightTextVariant.Heading else LightTextVariant.Detail,
                lighten = !state.isRoundOver,
                align = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // ---- Player ----
        LightText(
            text = "YOU",
            variant = LightTextVariant.Detail,
            lighten = true,
            modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp()),
        )
        BlackjackHand(
            cards = state.playerHand,
            hideFirst = false,
            foreground = colors.content,
            background = colors.background,
        )
        LightText(
            text = "Total: ${handValue(state.playerHand)}",
            variant = LightTextVariant.Detail,
            modifier = Modifier.padding(top = 0.5f.gridUnitsAsDp(), bottom = 1f.gridUnitsAsDp()),
        )

        // ---- Action buttons ----
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 1f.gridUnitsAsDp()),
            horizontalArrangement = Arrangement.Center,
        ) {
            if (state.isRoundOver) {
                ActionButton(
                    text = "Deal again",
                    enabled = state.resultRevealed,
                    foreground = colors.content,
                    subdued = colors.contentSecondary,
                    onClick = { viewModel.newDeal() },
                )
            } else {
                ActionButton(
                    text = "Hit",
                    enabled = true,
                    foreground = colors.content,
                    subdued = colors.contentSecondary,
                    onClick = { viewModel.hit() },
                    modifier = Modifier.padding(end = 1f.gridUnitsAsDp()),
                )
                ActionButton(
                    text = "Stand",
                    enabled = true,
                    foreground = colors.content,
                    subdued = colors.contentSecondary,
                    onClick = { viewModel.stand() },
                    modifier = Modifier.padding(end = 1f.gridUnitsAsDp()),
                )
                ActionButton(
                    text = "Double",
                    enabled = state.canDouble,
                    foreground = colors.content,
                    subdued = colors.contentSecondary,
                    onClick = { viewModel.doubleDown() },
                )
            }
        }
    }
}

// ---------------------------------------------------------------- card layout

@Composable
private fun BlackjackHand(
    cards: List<Card>,
    hideFirst: Boolean,
    foreground: Color,
    background: Color,
) {
    val cardW = 52.dp
    val cardH = cardW * 1.42f
    val maxFullCards = 5
    val overlap = if (cards.size > maxFullCards) cardW * 0.45f else 0.dp
    val spacing = if (cards.size > maxFullCards) cardW - overlap else cardW + 6.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .size(
                width = (spacing * cards.size + (if (cards.size > maxFullCards) overlap else 0.dp))
                    .coerceAtMost(360.dp),
                height = cardH,
            ),
    ) {
        cards.forEachIndexed { index, card ->
            val x = spacing * index
            if (index == 0 && hideFirst) {
                CardBack(
                    width = cardW,
                    height = cardH,
                    foreground = foreground,
                    background = background,
                    modifier = Modifier.offset(x = x),
                )
            } else {
                CardView(
                    card = card,
                    width = cardW,
                    height = cardH,
                    foreground = foreground,
                    background = background,
                    modifier = Modifier.offset(x = x),
                    showCenterGlyph = true,
                )
            }
        }
    }
}

@Composable
private fun ActionButton(
    text: String,
    enabled: Boolean,
    foreground: Color,
    subdued: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = if (enabled) foreground else subdued
    val shape = RoundedCornerShape(0.5f.gridUnitsAsDp())
    Box(
        modifier = modifier
            .clip(shape)
            .border(1.dp, color, shape)
            .then(if (enabled) Modifier.lightClickable(onClick = onClick) else Modifier)
            .padding(horizontal = 2f.gridUnitsAsDp(), vertical = 0.75f.gridUnitsAsDp()),
        contentAlignment = Alignment.Center,
    ) {
        LightText(
            text = text,
            variant = LightTextVariant.Detail,
            lighten = !enabled,
        )
    }
}

private fun formatClock(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

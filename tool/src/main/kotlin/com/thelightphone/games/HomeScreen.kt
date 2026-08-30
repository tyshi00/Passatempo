package com.thelightphone.games

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewModelScope
import com.thelightphone.games.blackjack.BlackjackScreen
import com.thelightphone.games.brickbreaker.BrickBreakerScreen
import com.thelightphone.games.connectfour.ConnectFourScreen
import com.thelightphone.games.dice.DiceScreen
import com.thelightphone.games.klondike.KlondikeScreen
import com.thelightphone.games.pong.PongScreen
import com.thelightphone.games.snake.SnakeScreen
import com.thelightphone.games.sudoku.SudokuScreen
import com.thelightphone.games.tictactoe.TicTacToeScreen
import com.thelightphone.games.wordsearch.WordSearchScreen
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HomeScreenViewModel(
    private val dailyLimitStore: DailyLimitStore,
    private val dailyPlaytimeStore: DailyPlaytimeStore,
    private val settingsStore: SettingsStore,
    private val gameVisibilityStore: GameVisibilityStore,
) : LightViewModel<Unit>() {

    data class UiState(
        val snakeRemainingSeconds: Int = GameBudgets.SNAKE_SECONDS,
        val brickBreakerRemainingSeconds: Int = GameBudgets.BRICK_BREAKER_SECONDS,
        val pongRemainingSeconds: Int = GameBudgets.PONG_SECONDS,
        val ticTacToeRemainingSeconds: Int = GameBudgets.TIC_TAC_TOE_SECONDS,
        val connectFourRemainingSeconds: Int = GameBudgets.CONNECT_FOUR_SECONDS,
        val sudokuRemaining: Int = DailyLimitStore.DEFAULT_DAILY_LIMIT,
        val wordSearchRemaining: Int = DailyLimitStore.DEFAULT_DAILY_LIMIT,
        val diceRemaining: Int = DAILY_DICE_THROWS,
        val blackjackRemainingSeconds: Int = GameBudgets.BLACKJACK_SECONDS,
        val klondikeRemainingSeconds: Int = GameBudgets.KLONDIKE_SECONDS,
        val gameVisibility: Map<String, Boolean> = emptyMap(),
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    private var hasAppliedSavedTheme = false

    // Refresh every time this screen becomes visible - including when a game
    // is backed out of, since LightActivity re-shows the previous screen.
    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        if (!hasAppliedSavedTheme) {
            hasAppliedSavedTheme = true
            applySavedTheme()
        }
        refresh()
    }

    private fun applySavedTheme() {
        viewModelScope.launch {
            if (settingsStore.isColorInverted()) {
                LightThemeController.setLightTheme()
            } else {
                LightThemeController.setDarkTheme()
            }
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            _state.value = UiState(
                snakeRemainingSeconds = dailyPlaytimeStore.remainingSeconds(GameKeys.SNAKE, GameBudgets.SNAKE_SECONDS),
                brickBreakerRemainingSeconds = dailyPlaytimeStore.remainingSeconds(
                    GameKeys.BRICK_BREAKER,
                    GameBudgets.BRICK_BREAKER_SECONDS,
                ),
                pongRemainingSeconds = dailyPlaytimeStore.remainingSeconds(GameKeys.PONG, GameBudgets.PONG_SECONDS),
                ticTacToeRemainingSeconds = dailyPlaytimeStore.remainingSeconds(
                    GameKeys.TIC_TAC_TOE,
                    GameBudgets.TIC_TAC_TOE_SECONDS,
                ),
                connectFourRemainingSeconds = dailyPlaytimeStore.remainingSeconds(
                    GameKeys.CONNECT_FOUR,
                    GameBudgets.CONNECT_FOUR_SECONDS,
                ),
                sudokuRemaining = dailyLimitStore.remainingPlays(GameKeys.SUDOKU),
                wordSearchRemaining = dailyLimitStore.remainingPlays(GameKeys.WORD_SEARCH),
                diceRemaining = dailyLimitStore.remainingPlays(GameKeys.DICE, DAILY_DICE_THROWS),
                blackjackRemainingSeconds = dailyPlaytimeStore.remainingSeconds(
                    GameKeys.BLACKJACK,
                    GameBudgets.BLACKJACK_SECONDS,
                ),
                klondikeRemainingSeconds = dailyPlaytimeStore.remainingSeconds(
                    GameKeys.KLONDIKE,
                    GameBudgets.KLONDIKE_SECONDS,
                ),
                gameVisibility = ALL_GAME_KEYS.associateWith { gameVisibilityStore.isVisible(it) },
            )
        }
    }
}

@InitialScreen
class HomeScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, HomeScreenViewModel>(sealedActivity) {

    override val viewModelClass: Class<HomeScreenViewModel>
        get() = HomeScreenViewModel::class.java

    override fun createViewModel(): HomeScreenViewModel =
        HomeScreenViewModel(
            dailyLimitStore = DailyLimitStore(lightContext.dataStore),
            dailyPlaytimeStore = DailyPlaytimeStore(lightContext.dataStore),
            settingsStore = SettingsStore(lightContext.dataStore),
            gameVisibilityStore = GameVisibilityStore(lightContext.dataStore),
        )

    @Composable
    override fun Content() {
        val state by viewModel.state.collectAsState()
        val themeColors by LightThemeController.colors.collectAsState()

        LightTheme(colors = themeColors) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    LightTopBar(center = LightTopBarCenter.Text("Passatempo"))

                    LightScrollView(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 2f.gridUnitsAsDp())
                            // Leaves room for the floating settings icon (2 gridUnits tall +
                            // 2 gridUnits of its own padding = 4) at the bottom-left, so the
                            // last row's text never sits underneath it.
                            .padding(bottom = 5f.gridUnitsAsDp()),
                    ) {
                        if (state.gameVisibility[GameKeys.SNAKE] != false) {
                            GameMenuRow(
                                title = gameDisplayName(GameKeys.SNAKE),
                                subtitle = describeRemainingTime(state.snakeRemainingSeconds),
                                enabled = state.snakeRemainingSeconds > 0,
                                onClick = { navigateTo(screenFactory = { activity -> SnakeScreen(activity) }) },
                            )
                        }
                        if (state.gameVisibility[GameKeys.BRICK_BREAKER] != false) {
                            GameMenuRow(
                                title = gameDisplayName(GameKeys.BRICK_BREAKER),
                                subtitle = describeRemainingTime(state.brickBreakerRemainingSeconds),
                                enabled = state.brickBreakerRemainingSeconds > 0,
                                onClick = { navigateTo(screenFactory = { activity -> BrickBreakerScreen(activity) }) },
                            )
                        }
                        if (state.gameVisibility[GameKeys.PONG] != false) {
                            GameMenuRow(
                                title = gameDisplayName(GameKeys.PONG),
                                subtitle = describeRemainingTime(state.pongRemainingSeconds),
                                enabled = state.pongRemainingSeconds > 0,
                                onClick = { navigateTo(screenFactory = { activity -> PongScreen(activity) }) },
                            )
                        }
                        if (state.gameVisibility[GameKeys.TIC_TAC_TOE] != false) {
                            GameMenuRow(
                                title = gameDisplayName(GameKeys.TIC_TAC_TOE),
                                subtitle = describeRemainingTime(state.ticTacToeRemainingSeconds),
                                enabled = state.ticTacToeRemainingSeconds > 0,
                                onClick = { navigateTo(screenFactory = { activity -> TicTacToeScreen(activity) }) },
                            )
                        }
                        if (state.gameVisibility[GameKeys.CONNECT_FOUR] != false) {
                            GameMenuRow(
                                title = gameDisplayName(GameKeys.CONNECT_FOUR),
                                subtitle = describeRemainingTime(state.connectFourRemainingSeconds),
                                enabled = state.connectFourRemainingSeconds > 0,
                                onClick = { navigateTo(screenFactory = { activity -> ConnectFourScreen(activity) }) },
                            )
                        }
                        if (state.gameVisibility[GameKeys.SUDOKU] != false) {
                            GameMenuRow(
                                title = gameDisplayName(GameKeys.SUDOKU),
                                subtitle = describeRemaining(state.sudokuRemaining, "puzzle"),
                                enabled = state.sudokuRemaining > 0,
                                onClick = { navigateTo(screenFactory = { activity -> SudokuScreen(activity) }) },
                            )
                        }
                        if (state.gameVisibility[GameKeys.WORD_SEARCH] != false) {
                            GameMenuRow(
                                title = gameDisplayName(GameKeys.WORD_SEARCH),
                                subtitle = describeRemaining(state.wordSearchRemaining, "puzzle"),
                                enabled = state.wordSearchRemaining > 0,
                                onClick = { navigateTo(screenFactory = { activity -> WordSearchScreen(activity) }) },
                            )
                        }
                        if (state.gameVisibility[GameKeys.BLACKJACK] != false) {
                            GameMenuRow(
                                title = gameDisplayName(GameKeys.BLACKJACK),
                                subtitle = describeRemainingTime(state.blackjackRemainingSeconds),
                                enabled = state.blackjackRemainingSeconds > 0,
                                onClick = { navigateTo(screenFactory = { activity -> BlackjackScreen(activity) }) },
                            )
                        }
                        if (state.gameVisibility[GameKeys.KLONDIKE] != false) {
                            GameMenuRow(
                                title = gameDisplayName(GameKeys.KLONDIKE),
                                subtitle = describeRemainingTime(state.klondikeRemainingSeconds),
                                enabled = state.klondikeRemainingSeconds > 0,
                                onClick = { navigateTo(screenFactory = { activity -> KlondikeScreen(activity) }) },
                            )
                        }
                        // Dice always stays last in the list, no matter what games get added
                        // above it later - keep any new game's row above this block, not below.
                        if (state.gameVisibility[GameKeys.DICE] != false) {
                            GameMenuRow(
                                title = gameDisplayName(GameKeys.DICE),
                                subtitle = describeRemaining(state.diceRemaining, "throw"),
                                enabled = state.diceRemaining > 0,
                                onClick = { navigateTo(screenFactory = { activity -> DiceScreen(activity) }) },
                            )
                        }
                    }
                }

                LightIcon(
                    icon = LightIcons.SETTINGS,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(2f.gridUnitsAsDp())
                        .lightClickable {
                            navigateTo(screenFactory = { activity -> SettingsScreen(activity) })
                        },
                )
            }
        }
    }
}

private fun describeRemaining(remaining: Int, noun: String): String = when (remaining) {
    0 -> "No ${noun}s left today - come back tomorrow"
    1 -> "1 $noun left today"
    else -> "$remaining ${noun}s left today"
}

private fun describeRemainingTime(remainingSeconds: Int): String {
    if (remainingSeconds <= 0) return "No time left today - come back tomorrow"
    val minutes = remainingSeconds / 60
    return when {
        minutes >= 1 -> "$minutes min left today"
        else -> "Under a minute left today"
    }
}

@Composable
private fun GameMenuRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .lightClickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 1.25f.gridUnitsAsDp()),
    ) {
        LightText(text = title, variant = LightTextVariant.Heading, lighten = !enabled)
        LightText(
            text = subtitle,
            variant = LightTextVariant.Detail,
            lighten = true,
            modifier = Modifier.padding(top = 0.25f.gridUnitsAsDp()),
        )
    }
}

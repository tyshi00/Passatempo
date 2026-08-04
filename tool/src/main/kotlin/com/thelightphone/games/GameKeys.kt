package com.thelightphone.games

/** Stable identifiers used as DataStore key prefixes. Do not rename once shipped. */
object GameKeys {
    const val SNAKE = "snake"
    const val BRICK_BREAKER = "brick_breaker"
    const val PONG = "pong"
    const val TIC_TAC_TOE = "tic_tac_toe"
    const val CONNECT_FOUR = "connect_four"
    const val SUDOKU = "sudoku"
    const val WORD_SEARCH = "word_search"
    const val DICE = "dice"
    const val BLACKJACK = "blackjack"
    const val KLONDIKE = "klondike"
}

/**
 * Every game, in the order they should appear on the home screen and in Settings.
 * DICE always stays last, no matter what gets added later - insert new games above it,
 * never below.
 */
val ALL_GAME_KEYS = listOf(
    GameKeys.SNAKE,
    GameKeys.BRICK_BREAKER,
    GameKeys.PONG,
    GameKeys.TIC_TAC_TOE,
    GameKeys.CONNECT_FOUR,
    GameKeys.SUDOKU,
    GameKeys.WORD_SEARCH,
    GameKeys.BLACKJACK,
    GameKeys.KLONDIKE,
    GameKeys.DICE,
)

/** Human-readable title for a game key - shared so Settings and the home screen never drift. */
fun gameDisplayName(gameKey: String): String = when (gameKey) {
    GameKeys.SNAKE -> "Snake"
    GameKeys.BRICK_BREAKER -> "Brick Breaker"
    GameKeys.PONG -> "Pong"
    GameKeys.TIC_TAC_TOE -> "Tic-Tac-Toe"
    GameKeys.CONNECT_FOUR -> "Connect Four"
    GameKeys.SUDOKU -> "Sudoku"
    GameKeys.WORD_SEARCH -> "Word Search"
    GameKeys.DICE -> "Dice"
    GameKeys.BLACKJACK -> "Blackjack"
    GameKeys.KLONDIKE -> "Klondike"
    else -> gameKey
}

/** Daily attempt limit for the Dice throw feature (not a time budget - each throw is one attempt). */
const val DAILY_DICE_THROWS = 10

/** Per-game daily time budgets - kept separate rather than one shared default. */
object GameBudgets {
    const val SNAKE_SECONDS = 15 * 60
    const val BRICK_BREAKER_SECONDS = 15 * 60
    const val PONG_SECONDS = 15 * 60
    const val TIC_TAC_TOE_SECONDS = 20 * 60
    const val CONNECT_FOUR_SECONDS = 20 * 60
    const val BLACKJACK_SECONDS = 10 * 60
    const val KLONDIKE_SECONDS = 30 * 60
}

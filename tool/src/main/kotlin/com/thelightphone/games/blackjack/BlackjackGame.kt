package com.thelightphone.games.blackjack

import com.thelightphone.games.cards.Card
import com.thelightphone.games.cards.freshDeck
import kotlin.random.Random

/**
 * Blackjack game engine. Immutable - every action returns a new state.
 *
 * Rules:
 *  - Single 52-card deck, reshuffled each round.
 *  - Dealer hits on soft 16 and below, stands on hard/soft 17+.
 *  - No betting, no split, no insurance.
 *  - Double down allowed only on the initial two-card hand.
 *  - Ace counts as 11 or 1 automatically (best value <= 21).
 *  - Natural 21 (Ace + 10-value) on first two cards = Blackjack.
 */

enum class BlackjackState {
    PLAYER_TURN,
    DEALER_TURN,
    PLAYER_BLACKJACK,
    PLAYER_WINS,
    DEALER_WINS,
    PLAYER_BUST,
    PUSH,
}

data class BlackjackGame(
    val deck: List<Card>,
    val playerHand: List<Card>,
    val dealerHand: List<Card>,
    val dealerHoleRevealed: Boolean = false,
    val state: BlackjackState = BlackjackState.PLAYER_TURN,
    val doubled: Boolean = false,
) {
    val canDouble: Boolean
        get() = state == BlackjackState.PLAYER_TURN &&
            playerHand.size == 2 && !doubled

    val isRoundOver: Boolean
        get() = state != BlackjackState.PLAYER_TURN &&
            state != BlackjackState.DEALER_TURN

    companion object {
        fun deal(seed: Long = System.currentTimeMillis()): BlackjackGame {
            val shuffled = freshDeck().shuffled(Random(seed)).toMutableList()
            val playerHand = listOf(shuffled.removeAt(0), shuffled.removeAt(0))
            val dealerHand = listOf(shuffled.removeAt(0), shuffled.removeAt(0))

            val playerScore = handValue(playerHand)
            val dealerScore = handValue(dealerHand)

            // Check for naturals
            val state = when {
                playerScore == 21 && dealerScore == 21 -> BlackjackState.PUSH
                playerScore == 21 -> BlackjackState.PLAYER_BLACKJACK
                else -> BlackjackState.PLAYER_TURN
            }

            return BlackjackGame(
                deck = shuffled,
                playerHand = playerHand,
                dealerHand = dealerHand,
                dealerHoleRevealed = state != BlackjackState.PLAYER_TURN,
                state = state,
            )
        }
    }
}

// ---------------------------------------------------------------- scoring

/** Value of a card in Blackjack. Face cards = 10, Ace = 11 (adjusted later). */
fun cardValue(card: Card): Int = when {
    card.rank == 1 -> 11  // Ace
    card.rank >= 10 -> 10 // J, Q, K
    else -> card.rank
}

/**
 * Best hand value <= 21 if possible. Aces are counted as 11 first,
 * then reduced to 1 one at a time until the total is <= 21 or all
 * aces are reduced.
 */
fun handValue(hand: List<Card>): Int {
    var total = hand.sumOf { cardValue(it) }
    var aces = hand.count { it.rank == 1 }
    while (total > 21 && aces > 0) {
        total -= 10
        aces--
    }
    return total
}

/** True if the hand contains an ace counted as 11. */
fun isSoft(hand: List<Card>): Boolean {
    val total = hand.sumOf { cardValue(it) }
    val aces = hand.count { it.rank == 1 }
    var reduced = 0
    var t = total
    while (t > 21 && reduced < aces) { t -= 10; reduced++ }
    return reduced < aces && t <= 21
}

/** The dealer's visible card value (second card is the up-card). */
fun BlackjackGame.dealerShowing(): Int = cardValue(dealerHand.last())

// ---------------------------------------------------------------- actions

/** Player takes a card. */
fun BlackjackGame.hit(): BlackjackGame {
    if (state != BlackjackState.PLAYER_TURN) return this
    val remaining = deck.toMutableList()
    val newCard = remaining.removeAt(0)
    val newHand = playerHand + newCard
    val score = handValue(newHand)

    return if (score > 21) {
        copy(
            deck = remaining,
            playerHand = newHand,
            dealerHoleRevealed = true,
            state = BlackjackState.PLAYER_BUST,
        )
    } else if (score == 21) {
        // Auto-stand on 21
        copy(deck = remaining, playerHand = newHand)
            .standInternal()
    } else {
        copy(
            deck = remaining,
            playerHand = newHand,
        )
    }
}

/** Player stands - dealer plays out. */
fun BlackjackGame.stand(): BlackjackGame {
    if (state != BlackjackState.PLAYER_TURN) return this
    return standInternal()
}

/** Player doubles down - one more card, then stand. */
fun BlackjackGame.doubleDown(): BlackjackGame {
    if (!canDouble) return this
    val remaining = deck.toMutableList()
    val newCard = remaining.removeAt(0)
    val newHand = playerHand + newCard
    val score = handValue(newHand)

    return if (score > 21) {
        copy(
            deck = remaining,
            playerHand = newHand,
            dealerHoleRevealed = true,
            state = BlackjackState.PLAYER_BUST,
            doubled = true,
        )
    } else {
        copy(
            deck = remaining,
            playerHand = newHand,
            doubled = true,
        ).standInternal()
    }
}

/**
 * Internal: dealer reveals hole card and hits until 17+, then resolve.
 *
 * This computes the dealer's whole turn in one step - the *result* is correct and
 * unchanged from the original engine. Screen-side, BlackjackScreenViewModel takes this
 * final dealerHand and reveals it to the player one card at a time with pauses in between,
 * rather than showing the fully-resolved hand instantly - see its `beginDealerReveal`.
 */
private fun BlackjackGame.standInternal(): BlackjackGame {
    val remaining = deck.toMutableList()
    val dealer = dealerHand.toMutableList()

    // Dealer draws until hard 17+ or any 17+
    while (handValue(dealer) < 17) {
        dealer.add(remaining.removeAt(0))
    }

    val playerScore = handValue(playerHand)
    val dealerScore = handValue(dealer)

    val result = when {
        dealerScore > 21 -> BlackjackState.PLAYER_WINS
        dealerScore > playerScore -> BlackjackState.DEALER_WINS
        dealerScore < playerScore -> BlackjackState.PLAYER_WINS
        else -> BlackjackState.PUSH
    }

    return copy(
        deck = remaining,
        dealerHand = dealer,
        dealerHoleRevealed = true,
        state = result,
    )
}

// ---------------------------------------------------------------- display helpers

fun BlackjackState.statusText(): String = when (this) {
    BlackjackState.PLAYER_TURN -> "Your turn"
    BlackjackState.DEALER_TURN -> "Dealer's turn"
    BlackjackState.PLAYER_BLACKJACK -> "Blackjack!"
    BlackjackState.PLAYER_WINS -> "You win"
    BlackjackState.DEALER_WINS -> "Dealer wins"
    BlackjackState.PLAYER_BUST -> "Bust!"
    BlackjackState.PUSH -> "Push"
}

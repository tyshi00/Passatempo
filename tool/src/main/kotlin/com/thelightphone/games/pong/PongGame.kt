package com.thelightphone.games.pong

enum class PongPaddleDirection { LEFT, RIGHT }

/**
 * Solo Pong: you control the bottom paddle (tap-nudge, same control model as Brick Breaker),
 * the top paddle is a simple AI that tracks the ball's x-position at a capped speed - fast
 * enough to be a real opponent, slow enough to be beatable. No fixed win condition; score
 * just keeps climbing on both sides until the player's time budget runs out, matching how
 * Snake plays (no artificial "game over" screen breaking up the flow).
 */
class PongGame(
    val width: Float = 200f,
    val height: Float = 320f,
) {
    companion object {
        private const val PADDLE_WIDTH = 40f
        private const val PADDLE_HEIGHT = 8f
        private const val PADDLE_NUDGE_DISTANCE = 16f
        private const val AI_PADDLE_SPEED = 2.6f
        private const val BALL_RADIUS = 5f
        private const val BALL_SPEED = 3.0f
    }

    private val playerPaddleY = height - 20f
    private val aiPaddleY = 12f

    var playerPaddleX: Float = (width - PADDLE_WIDTH) / 2f
        private set
    var aiPaddleX: Float = (width - PADDLE_WIDTH) / 2f
        private set

    var ballX: Float = width / 2f
        private set
    var ballY: Float = height / 2f
        private set
    private var ballDx: Float = BALL_SPEED
    private var ballDy: Float = BALL_SPEED

    var playerScore: Int = 0
        private set
    var aiScore: Int = 0
        private set

    fun nudgePlayerPaddle(direction: PongPaddleDirection) {
        val delta = when (direction) {
            PongPaddleDirection.LEFT -> -PADDLE_NUDGE_DISTANCE
            PongPaddleDirection.RIGHT -> PADDLE_NUDGE_DISTANCE
        }
        playerPaddleX = (playerPaddleX + delta).coerceIn(0f, width - PADDLE_WIDTH)
    }

    /** Moves the player's paddle so its center sits under [centerX] (drag mode). */
    fun setPlayerPaddleCenterX(centerX: Float) {
        playerPaddleX = (centerX - PADDLE_WIDTH / 2f).coerceIn(0f, width - PADDLE_WIDTH)
    }

    fun tick() {
        moveAiPaddle()
        moveBall()
    }

    private fun moveAiPaddle() {
        val paddleCenter = aiPaddleX + PADDLE_WIDTH / 2f
        val delta = (ballX - paddleCenter).coerceIn(-AI_PADDLE_SPEED, AI_PADDLE_SPEED)
        aiPaddleX = (aiPaddleX + delta).coerceIn(0f, width - PADDLE_WIDTH)
    }

    private fun moveBall() {
        var newX = ballX + ballDx
        var newY = ballY + ballDy

        if (newX - BALL_RADIUS < 0f) {
            newX = BALL_RADIUS
            ballDx = -ballDx
        } else if (newX + BALL_RADIUS > width) {
            newX = width - BALL_RADIUS
            ballDx = -ballDx
        }

        // Player paddle (bottom)
        if (ballDy > 0 &&
            newY + BALL_RADIUS >= playerPaddleY &&
            newY - BALL_RADIUS <= playerPaddleY + PADDLE_HEIGHT &&
            newX + BALL_RADIUS >= playerPaddleX &&
            newX - BALL_RADIUS <= playerPaddleX + PADDLE_WIDTH
        ) {
            newY = playerPaddleY - BALL_RADIUS
            val hitOffset = ((newX - playerPaddleX) / PADDLE_WIDTH - 0.5f) * 2f
            ballDx = BALL_SPEED * hitOffset
            ballDy = -kotlin.math.abs(ballDy)
        }

        // AI paddle (top)
        if (ballDy < 0 &&
            newY - BALL_RADIUS <= aiPaddleY + PADDLE_HEIGHT &&
            newY + BALL_RADIUS >= aiPaddleY &&
            newX + BALL_RADIUS >= aiPaddleX &&
            newX - BALL_RADIUS <= aiPaddleX + PADDLE_WIDTH
        ) {
            newY = aiPaddleY + PADDLE_HEIGHT + BALL_RADIUS
            val hitOffset = ((newX - aiPaddleX) / PADDLE_WIDTH - 0.5f) * 2f
            ballDx = BALL_SPEED * hitOffset
            ballDy = kotlin.math.abs(ballDy)
        }

        ballX = newX
        ballY = newY

        if (newY - BALL_RADIUS > height) {
            aiScore++
            resetBall(towardPlayer = false)
        } else if (newY + BALL_RADIUS < 0f) {
            playerScore++
            resetBall(towardPlayer = true)
        }
    }

    private fun resetBall(towardPlayer: Boolean) {
        ballX = width / 2f
        ballY = height / 2f
        ballDx = BALL_SPEED
        ballDy = if (towardPlayer) BALL_SPEED else -BALL_SPEED
    }

    fun reset() {
        playerPaddleX = (width - PADDLE_WIDTH) / 2f
        aiPaddleX = (width - PADDLE_WIDTH) / 2f
        playerScore = 0
        aiScore = 0
        resetBall(towardPlayer = true)
    }
}

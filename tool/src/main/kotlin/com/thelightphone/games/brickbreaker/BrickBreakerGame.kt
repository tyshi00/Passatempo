package com.thelightphone.games.brickbreaker

enum class PaddleDirection { LEFT, RIGHT }

data class Brick(val row: Int, val col: Int, var destroyed: Boolean = false)

/**
 * Classic brick-breaker physics in a fixed logical coordinate space (independent of
 * actual screen pixels - the screen just scales this at draw time). Framework-agnostic
 * so it's testable and reusable outside Compose, matching SnakeGame's approach.
 *
 * Paddle control is tap-driven, not drag-driven: each tap moves the paddle a fixed,
 * small distance immediately, then it stops on its own - there's no persistent "keep
 * gliding until told to stop" state, so a player never overshoots because they reacted
 * a moment too late.
 */
class BrickBreakerGame(
    val width: Float = 200f,
    val height: Float = 320f,
    private val brickRows: Int = 5,
    private val brickCols: Int = 8,
) {
    companion object {
        private const val PADDLE_WIDTH = 40f
        private const val PADDLE_HEIGHT = 8f
        private const val PADDLE_NUDGE_DISTANCE = 16f
        private const val BALL_RADIUS = 5f
        private const val BALL_SPEED = 3.2f
        private const val BRICK_TOP_MARGIN = 24f
        private const val BRICK_AREA_HEIGHT_FRACTION = 0.35f
        private const val BRICK_GAP = 4f
    }

    private val paddleY = height - 20f

    var paddleX: Float = (width - PADDLE_WIDTH) / 2f
        private set

    var ballX: Float = width / 2f
        private set
    var ballY: Float = paddleY - BALL_RADIUS - 1f
        private set
    private var ballDx: Float = BALL_SPEED
    private var ballDy: Float = -BALL_SPEED

    var bricks: List<Brick> = buildBricks()
        private set

    var score: Int = 0
        private set
    var isGameOver: Boolean = false
        private set
    var isWon: Boolean = false
        private set

    private fun buildBricks(): List<Brick> =
        (0 until brickRows).flatMap { row -> (0 until brickCols).map { col -> Brick(row, col) } }

    private fun brickWidth() = (width - BRICK_GAP * (brickCols + 1)) / brickCols
    private fun brickHeight() = (height * BRICK_AREA_HEIGHT_FRACTION) / brickRows

    private fun brickRect(brick: Brick): Rect {
        val bw = brickWidth()
        val bh = brickHeight()
        val left = BRICK_GAP + brick.col * (bw + BRICK_GAP)
        val top = BRICK_TOP_MARGIN + brick.row * (bh + BRICK_GAP)
        return Rect(left, top, left + bw, top + bh)
    }

    /** Cell rectangles for every brick still standing - what the UI actually draws. */
    fun standingBrickRects(): List<Pair<Brick, Rect>> =
        bricks.filter { !it.destroyed }.map { it to brickRect(it) }

    /** Moves the paddle one fixed step immediately. Called once per tap, not per tick. */
    fun nudgePaddle(direction: PaddleDirection) {
        if (isGameOver || isWon) return
        val delta = when (direction) {
            PaddleDirection.LEFT -> -PADDLE_NUDGE_DISTANCE
            PaddleDirection.RIGHT -> PADDLE_NUDGE_DISTANCE
        }
        paddleX = (paddleX + delta).coerceIn(0f, width - PADDLE_WIDTH)
    }

    /** Moves the paddle so its center sits under [centerX] (drag mode) - clamped to the field. */
    fun setPaddleCenterX(centerX: Float) {
        if (isGameOver || isWon) return
        paddleX = (centerX - PADDLE_WIDTH / 2f).coerceIn(0f, width - PADDLE_WIDTH)
    }

    fun tick() {
        if (isGameOver || isWon) return
        moveBall()
    }

    private fun moveBall() {
        var newX = ballX + ballDx
        var newY = ballY + ballDy

        // Walls
        if (newX - BALL_RADIUS < 0f) {
            newX = BALL_RADIUS
            ballDx = -ballDx
        } else if (newX + BALL_RADIUS > width) {
            newX = width - BALL_RADIUS
            ballDx = -ballDx
        }
        if (newY - BALL_RADIUS < 0f) {
            newY = BALL_RADIUS
            ballDy = -ballDy
        }

        // Paddle
        val paddleTop = paddleY
        val paddleBottom = paddleY + PADDLE_HEIGHT
        if (ballDy > 0 &&
            newY + BALL_RADIUS >= paddleTop &&
            newY - BALL_RADIUS <= paddleBottom &&
            newX + BALL_RADIUS >= paddleX &&
            newX - BALL_RADIUS <= paddleX + PADDLE_WIDTH
        ) {
            newY = paddleTop - BALL_RADIUS
            // Where the ball hit the paddle (-1 = far left edge, +1 = far right edge)
            // steers the bounce angle, giving the player some aiming control.
            val hitOffset = ((newX - paddleX) / PADDLE_WIDTH - 0.5f) * 2f
            ballDx = BALL_SPEED * hitOffset
            ballDy = -kotlin.math.abs(ballDy)
        }

        // Bricks
        val ballRect = Rect(newX - BALL_RADIUS, newY - BALL_RADIUS, newX + BALL_RADIUS, newY + BALL_RADIUS)
        val hitBrick = bricks.firstOrNull { !it.destroyed && brickRect(it).intersects(ballRect) }
        if (hitBrick != null) {
            hitBrick.destroyed = true
            bricks = bricks.toList() // new list reference so Compose state observers recompose
            score += 1
            ballDy = -ballDy
            if (bricks.all { it.destroyed }) {
                isWon = true
            }
        }

        ballX = newX
        ballY = newY

        // Missed the paddle entirely
        if (newY - BALL_RADIUS > height) {
            isGameOver = true
        }
    }

    fun reset() {
        paddleX = (width - PADDLE_WIDTH) / 2f
        ballX = width / 2f
        ballY = paddleY - BALL_RADIUS - 1f
        ballDx = BALL_SPEED
        ballDy = -BALL_SPEED
        bricks = buildBricks()
        score = 0
        isGameOver = false
        isWon = false
    }
}

data class Rect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun intersects(other: Rect): Boolean =
        left < other.right && right > other.left && top < other.bottom && bottom > other.top
}

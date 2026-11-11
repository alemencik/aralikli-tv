package com.daylak.araliklitv

/** Option colors on the result screen. A correct option is green even after a wrong guess. */
enum class AnswerHighlight { CORRECT, WRONG, NORMAL }

object AnswerFeedback {
    fun highlight(letter: String, answer: String?, selected: String?): AnswerHighlight = when {
        letter == answer -> AnswerHighlight.CORRECT
        selected != null && letter == selected -> AnswerHighlight.WRONG
        else -> AnswerHighlight.NORMAL
    }
}

/** Randomized reveal latency; inclusive 3,000..5,000 milliseconds. */
object RevealDelay {
    fun millis(random: kotlin.random.Random = kotlin.random.Random.Default): Long =
        random.nextLong(3_000L, 5_001L)
}

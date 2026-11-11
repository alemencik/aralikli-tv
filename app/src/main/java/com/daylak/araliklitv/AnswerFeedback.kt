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

internal data class AnswerCountdown(val choice: String, val startedAtMs: Long) {
    fun remainingMs(now: Long) = CountdownSettings.remainingMillis(startedAtMs + DURATION_MS, now)
    fun untilRevealMs(now: Long) =
        CountdownSettings.remainingMillis(startedAtMs + DURATION_MS + REVEAL_DELAY_MS, now)

    fun select(letter: String, now: Long): AnswerCountdown =
        if (remainingMs(now) > 0L) copy(choice = letter) else this

    companion object {
        const val DURATION_MS = 4000L
        const val REVEAL_DELAY_MS = 1100L
    }
}

internal fun shuffledOptions(
    letters: Set<String>,
    previous: List<String>?,
    random: kotlin.random.Random = kotlin.random.Random.Default
): List<String> {
    val order = letters.shuffled(random)
    return if (order.size > 1 && order == previous) order.drop(1) + order.first() else order
}

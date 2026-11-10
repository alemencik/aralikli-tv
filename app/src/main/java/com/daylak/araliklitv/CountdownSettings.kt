package com.daylak.araliklitv

/**
 * Countdown is optional in study mode. Expiry is only visual feedback;
 * it never grades or schedules a question as forgotten.
 */
object CountdownSettings {
    val choices = listOf(0, 30, 60, 90)
    fun next(current: Int): Int {
        val index = choices.indexOf(current)
        return choices[(if (index < 0) 0 else index + 1) % choices.size]
    }
    fun label(seconds: Int): String =
        if (seconds == 0) "Kapalı" else "$seconds saniye"

    fun remainingMillis(deadline: Long, now: Long): Long =
        (deadline - now).coerceAtLeast(0L)

    fun display(milliseconds: Long): String {
        val seconds = (milliseconds.coerceAtLeast(0L) + 999L) / 1000L
        return "%02d:%02d".format(seconds / 60, seconds % 60)
    }
}

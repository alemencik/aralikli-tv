package com.daylak.araliklitv

/** Readable sizes for five TV options; Android wraps full text at each width. */
object StudyLayoutRules {
    fun questionSp(length: Int): Float = when {
        length > 320 -> 19f
        length > 200 -> 21f
        length > 110 -> 24f
        else -> 29f
    }

    fun answerSp(longest: Int, total: Int): Float = when {
        longest > 165 || total > 480 -> 17f
        longest > 100 || total > 320 -> 19f
        longest > 55 || total > 240 -> 21f
        else -> 25f
    }
}

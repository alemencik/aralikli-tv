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

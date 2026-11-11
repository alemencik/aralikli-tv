package com.daylak.araliklitv

import org.junit.Assert.assertEquals
import org.junit.Test

class AnswerFeedbackTest {
    @Test fun wrongAnswerHighlightsWrongRedAndCorrectGreen() {
        assertEquals(AnswerHighlight.WRONG, AnswerFeedback.highlight("A", "C", "A"))
        assertEquals(AnswerHighlight.CORRECT, AnswerFeedback.highlight("C", "C", "A"))
        assertEquals(AnswerHighlight.NORMAL, AnswerFeedback.highlight("B", "C", "A"))
    }
    @Test fun correctAnswerHighlightsOnlyCorrectGreen() {
        assertEquals(AnswerHighlight.CORRECT, AnswerFeedback.highlight("C", "C", "C"))
        assertEquals(AnswerHighlight.NORMAL, AnswerFeedback.highlight("A", "C", "C"))
    }
    @Test fun timeoutHighlightsCorrectWithoutAnIncorrectChoice() {
        assertEquals(AnswerHighlight.CORRECT, AnswerFeedback.highlight("D", "D", null))
        assertEquals(AnswerHighlight.NORMAL, AnswerFeedback.highlight("E", "D", null))
    }
}

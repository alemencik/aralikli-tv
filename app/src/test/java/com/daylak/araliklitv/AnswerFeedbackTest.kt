package com.daylak.araliklitv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerFeedbackTest {
    @Test fun revealDelayAlwaysBetweenThreeAndFiveSeconds() {
        val random = kotlin.random.Random(42)
        val samples = (0 until 1000).map { RevealDelay.millis(random) }
        assertTrue(samples.all { it in 3_000L..5_000L })
        assertTrue(samples.distinct().size > 50)
    }

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

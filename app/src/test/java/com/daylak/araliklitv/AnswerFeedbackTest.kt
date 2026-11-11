package com.daylak.araliklitv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AnswerFeedbackTest {
    @Test fun answerCanChangeUntilCountdownEndsWithoutResettingTheClock() {
        val first = AnswerCountdown("A", 100L)
        val updated = first.select("C", 4099L)
        assertEquals("C", updated.choice)
        assertEquals(1L, updated.remainingMs(4099L))
        assertEquals("C", updated.select("D", 4100L).choice)
        assertEquals(0L, updated.remainingMs(4100L))
        assertEquals(1100L, updated.untilRevealMs(4100L))
        assertEquals(1L, updated.untilRevealMs(5199L))
        assertEquals(0L, updated.untilRevealMs(5200L))
    }

    @Test fun optionOrderChangesEvenIfTheRandomShuffleRepeats() {
        val letters = linkedSetOf("A", "B", "C", "D")
        val previous = shuffledOptions(letters, null, kotlin.random.Random(42))
        val next = shuffledOptions(letters, previous, kotlin.random.Random(42))
        assertEquals(letters, next.toSet())
        assertNotEquals(previous, next)
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

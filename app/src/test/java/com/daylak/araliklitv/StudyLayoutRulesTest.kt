package com.daylak.araliklitv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyLayoutRulesTest {
    @Test fun answerTextShrinksOnlyForLongChoices() {
        assertEquals(25f, StudyLayoutRules.answerSp(25, 115), 0f)
        assertEquals(21f, StudyLayoutRules.answerSp(70, 250), 0f)
        assertEquals(19f, StudyLayoutRules.answerSp(130, 380), 0f)
        assertEquals(17f, StudyLayoutRules.answerSp(205, 520), 0f)
    }
    @Test fun longQuestionDoesNotKeepGiantFont() {
        assertEquals(29f, StudyLayoutRules.questionSp(80), 0f)
        assertTrue(StudyLayoutRules.questionSp(350) < StudyLayoutRules.questionSp(80))
    }
}

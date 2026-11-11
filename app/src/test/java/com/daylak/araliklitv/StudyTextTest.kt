package com.daylak.araliklitv

import org.junit.Assert.assertEquals
import org.junit.Test

class StudyTextTest {
    @Test fun readsEnglishPromptWithoutTheTurkishInstruction() {
        val line = StudyText.question("Our baby ______ one year old.\nCümleyi tamamlayan doğru seçenek?", true)
        assertEquals(StudyLine("Our baby  blank  one year old.", english = true), line)
    }

    @Test fun fillsEnglishBlanksInOrder() {
        val line = StudyText.takeaway("Weekend days are ____ and ____.", "Saturday / Sunday", true)
        assertEquals(StudyLine("Weekend days are Saturday and Sunday.", english = true), line)
    }

    @Test fun turnsYearAnswerIntoAStatement() {
        val line = StudyText.takeaway("Teşvik-i Sanayi Geçici Kanunu kaç yılında kabul edilmiştir?", "1913", false)
        assertEquals(StudyLine("Teşvik-i Sanayi Geçici Kanunu 1913 yılında kabul edilmiştir."), line)
    }

    @Test fun doesNotTurnNegativeQuestionIntoAnUnqualifiedClaim() {
        val line = StudyText.takeaway("Hangisi doğru değildir?", "Bütün ağaçlar yaprak döker.", false)
        assertEquals(StudyLine("Doğru cevap. Bütün ağaçlar yaprak döker."), line)
    }
}

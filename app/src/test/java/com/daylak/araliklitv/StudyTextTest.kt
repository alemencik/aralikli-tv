package com.daylak.araliklitv

import org.junit.Assert.assertEquals
import org.junit.Test

class StudyTextTest {
    @Test fun readsEnglishPromptWithoutTheTurkishInstruction() {
        val line = StudyText.question("Our baby ______ one year old.\nCümleyi tamamlayan doğru seçenek?", true)
        assertEquals(StudyLine("Our baby blank one year old.", english = true), line)
    }

    @Test fun doesNotTreatAsciiIAsATurkishCharacter() {
        val line = StudyText.question("I live in Istanbul.", true)
        assertEquals(StudyLine("I live in Istanbul.", english = true), line)
    }
}

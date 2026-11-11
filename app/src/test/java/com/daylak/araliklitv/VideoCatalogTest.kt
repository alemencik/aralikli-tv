package com.daylak.araliklitv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoCatalogTest {
    @Test fun directVideoLinksAreValidIds() {
        val deckIds = listOf("rapor", "yerel", "ataturk1", "ataturk2",
            "turkce1", "ingilizce1", "sosyal")
        val ids = deckIds.flatMap { VideoCatalog.forDeck(it) }.map { it.youtubeId }
        assertEquals(6, ids.size)
        assertTrue(ids.all { it.matches(Regex("[A-Za-z0-9_-]{11}")) })
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test fun deckWithoutVerifiedVideoHasNoInventedUrl() {
        assertTrue(VideoCatalog.forDeck("rapor").isEmpty())
        assertTrue(VideoCatalog.forDeck("not-a-deck").isEmpty())
    }
}

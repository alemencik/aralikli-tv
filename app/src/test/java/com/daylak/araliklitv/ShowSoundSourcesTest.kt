package com.daylak.araliklitv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowSoundSourcesTest {
    @Test fun allThreeChosenSoundLinksAreHttpsAndMappedCorrectly() {
        assertEquals(
            listOf("milyoner_correct.mp3", "milyoner_wrong.mp3", "milyoner_suspense.mp3"),
            ShowSoundSources.links.map { it.targetName }
        )
        assertTrue(ShowSoundSources.links.all { it.url.startsWith("https://www.myinstants.com/media/sounds/") })
    }

    @Test fun rejectsHtmlOrBrokenDownloadsBeforeInstalling() {
        assertTrue(ShowSoundSources.hasMp3Header("ID3".toByteArray()))
        assertTrue(ShowSoundSources.hasMp3Header(byteArrayOf(0xff.toByte(), 0xfb.toByte(), 0x90.toByte())))
        assertFalse(ShowSoundSources.hasMp3Header("<!DOCTYPE html>".toByteArray()))
        assertFalse(ShowSoundSources.hasMp3Header(byteArrayOf()))
    }
}

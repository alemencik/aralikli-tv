package com.daylak.araliklitv

import org.junit.Assert.assertEquals
import org.junit.Test

class CountdownSettingsTest {
    @Test fun cyclesThroughOptionalCountdownDurations() {
        assertEquals(20, CountdownSettings.next(0))
        assertEquals(30, CountdownSettings.next(20))
        assertEquals(60, CountdownSettings.next(30))
        assertEquals(90, CountdownSettings.next(60))
        assertEquals(0, CountdownSettings.next(90))
    }

    @Test fun defaultIsTwentySeconds() {
        assertEquals(20, CountdownSettings.DEFAULT_SECONDS)
        assertEquals("20 saniye", CountdownSettings.label(CountdownSettings.DEFAULT_SECONDS))
    }
    @Test fun usesMonotonicRemainingTimeAndClampsAtZero() {
        assertEquals(30_000L, CountdownSettings.remainingMillis(100_000L,70_000L))
        assertEquals(0L, CountdownSettings.remainingMillis(100_000L,100_001L))
    }

    @Test fun showsMinutesAndSecondsForTv() {
        assertEquals("01:30", CountdownSettings.display(90_000L))
        assertEquals("00:10", CountdownSettings.display(9_600L))
        assertEquals("00:00", CountdownSettings.display(0L))
        assertEquals("Kapalı", CountdownSettings.label(0))
    }
}

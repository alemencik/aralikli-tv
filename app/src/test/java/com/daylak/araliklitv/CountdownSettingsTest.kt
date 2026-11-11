package com.daylak.araliklitv

import org.junit.Assert.assertEquals
import org.junit.Test

class CountdownSettingsTest {
    @Test fun cyclesThroughOptionalCountdownDurations() {
        assertEquals(50, CountdownSettings.next(0))
        assertEquals(0, CountdownSettings.next(50))
    }

    @Test fun defaultIsFiftySeconds() {
        assertEquals(50, CountdownSettings.DEFAULT_SECONDS)
        assertEquals("50 saniye", CountdownSettings.label(CountdownSettings.DEFAULT_SECONDS))
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

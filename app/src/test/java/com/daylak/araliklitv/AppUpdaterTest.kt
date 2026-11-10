package com.daylak.araliklitv

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdaterTest {
    @Test fun newerSemanticVersion() {
        assertTrue(AppUpdater.isNewer("0.2.1","0.2.0"))
        assertTrue(AppUpdater.isNewer("0.3.0","0.2.99"))
        assertTrue(AppUpdater.isNewer("1.0.0","0.9.9"))
    }
    @Test fun oldEqualAndMalformedVersionsAreIgnored() {
        assertFalse(AppUpdater.isNewer("0.2.0","0.2.0"))
        assertFalse(AppUpdater.isNewer("0.1.1","0.2.0"))
        assertFalse(AppUpdater.isNewer("latest","0.2.0"))
    }
}

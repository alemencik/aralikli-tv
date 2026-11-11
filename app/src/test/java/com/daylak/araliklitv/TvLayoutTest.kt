package com.daylak.araliklitv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TvLayoutTest {
    @Test fun compactTvGetsSmallerControls() {
        val compact = TvLayout.scale(853f, 480f)
        assertTrue(compact < 0.8f)
        assertTrue(compact >= 0.65f)
    }
    @Test fun largeTvDoesNotOverscale() {
        assertEquals(1f, TvLayout.scale(1920f, 1080f), 0.001f)
    }
}

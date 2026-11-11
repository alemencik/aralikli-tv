package com.daylak.araliklitv

/** Scales Android TV text, button heights and safe margins for 720p and 1080p. */
object TvLayout {
    fun scale(widthDp: Float, heightDp: Float): Float {
        if (widthDp <= 0f || heightDp <= 0f) return 1f
        return minOf(1f, (heightDp / 650f).coerceAtLeast(0.65f),
            (widthDp / 1120f).coerceAtLeast(0.70f))
    }
}

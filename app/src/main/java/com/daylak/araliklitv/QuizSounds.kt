package com.daylak.araliklitv

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

/** Short device-generated sound effects, not third-party television music. */
class QuizSounds {
    private val handler = Handler(Looper.getMainLooper())
    private var tones: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_MUSIC, 65)
    } catch (_: RuntimeException) { null }
    private val secondTone = Runnable {
        try { tones?.startTone(ToneGenerator.TONE_PROP_ACK, 175) } catch (_: RuntimeException) {}
    }

    fun correct() {
        handler.removeCallbacks(secondTone)
        try { tones?.startTone(ToneGenerator.TONE_PROP_BEEP, 130) } catch (_: RuntimeException) {}
        handler.postDelayed(secondTone, 180L)
    }
    fun wrong() {
        handler.removeCallbacks(secondTone)
        try { tones?.startTone(ToneGenerator.TONE_PROP_NACK, 260) } catch (_: RuntimeException) {}
    }
    fun warning() {
        handler.removeCallbacks(secondTone)
        try { tones?.startTone(ToneGenerator.TONE_PROP_PROMPT, 120) }
        catch (_: RuntimeException) {}
    }
    fun release() {
        handler.removeCallbacks(secondTone)
        tones?.release()
        tones = null
    }
}

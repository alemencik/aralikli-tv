package com.daylak.araliklitv

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

/** Local personal-use mp3 cues override built-in tones without entering GitHub. */
class QuizSounds(context: Context) {
    private val assets = LocalShowAssets(context)
    private val handler = Handler(Looper.getMainLooper())
    private var media: MediaPlayer? = null
    private var suspenseMedia: MediaPlayer? = null
    private var questionMedia: MediaPlayer? = null

    /** These privately installed tracks alternate between question screens. */
    fun startQuestionMusic(questionIndex: Int) {
        stopQuestionMusic()
        val preferred = if (questionIndex % 2 == 0)
            "milyoner_bed_a.mp3" else "milyoner_bed_b.mp3"
        val fallback = if (questionIndex % 2 == 0)
            "milyoner_bed_b.mp3" else "milyoner_bed_a.mp3"
        val file = assets.soundFile(preferred) ?: assets.soundFile(fallback) ?: return
        var player: MediaPlayer? = null
        try {
            player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                isLooping = true
                setVolume(0.28f, 0.28f)
                prepare()
            }
            questionMedia = player
            player.start()
        } catch (_: Exception) {
            player?.release()
            questionMedia = null
        }
    }

    fun stopQuestionMusic() {
        questionMedia?.run {
            try { if (isPlaying) stop() } catch (_: Exception) {}
            release()
        }
        questionMedia = null
    }
    private var tones: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_MUSIC, 65)
    } catch (_: RuntimeException) { null }
    private val secondTone = Runnable {
        try { tones?.startTone(ToneGenerator.TONE_PROP_ACK, 175) } catch (_: RuntimeException) {}
    }
    private val tensionTicks = mutableListOf<Runnable>()
    fun stopTension() {
        tensionTicks.forEach { handler.removeCallbacks(it) }
        tensionTicks.clear()
        suspenseMedia?.run {
            try { if (isPlaying) stop() } catch (_: Exception) {}
            release()
        }
        suspenseMedia = null
    }

    /**
     * User-provided show suspense MP3 has priority; it loops until the answer
     * is revealed. If absent, use the independent built-in tension cue.
     */
    fun tension(waitMs: Long) {
        stopTension()
        val file = assets.soundFile("milyoner_lock.mp3")
            ?: assets.soundFile("milyoner_suspense.mp3")
        if (file != null) {
            var player: MediaPlayer? = null
            try {
                player = MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    isLooping = true
                    setVolume(0.73f, 0.73f)
                    prepare()
                }
                suspenseMedia = player
                player.start()
                return
            } catch (_: Exception) {
                player?.release()
                suspenseMedia = null
            }
        }
        val ticks = ((waitMs - 500L) / 750L).toInt().coerceIn(0, 7)
        for (i in 0 until ticks) {
            val task = Runnable {
                try { tones?.startTone(ToneGenerator.TONE_PROP_PROMPT, 90) }
                catch (_: RuntimeException) {}
            }
            tensionTicks.add(task)
            handler.postDelayed(task, 520L + i * 750L)
        }
    }

    private fun playLocal(name: String): Boolean {
        val file = assets.soundFile(name) ?: return false
        media?.release()
        media = null
        var player: MediaPlayer? = null
        return try {
            player = MediaPlayer()
            val playing = player
            playing.setDataSource(file.absolutePath)
            playing.setOnCompletionListener {
                if (media === it) media = null
                it.release()
            }
            playing.prepare()
            media = playing
            playing.start()
            true
        } catch (_: Exception) {
            player?.release()
            media = null
            false
        }
    }

    fun press() {
        handler.removeCallbacks(secondTone)
        if (playLocal("milyoner_select.mp3")) return
        try { tones?.startTone(ToneGenerator.TONE_PROP_BEEP, 95) }
        catch (_: RuntimeException) {}
    }
    fun correct() {
        stopTension()
        handler.removeCallbacks(secondTone)
        if (playLocal("milyoner_correct.mp3")) return
        try { tones?.startTone(ToneGenerator.TONE_PROP_BEEP, 130) } catch (_: RuntimeException) {}
        handler.postDelayed(secondTone, 180L)
    }
    fun wrong() {
        stopTension()
        handler.removeCallbacks(secondTone)
        if (playLocal("milyoner_wrong.mp3")) return
        try { tones?.startTone(ToneGenerator.TONE_PROP_NACK, 260) } catch (_: RuntimeException) {}
    }
    fun warning() {
        handler.removeCallbacks(secondTone)
        if (playLocal("milyoner_warning.mp3")) return
        try { tones?.startTone(ToneGenerator.TONE_PROP_PROMPT, 120) }
        catch (_: RuntimeException) {}
    }
    fun release() {
        stopQuestionMusic()
        stopTension()
        handler.removeCallbacks(secondTone)
        media?.release()
        media = null
        tones?.release()
        tones = null
    }
}

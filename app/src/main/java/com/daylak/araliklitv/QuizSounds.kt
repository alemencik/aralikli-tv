package com.daylak.araliklitv

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

class QuizSounds(context: Context) {
    private val cache = File(context.cacheDir, "original-quiz-audio-v1")
    private var background: MediaPlayer? = null
    private var effect: MediaPlayer? = null

    fun startQuestionMusic() = playBackground(Cue.AMBIENCE, 0.42f)

    fun stopBackground() {
        background?.release()
        background = null
    }

    fun tension() = playBackground(Cue.WAIT, 0.48f)

    fun press() = playEffect(Cue.SELECT, 0.42f)
    fun correct() = playEffect(Cue.CORRECT, 0.8f)
    fun wrong() = playEffect(Cue.WRONG, 0.8f)
    fun warning() = playEffect(Cue.WARNING, 0.52f)

    fun release() {
        stopBackground()
        effect?.release()
        effect = null
    }

    private fun playBackground(cue: Cue, volume: Float) {
        stopBackground()
        background = play(cue, volume, loop = true)
    }

    private fun playEffect(cue: Cue, volume: Float) {
        effect?.release()
        effect = play(cue, volume, loop = false)?.apply {
            setOnCompletionListener { completed ->
                if (effect === completed) {
                    effect = null
                    completed.release()
                }
            }
        }
    }

    private fun play(cue: Cue, volume: Float, loop: Boolean): MediaPlayer? {
        var player: MediaPlayer? = null
        return try {
            val file = File(cache, "${cue.name}.wav")
            if (!file.isFile) {
                cache.mkdirs()
                val pending = File.createTempFile("cue-", ".wav", cache)
                try {
                    pending.writeBytes(cue.wav())
                    check(pending.renameTo(file))
                } finally {
                    pending.delete()
                }
            }
            player = MediaPlayer()
            player.setDataSource(file.absolutePath)
            player.isLooping = loop
            player.setVolume(volume, volume)
            player.prepare()
            player.start()
            player
        } catch (e: Exception) {
            player?.release()
            Log.w("QuizSounds", "Could not play ${cue.name}", e)
            null
        }
    }
}

private const val SAMPLE_RATE = 24_000

/** Original cues synthesized from oscillators. The two beds are periodic for looping. */
internal enum class Cue(private val duration: Double) {
    AMBIENCE(12.0),
    WAIT(3.0),
    CORRECT(0.85),
    WRONG(0.65),
    SELECT(0.12),
    WARNING(0.24);

    fun wav(): ByteArray {
        val frames = (duration * SAMPLE_RATE).toInt()
        val dataSize = frames * Short.SIZE_BYTES
        val output = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        output.put("RIFF".toByteArray()).putInt(36 + dataSize)
        output.put("WAVEfmt ".toByteArray()).putInt(16)
        output.putShort(1).putShort(1).putInt(SAMPLE_RATE)
        output.putInt(SAMPLE_RATE * 2).putShort(2).putShort(16)
        output.put("data".toByteArray()).putInt(dataSize)
        repeat(frames) { frame ->
            val time = frame.toDouble() / SAMPLE_RATE
            val sample = sampleAt(time).coerceIn(-1.0, 1.0)
            output.putShort((sample * Short.MAX_VALUE).toInt().toShort())
        }
        return output.array()
    }

    private fun sampleAt(time: Double): Double = when (this) {
        AMBIENCE -> {
            val swell = 0.6 - 0.4 * wave(1.0 / 12.0, time + 3.0)
            val pulse = 0.5 - 0.5 * wave(2.0, time + 0.125)
            0.26 * swell * (wave(110.0, time) + 0.28 * wave(165.0, time)) +
                0.15 * pulse * pulse * wave(330.0, time)
        }
        WAIT -> {
            val pulse = 0.5 - 0.5 * wave(2.0, time + 0.125)
            0.34 * pulse * pulse * wave(130.0, time) +
                0.09 * wave(195.0, time) * (0.5 + 0.5 * wave(1.0, time))
        }
        CORRECT -> 0.55 * note(time, 523.25, 0.36) +
            0.48 * note(time - 0.12, 659.25, 0.4) +
            0.42 * note(time - 0.24, 880.0, 0.61)
        WRONG -> 0.57 * note(time, 293.66, 0.3) +
            0.56 * note(time - 0.15, 196.0, 0.5)
        SELECT -> 0.42 * note(time, 720.0, duration)
        WARNING -> 0.48 * note(time, 440.0, duration)
    }

    private fun note(time: Double, frequency: Double, length: Double): Double {
        if (time <= 0.0 || time >= length) return 0.0
        val envelope = (time / 0.012).coerceAtMost(1.0) *
            ((length - time) / 0.06).coerceAtMost(1.0) * exp(-4.0 * time / length)
        return envelope * (wave(frequency, time) + 0.17 * wave(2.0 * frequency, time))
    }

    private fun wave(frequency: Double, time: Double) = sin(2.0 * PI * frequency * time)
}

package com.daylak.araliklitv

import android.content.Context
import android.media.MediaPlayer
import android.util.Log

class QuizSounds(private val context: Context) {
    private var effect: MediaPlayer? = null

    fun correct() = play(R.raw.quiz_correct)
    fun wrong() = play(R.raw.quiz_wrong)

    fun release() {
        effect?.release()
        effect = null
    }

    private fun play(resource: Int) {
        release()
        try {
            val player = MediaPlayer.create(context, resource) ?: return
            effect = player
            player.setOnCompletionListener { completed ->
                if (effect === completed) release()
            }
            player.setOnErrorListener { failed, _, _ ->
                if (effect === failed) release()
                true
            }
            player.setVolume(0.8f, 0.8f)
            player.start()
        } catch (e: Exception) {
            release()
            Log.w("QuizSounds", "Could not play feedback effect", e)
        }
    }
}

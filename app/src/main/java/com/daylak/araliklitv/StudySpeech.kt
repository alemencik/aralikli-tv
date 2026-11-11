package com.daylak.araliklitv

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import java.util.Locale

internal data class StudyLine(val text: String, val english: Boolean = false)

internal object StudyText {
    private val blank = Regex("_{2,}|…+|\\.{3,}")
    private val englishInstruction = Regex(
        "(?i)\\s*(?:Cümleyi tamamlayan|Karşılıklı konuşmayı tamamlayan|Boş bırakılan yere)"
    )
    private val turkishWording = Regex(
        "[çğıöşüÇĞİÖŞÜ]|(?i:\\b(?:hangisi|hangisidir|cümle|boşluk|değildir)\\b)"
    )
    private val repeatedSpaces = Regex("[ \\t]{2,}")

    fun question(stem: String, englishDeck: Boolean): StudyLine {
        val prompt = if (englishDeck) englishInstruction.split(stem, limit = 2).first()
            else stem
        val english = englishDeck && !turkishWording.containsMatchIn(prompt)
        val spoken = blank.replace(prompt.trim(), if (english) " blank " else " boşluk ")
        return StudyLine(repeatedSpaces.replace(spoken, " ").trim(), english)
    }

    fun option(index: Int, text: String, englishDeck: Boolean): StudyLine {
        val english = englishDeck && !turkishWording.containsMatchIn(text)
        return StudyLine("${'A' + index}. ${text.trim()}", english)
    }
}

internal class StudySpeech(context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var status: Int? = null
    private var pending: List<StudyLine>? = null
    private var closed = false
    private val engine = TextToSpeech(context.applicationContext) { result ->
        main.post {
            if (!closed) {
                status = result
                pending?.let(::speak)
                pending = null
            }
        }
    }

    fun speak(line: StudyLine) = speak(listOf(line))

    fun speak(lines: List<StudyLine>) {
        when (status) {
            null -> {
                pending = lines
                return
            }
            TextToSpeech.SUCCESS -> Unit
            else -> return
        }
        engine.stop()
        engine.setSpeechRate(1.12f)
        val voices = engine.voices.orEmpty().filter { !it.isNetworkConnectionRequired }
        var hasQueuedSpeech = false
        lines.forEachIndexed { index, line ->
            val locale = if (line.english) Locale.US else Locale.forLanguageTag("tr-TR")
            val voice = voices.filter { it.locale.language == locale.language }
                .maxByOrNull { if (it.locale.country == locale.country) it.quality + 1000 else it.quality }
                ?: return@forEachIndexed
            if (engine.setVoice(voice) == TextToSpeech.SUCCESS) {
                if (hasQueuedSpeech) {
                    engine.playSilentUtterance(200L, TextToSpeech.QUEUE_ADD, "study-gap-$index")
                }
                val queued = engine.speak(
                    line.text.take(TextToSpeech.getMaxSpeechInputLength()),
                    TextToSpeech.QUEUE_ADD, null, "study-$index"
                )
                if (queued == TextToSpeech.SUCCESS) hasQueuedSpeech = true
            }
        }
    }

    fun stop() {
        pending = null
        if (status == TextToSpeech.SUCCESS) engine.stop()
    }

    fun close() {
        closed = true
        stop()
        engine.shutdown()
    }
}

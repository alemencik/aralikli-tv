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

    private val yearQuestion = Regex(
        "^(.+?) (?:kaç yılında|hangi yılda) ([^?]+)\\?$", RegexOption.IGNORE_CASE
    )
    private val identifyingQuestion = Regex(
        "^([^?!]+?) aşağıdakilerden hangisidir\\?$", RegexOption.IGNORE_CASE
    )

    fun question(stem: String, englishDeck: Boolean): StudyLine {
        val prompt = if (englishDeck) englishInstruction.split(stem, limit = 2).first()
            else stem
        val english = englishDeck && !turkishWording.containsMatchIn(prompt)
        val spoken = blank.replace(prompt.trim(), if (english) " blank " else " boşluk ")
        return StudyLine(repeatedSpaces.replace(spoken, " ").trim(), english)
    }

    fun takeaway(stem: String, answer: String, englishDeck: Boolean): StudyLine {
        val prompt = if (englishDeck) englishInstruction.split(stem, limit = 2).first().trim() else stem.trim()
        val blanks = blank.findAll(prompt).count()
        val answers = if (blanks == 1) listOf(answer.trim())
            else answer.split(Regex("\\s*(?:/|\\s[-–]\\s)\\s*")).map(String::trim)
        if (blanks > 0 && blanks == answers.size && answers.none(String::isBlank)) {
            val replacements = answers.iterator()
            val completed = blank.replace(prompt) { replacements.next() }
            return StudyLine(completed, englishDeck && !turkishWording.containsMatchIn(completed))
        }
        val choice = answer.trim()
        val english = englishDeck && !turkishWording.containsMatchIn(prompt + " " + choice)
        if (!englishDeck && choice.matches(Regex("[0-9]{4}"))) {
            yearQuestion.matchEntire(prompt)?.let { match ->
                val (subject, verb) = match.destructured
                return StudyLine("$subject $choice yılında $verb.")
            }
        }
        if (!englishDeck && choice.length <= 90 && !choice.endsWith('.') &&
            choice.none { it == '?' || it == '!' || it == '\n' } &&
            !choice.equals("Hepsi", ignoreCase = true) && !choice.equals("Hiçbiri", ignoreCase = true)) {
            identifyingQuestion.matchEntire(prompt)?.let { match ->
                val subject = match.groupValues[1]
                return StudyLine("$subject $choice.")
            }
        }
        val caption = if (english) "Correct answer." else "Doğru cevap."
        return StudyLine("$caption $choice", english)
    }
}

internal class StudySpeech(context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var status: Int? = null
    private var pending: StudyLine? = null
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

    fun speak(line: StudyLine) {
        when (status) {
            null -> {
                pending = line
                return
            }
            TextToSpeech.SUCCESS -> Unit
            else -> return
        }
        val locale = if (line.english) Locale.US else Locale.forLanguageTag("tr-TR")
        val voice = engine.voices.orEmpty()
            .filter { !it.isNetworkConnectionRequired && it.locale.language == locale.language }
            .maxByOrNull { if (it.locale.country == locale.country) it.quality + 1000 else it.quality }
            ?: return
        if (engine.setVoice(voice) != TextToSpeech.SUCCESS) return
        engine.speak(line.text.take(TextToSpeech.getMaxSpeechInputLength()), TextToSpeech.QUEUE_FLUSH,
            null, "study")
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

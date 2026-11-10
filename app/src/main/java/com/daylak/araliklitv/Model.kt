package com.daylak.araliklitv

import org.json.JSONObject

/** Stable IDs matter: changing a question ID resets its repetition history. */
data class Question(
    val id: String,
    val stem: String,
    val options: LinkedHashMap<String, String>,
    val answer: String?,
    val answerText: String?,
    val sourcePage: Int = 0,
    val sourceFile: String? = null
) {
    val isScorable get() = answer != null && options.containsKey(answer)
}

data class Deck(val id: String, val title: String, val questions: List<Question>)

data class Progress(
    val repetitions: Int = 0,
    val intervalDays: Int = 0,
    val ease: Double = 2.5,
    val dueAt: Long = 0L,
    val attempts: Int = 0,
    val correct: Int = 0,
    val learningStep: Int = 0, // 0 = long-term schedule; positive = within-session learning
    val dueAtTurn: Int = 0,
    val timeouts: Int = 0
)

object QuestionLoader {
    fun load(json: String): List<Deck> {
        val root = JSONObject(json)
        require(root.getInt("schema") == 1) { "Bilinmeyen JSON şeması" }
        val decks = root.getJSONArray("decks")
        val usedIds = mutableSetOf<String>()
        return (0 until decks.length()).map { d ->
            val deck = decks.getJSONObject(d)
            val deckId = deck.getString("id").trim()
            require(deckId.isNotEmpty())
            val items = deck.getJSONArray("questions")
            val questions = (0 until items.length()).map { q ->
                val item = items.getJSONObject(q)
                val id = item.getString("id").trim()
                require(id.isNotEmpty() && usedIds.add(id)) { "Tekrarlanan/boş soru kimliği: $id" }
                val opts = linkedMapOf<String, String>()
                val raw = item.optJSONObject("options") ?: JSONObject()
                for (letter in listOf("A", "B", "C", "D", "E")) {
                    if (raw.has(letter)) opts[letter] = raw.getString(letter)
                }
                val answer = item.optString("answer").takeIf { it in opts.keys }
                val answerText = item.optString("answer_text").takeUnless { it.isBlank() || it == "null" }
                Question(id, item.getString("stem"), opts, answer, answerText, item.optInt("source_page"), item.optString("source_file").takeUnless { it.isBlank() })
            }
            Deck(deckId, deck.getString("title"), questions)
        }
    }
}

package com.daylak.araliklitv

/**
 * Only directly identified public YouTube videos. These are third-party
 * exam-preparation videos and are not represented as official AUZEF lectures.
 * A deck without a verified watch URL intentionally has no entries.
 */
data class VideoLesson(val title: String, val youtubeId: String)

object VideoCatalog {
    private val lessons = mapOf(
        "yerel" to listOf(
            VideoLesson("Yerel Yönetimler ve Sosyal Hizmet - Final Hazırlığı", "DguJ-q4CWuE")
        ),
        "ataturk1" to listOf(
            VideoLesson("Atatürk İlkeleri ve İnkılap Tarihi I - Final Hazırlığı", "BdUBelS2oIo")
        ),
        "ataturk2" to listOf(
            VideoLesson("Atatürk İlkeleri ve İnkılap Tarihi II - Final Hazırlığı", "22LPKj6eBfc")
        ),
        "turkce1" to listOf(
            VideoLesson("Türk Dili I - Final Hazırlığı", "zg6MDbK2T_8")
        ),
        "ingilizce1" to listOf(
            VideoLesson("İngilizce I - Vize Hazırlığı", "5OMZBCDd3TM")
        ),
        "sosyal" to listOf(
            VideoLesson("Sosyal Hizmet Kuramları - Final Hazırlığı", "c7VgCMj6dOI")
        )
    )

    fun forDeck(deckId: String): List<VideoLesson> = lessons[deckId].orEmpty()
}

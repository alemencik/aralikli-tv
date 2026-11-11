package com.daylak.araliklitv

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** User-requested external sounds are saved locally, never bundled into public APKs. */
data class ShowSoundLink(val targetName: String, val url: String)

object ShowSoundSources {
    val links = listOf(
        ShowSoundLink("milyoner_correct.mp3",
            "https://www.myinstants.com/media/sounds/correct_VsVqwRb.mp3"),
        ShowSoundLink("milyoner_wrong.mp3",
            "https://www.myinstants.com/media/sounds/wrong_JbK803k.mp3"),
        ShowSoundLink("milyoner_suspense.mp3",
            "https://www.myinstants.com/media/sounds/5000000-music-mp3cut.mp3")
    )

    /** An MP3 starts with ID3 metadata or an MPEG audio frame sync. */
    fun hasMp3Header(bytes: ByteArray): Boolean {
        if (bytes.size < 3) return false
        val id3 = bytes[0] == 0x49.toByte() &&
            bytes[1] == 0x44.toByte() && bytes[2] == 0x33.toByte()
        val frameSync = (bytes[0].toInt() and 0xff) == 0xff &&
            (bytes[1].toInt() and 0xe0) == 0xe0
        return id3 || frameSync
    }
}

data class ShowSoundDownloadResult(
    val downloaded: Int, val installed: Int, val failed: List<String>
)

class ShowSoundDownloader(private val context: Context) {
    private val folder: File get() =
        context.getExternalFilesDir(null) ?: error("TV ses klasörü bulunamadı")

    fun installedCount(): Int =
        ShowSoundSources.links.count { name ->
            File(folder, name.targetName).let { it.isFile && it.length() > 1024L }
        }

    fun installMissing(): ShowSoundDownloadResult {
        folder.mkdirs()
        var downloaded = 0
        val failed = mutableListOf<String>()
        ShowSoundSources.links.forEach { sound ->
            val destination = File(folder, sound.targetName)
            if (destination.isFile && destination.length() > 1024L) return@forEach
            val temporary = File(folder, ".${sound.targetName}.download")
            var conn: HttpURLConnection? = null
            try {
                // Only these explicitly selected HTTPS URLs are allowed.
                val url = URL(sound.url)
                require(url.protocol == "https" && url.host == "www.myinstants.com")
                conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 12000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Mozilla/5.0")
                    setRequestProperty("Accept", "audio/mpeg,*/*")
                }
                if (conn.responseCode !in 200..299)
                    error("HTTP ${conn.responseCode}")
                var total = 0L
                var signature = ByteArray(0)
                conn.inputStream.use { input ->
                    temporary.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val size = input.read(buffer)
                            if (size == -1) break
                            if (size == 0) continue
                            if (signature.isEmpty()) signature = buffer.copyOf(size.coerceAtMost(3))
                            total += size
                            if (total > 12_000_000L) error("Dosya boyutu sınırı aşıldı")
                            output.write(buffer, 0, size)
                        }
                    }
                }
                if (total < 1024L || !ShowSoundSources.hasMp3Header(signature))
                    error("Ses dosyası yerine geçersiz içerik geldi")
                if (!temporary.renameTo(destination))
                    error("TV klasörüne kaydedilemedi")
                downloaded++
            } catch (e: Exception) {
                failed.add("${sound.targetName.removePrefix("milyoner_")}: ${e.message ?: "bağlantı hatası"}")
            } finally {
                conn?.disconnect()
                if (temporary.exists()) temporary.delete()
            }
        }
        return ShowSoundDownloadResult(downloaded, installedCount(), failed)
    }
}

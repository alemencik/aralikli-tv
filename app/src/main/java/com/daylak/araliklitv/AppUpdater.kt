package com.daylak.araliklitv

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class UpdateRelease(val version: String, val downloadUrl: String, val sha256: String)

/**
 * Reads official public GitHub Releases, downloads an APK and verifies its
 * GitHub-published SHA-256 digest. Android verifies the signing certificate
 * and presents the installation confirmation screen.
 */
object AppUpdater {
    private const val releasesUrl =
        "https://api.github.com/repos/alemencik/aralikli-tv/releases?per_page=15"

    fun findUpdate(installedVersion: String): UpdateRelease? {
        val conn = (URL(releasesUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12000
            readTimeout = 12000
            setRequestProperty("User-Agent", "AralikliTV-Android")
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        val response = try {
            if (conn.responseCode !in 200..299)
                throw IllegalStateException("GitHub HTTP ${conn.responseCode}")
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally { conn.disconnect() }
        val releases = JSONArray(response)
        var best: UpdateRelease? = null
        for (i in 0 until releases.length()) {
            val release = releases.getJSONObject(i)
            if (release.optBoolean("draft", false)) continue
            val version = release.optString("tag_name").removePrefix("v").substringBefore("-")
            if (!isNewer(version, installedVersion)) continue
            val assets = release.optJSONArray("assets") ?: continue
            for (j in 0 until assets.length()) {
                val asset = assets.getJSONObject(j)
                if (!asset.optString("name").endsWith(".apk", ignoreCase = true)) continue
                val url = asset.optString("browser_download_url")
                if (!url.startsWith("https://github.com/alemencik/aralikli-tv/releases/download/")) continue
                val digest = asset.optString("digest")
                if (!digest.startsWith("sha256:")) continue
                val hash = digest.substringAfter("sha256:").lowercase()
                if (!hash.matches(Regex("[a-f0-9]{64}"))) continue
                if (best == null || isNewer(version, best.version))
                    best = UpdateRelease(version, url, hash)
            }
        }
        return best
    }

    internal fun isNewer(candidate: String, installed: String): Boolean {
        fun numbers(s: String): List<Int> {
            val values = s.substringBefore("-").split(".")
            return if (values.size == 3 && values.all { it.toIntOrNull()!=null })
                values.map { it.toInt() } else emptyList()
        }
        val a = numbers(candidate)
        val b = numbers(installed)
        if (a.size != 3 || b.size != 3) return false
        for (i in 0..2) {
            if (a[i] > b[i]) return true
            if (a[i] < b[i]) return false
        }
        return false
    }

    fun download(context: Context, update: UpdateRelease): File {
        val folder = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: throw IllegalStateException("İndirme dizini bulunamadı")
        if (!folder.exists()) folder.mkdirs()
        val file = File(folder, "AralikliTV-${update.version}.apk")
        if (file.exists() && verified(file, update.sha256)) return file
        val partial = File(folder, "araliklitv.download.tmp")
        try {
            val conn = URL(update.downloadUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 60000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "AralikliTV-Android")
            try {
                if (conn.responseCode !in 200..299)
                    throw IllegalStateException("APK HTTP ${conn.responseCode}")
                conn.inputStream.use { input ->
                    partial.outputStream().use { output -> input.copyTo(output) }
                }
            } finally { conn.disconnect() }
            if (!verified(partial, update.sha256))
                throw IllegalStateException("APK SHA-256 doğrulaması başarısız")
            if (file.exists()) file.delete()
            if (!partial.renameTo(file)) throw IllegalStateException("APK kaydedilemedi")
            return file
        } finally {
            if (partial.exists()) partial.delete()
        }
    }

    private fun verified(file: File, expectedHash: String): Boolean {
        if (!file.isFile || file.length() < 1024L) return false
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(16_384)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                hash.update(buffer, 0, count)
            }
        }
        val actual = hash.digest().joinToString("") { "%02x".format(it) }
        return actual.equals(expectedHash, ignoreCase = true)
    }

    /** Returns false when user must first allow installations from this app. */
    fun install(context: Context, apk: File): Boolean {
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(intent)
            return false
        }
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", apk
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return true
    }
}

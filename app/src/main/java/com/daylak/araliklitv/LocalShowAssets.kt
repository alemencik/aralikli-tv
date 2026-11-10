package com.daylak.araliklitv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import java.io.File

/**
 * Original TV-show images and sound effects are supplied ONLY by the user,
 * locally on their TV. No copyrighted binary asset is committed to GitHub.
 *
 * Put milyoner_stage.jpg (or .png) in:
 * /sdcard/Android/data/com.daylak.araliklitv/files/
 */
class LocalShowAssets(private val context: Context) {
    private var cachedFilePath: String? = null
    private var cachedModified: Long = -1L
    private var cachedBitmap: Bitmap? = null

    fun assetsDirectory(): File? = context.getExternalFilesDir(null)

    fun stageFile(): File? {
        val dir = assetsDirectory() ?: return null
        return listOf("milyoner_stage.jpg", "milyoner_stage.png")
            .map { File(dir, it) }
            .firstOrNull { it.isFile && it.length() > 0L }
    }

    fun hasStage(): Boolean = stageFile() != null

    fun stageBackground(): Drawable {
        val file = stageFile() ?: return StageDrawable()
        val path = file.absolutePath
        if (path != cachedFilePath || file.lastModified() != cachedModified) {
            cachedFilePath = path
            cachedModified = file.lastModified()
            cachedBitmap = decodeScaled(file)
        }
        val image = cachedBitmap ?: return StageDrawable()
        return UserStageDrawable(image)
    }

    fun soundFile(name: String): File? {
        if (name !in setOf("milyoner_correct.mp3", "milyoner_wrong.mp3",
                "milyoner_warning.mp3")) return null
        return assetsDirectory()?.let { File(it, name) }
            ?.takeIf { it.isFile && it.length() > 0L }
    }

    private fun decodeScaled(file: File): Bitmap? {
        return try {
            val measure = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, measure)
            if (measure.outWidth <= 0 || measure.outHeight <= 0) {
                null
            } else {
                var sample = 1
                while (measure.outWidth / (sample * 2) >= 1920 &&
                    measure.outHeight / (sample * 2) >= 1080) {
                    sample *= 2
                }
                BitmapFactory.decodeFile(file.absolutePath,
                    BitmapFactory.Options().apply { inSampleSize = sample })
            }
        } catch (_: Exception) {
            null
        }
    }
}

/** A genuine user-supplied photo with a dark tint to keep five answers readable. */
private class UserStageDrawable(private val image: Bitmap) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    override fun draw(canvas: Canvas) {
        val target = bounds
        if (target.isEmpty || image.isRecycled) return
        val destW = target.width().toFloat()
        val destH = target.height().toFloat()
        val aspect = destW / destH
        val srcWidth: Int
        val srcHeight: Int
        if (image.width.toFloat() / image.height > aspect) {
            srcHeight = image.height
            srcWidth = (image.height * aspect).toInt().coerceAtLeast(1)
        } else {
            srcWidth = image.width
            srcHeight = (image.width / aspect).toInt().coerceAtLeast(1)
        }
        val source = Rect(
            (image.width - srcWidth) / 2, (image.height - srcHeight) / 2,
            (image.width + srcWidth) / 2, (image.height + srcHeight) / 2
        )
        paint.color = Color.WHITE
        paint.alpha = 255
        canvas.drawBitmap(image, source, RectF(target), paint)
        paint.color = Color.argb(135, 1, 6, 24)
        canvas.drawRect(target, paint)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Android drawable opacity")
    override fun getOpacity(): Int = PixelFormat.OPAQUE
}

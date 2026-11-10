package com.daylak.araliklitv

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

/** Original neon quiz-studio backdrop, without broadcast images or logos. */
class StageDrawable : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private fun color(value: String) = Color.parseColor(value)

    override fun draw(canvas: Canvas) {
        val width = bounds.width().toFloat()
        val height = bounds.height().toFloat()
        if (width <= 0f || height <= 0f) return
        canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(0f, 0f, width, height,
            intArrayOf(color("#050B20"), color("#191148"), color("#090C27")),
            null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width, height, paint)
        paint.shader = null
        val glowPositions = listOf(
            Triple(.06f, .26f, color("#275BBD")),
            Triple(.93f, .23f, color("#783ABA")),
            Triple(.51f, .88f, color("#174B93"))
        )
        for ((x,y,c) in glowPositions) {
            paint.shader = RadialGradient(width*x, height*y, width*.50f,
                intArrayOf(Color.argb(95, Color.red(c), Color.green(c), Color.blue(c)),
                    Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
            canvas.drawCircle(width*x, height*y, width*.50f, paint)
        }
        paint.shader = null
        for (i in 0..6) {
            val topX = width*(i+.5f)/7f
            val endX = width*(.08f+i*.14f)
            val beam = Path().apply {
                moveTo(topX-5f, 0f); lineTo(topX+5f, 0f)
                lineTo(endX+width*.17f, height*.76f)
                lineTo(endX-width*.17f, height*.76f)
                close()
            }
            paint.color = if (i%2 == 0) color("#235CB8") else color("#7D31B6")
            paint.alpha = 24
            canvas.drawPath(beam, paint)
        }
        paint.style = Paint.Style.STROKE
        for (i in 0..3) {
            val r = height*(.25f+i*.08f)
            paint.color = if (i%2==0) color("#2176D8") else color("#9D51DD")
            paint.alpha = if (i==0) 85 else 46
            paint.strokeWidth = if (i==0) 5f else 2f
            canvas.drawArc(RectF(width*.5f-r, height*.32f-r,
                width*.5f+r, height*.32f+r), 195f, 150f, false, paint)
        }
        for (i in 0..3) {
            paint.color = if(i%2==0) color("#2D84EF") else color("#A05ADA")
            paint.alpha = if(i==0) 110 else 55
            paint.strokeWidth = if(i==0) 5f else 2f
            canvas.drawOval(RectF(width*(.08f-i*.045f), height*(.78f-i*.07f),
                width*(.92f+i*.045f), height*(.94f+i*.015f)),paint)
        }
        paint.shader = null
        paint.alpha = 255
        canvas.restore()
    }
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(filter: android.graphics.ColorFilter?) {}
    @Deprecated("Drawable opacity")
    override fun getOpacity() = PixelFormat.OPAQUE
}

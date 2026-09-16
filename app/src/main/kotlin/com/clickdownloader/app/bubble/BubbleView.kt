package com.clickdownloader.app.bubble

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.min

internal class BubbleView(context: Context) : View(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(20, 92, 230) }
    private val mark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 3f
        strokeCap = Paint.Cap.ROUND
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(82, 224, 154)
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 3f
    }
    var progress: Float? = null
        set(value) {
            field = value?.coerceIn(0f, 1f)
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = min(width, height).toFloat()
        val center = size / 2f
        canvas.drawCircle(center, center, size * .43f, fill)
        canvas.drawLine(center, size * .25f, center, size * .62f, mark)
        canvas.drawLine(size * .36f, size * .50f, center, size * .64f, mark)
        canvas.drawLine(size * .64f, size * .50f, center, size * .64f, mark)
        canvas.drawLine(size * .32f, size * .73f, size * .68f, size * .73f, mark)
        progress?.let {
            val inset = progressPaint.strokeWidth
            canvas.drawArc(RectF(inset, inset, size - inset, size - inset), -90f, it * 360f, false, progressPaint)
        }
    }
}

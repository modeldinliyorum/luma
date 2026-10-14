package com.luma.reader.ui.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.luma.reader.data.Highlight
import com.luma.reader.ui.theme.HighlightMarkArgb
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** A rectangle expressed as fractions of the page (0..1), zoom independent. */
data class NormalizedRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

@Composable
fun PdfPage(
    bitmap: Bitmap?,
    highlights: List<Highlight>,
    onHighlight: (NormalizedRect) -> Unit,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { context -> PdfCanvasView(context).also { it.onHighlight = onHighlight } },
        update = { view ->
            view.onHighlight = onHighlight
            view.setContent(bitmap, highlights)
        }
    )
}

/**
 * Draws the current page bitmap and overlays translucent highlighter strokes.
 * A drag paints a pencil band; the released rectangle is reported as a
 * NormalizedRect so it survives zoom and relaunch.
 */
class PdfCanvasView(context: Context) : View(context) {

    private var bitmap: Bitmap? = null
    private var highlights: List<Highlight> = emptyList()
    var onHighlight: ((NormalizedRect) -> Unit)? = null

    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val markPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = HighlightMarkArgb
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE6C34A.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val dest = RectF()
    private var dragStart: PointF? = null
    private var dragEnd: PointF? = null

    fun setContent(newBitmap: Bitmap?, newHighlights: List<Highlight>) {
        bitmap = newBitmap
        highlights = newHighlights
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeDest()
    }

    private fun computeDest() {
        val bmp = bitmap ?: return
        if (width == 0 || height == 0) return
        val scale = min(width.toFloat() / bmp.width, height.toFloat() / bmp.height)
        val drawWidth = bmp.width * scale
        val drawHeight = bmp.height * scale
        val left = (width - drawWidth) / 2f
        val top = (height - drawHeight) / 2f
        dest.set(left, top, left + drawWidth, top + drawHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = bitmap ?: return
        computeDest()
        canvas.drawBitmap(bmp, null, dest, bitmapPaint)

        highlights.forEach { mark ->
            val left = mark.rectLeft ?: return@forEach
            val top = mark.rectTop ?: return@forEach
            val right = mark.rectRight ?: return@forEach
            val bottom = mark.rectBottom ?: return@forEach
            val rect = RectF(
                dest.left + left * dest.width(),
                dest.top + top * dest.height(),
                dest.left + right * dest.width(),
                dest.top + bottom * dest.height()
            )
            canvas.drawRoundRect(rect, 4f, 4f, markPaint)
        }

        val start = dragStart
        val end = dragEnd
        if (start != null && end != null) {
            val rect = RectF(min(start.x, end.x), min(start.y, end.y), max(start.x, end.x), max(start.y, end.y))
            canvas.drawRoundRect(rect, 4f, 4f, markPaint)
            canvas.drawRoundRect(rect, 4f, 4f, strokePaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (bitmap == null) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragStart = PointF(event.x, event.y)
                dragEnd = dragStart
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (dragStart != null) {
                    dragEnd = PointF(event.x, event.y)
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val start = dragStart
                val end = dragEnd
                dragStart = null
                dragEnd = null
                invalidate()
                if (start != null && end != null && hypot(end.x - start.x, end.y - start.y) > TOUCH_SLOP) {
                    onHighlight?.invoke(toNormalized(start, end))
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun toNormalized(a: PointF, b: PointF): NormalizedRect {
        val w = dest.width().coerceAtLeast(1f)
        val h = dest.height().coerceAtLeast(1f)
        return NormalizedRect(
            left = ((min(a.x, b.x) - dest.left) / w).coerceIn(0f, 1f),
            top = ((min(a.y, b.y) - dest.top) / h).coerceIn(0f, 1f),
            right = ((max(a.x, b.x) - dest.left) / w).coerceIn(0f, 1f),
            bottom = ((max(a.y, b.y) - dest.top) / h).coerceIn(0f, 1f)
        )
    }

    private companion object {
        const val TOUCH_SLOP = 24f
    }
}

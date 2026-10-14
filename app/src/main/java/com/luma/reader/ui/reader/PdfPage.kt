package com.luma.reader.ui.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.luma.reader.data.Highlight
import com.luma.reader.util.ReadingLogic
import kotlin.math.min

@Composable
fun PdfPageContent(
    page: Int,
    bitmaps: Map<Int, Bitmap>,
    highlights: List<Highlight>,
    penMode: Boolean,
    color: Int,
    ensureRendered: (Int) -> Unit,
    onStroke: (List<Pair<Float, Float>>) -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(page) { ensureRendered(page) }
    PdfPage(
        bitmap = bitmaps[page],
        highlights = highlights,
        penMode = penMode,
        color = color,
        onStroke = onStroke,
        modifier = modifier
    )
}

@Composable
fun PdfPage(
    bitmap: Bitmap?,
    highlights: List<Highlight>,
    penMode: Boolean,
    color: Int,
    onStroke: (List<Pair<Float, Float>>) -> Unit,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { context -> PdfCanvasView(context).also { it.onStroke = onStroke } },
        update = { view ->
            view.onStroke = onStroke
            view.setContent(bitmap, highlights, penMode, color)
        }
    )
}

/**
 * Draws the current page bitmap and overlays saved pen strokes. While pen mode
 * is on a drag paints a freehand stroke and the touch is consumed. While it is
 * off the view does not consume touches, so the pager swipe can change pages.
 */
class PdfCanvasView(context: Context) : View(context) {

    private var bitmap: Bitmap? = null
    private var highlights: List<Highlight> = emptyList()
    private var penMode = false
    private var strokeColor = 0xFFF5C518.toInt()
    var onStroke: ((List<Pair<Float, Float>>) -> Unit)? = null

    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 12f * resources.displayMetrics.density
    }
    private val dest = RectF()
    private val live = mutableListOf<Pair<Float, Float>>()

    fun setContent(newBitmap: Bitmap?, newHighlights: List<Highlight>, pen: Boolean, color: Int) {
        bitmap = newBitmap
        highlights = newHighlights
        penMode = pen
        strokeColor = color
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

        highlights.forEach { highlight ->
            val encoded = highlight.stroke
            if (!encoded.isNullOrBlank()) {
                val points = ReadingLogic.decodeStroke(encoded).map { toView(it.first, it.second) }
                drawStroke(canvas, points, highlight.color, strokePaint)
            }
        }

        if (live.size >= 2) {
            drawStroke(canvas, live, strokeColor, strokePaint)
        }
    }

    private fun drawStroke(canvas: Canvas, points: List<Pair<Float, Float>>, color: Int, paint: Paint) {
        if (points.size < 2) return
        paint.color = color
        val path = Path()
        path.moveTo(points[0].first, points[0].second)
        for (i in 1 until points.size) path.lineTo(points[i].first, points[i].second)
        canvas.drawPath(path, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (bitmap == null || !penMode) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                live.clear()
                live.add(event.x to event.y)
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                live.add(event.x to event.y)
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val points = live.toList()
                live.clear()
                invalidate()
                val normalized = points
                    .filter { dest.contains(it.first, it.second) }
                    .map { toNormalized(it.first, it.second) }
                if (normalized.size >= 2) onStroke?.invoke(normalized)
                return true
            }
        }
        return true
    }

    private fun toView(x: Float, y: Float): Pair<Float, Float> =
        (dest.left + x * dest.width()) to (dest.top + y * dest.height())

    private fun toNormalized(x: Float, y: Float): Pair<Float, Float> {
        val w = dest.width().coerceAtLeast(1f)
        val h = dest.height().coerceAtLeast(1f)
        return ((x - dest.left) / w).coerceIn(0f, 1f) to ((y - dest.top) / h).coerceIn(0f, 1f)
    }
}

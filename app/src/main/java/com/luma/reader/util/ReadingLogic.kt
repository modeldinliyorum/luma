package com.luma.reader.util

import com.luma.reader.data.Highlight
import java.util.Locale

/** Pure, testable reading rules with no Android dependencies. */
object ReadingLogic {

    /** Page numbers are 0-based; the result is always a valid page. */
    fun clampPage(page: Int, pageCount: Int): Int =
        if (pageCount <= 0) 0 else page.coerceIn(0, pageCount - 1)

    fun isValidPage(page: Int, pageCount: Int): Boolean =
        pageCount > 0 && page in 0 until pageCount

    /** Progress in 0..1 for a 0-based page. */
    fun progressFor(page: Int, pageCount: Int): Float =
        if (pageCount <= 0) 0f else ((page + 1).toFloat() / pageCount).coerceIn(0f, 1f)

    /**
     * A candidate is a duplicate when an existing mark sits on the same page at
     * the same spot: same trimmed text for EPUB selections, same normalized path
     * for pen strokes, or the same rounded rectangle as a fallback.
     */
    fun isDuplicate(existing: List<Highlight>, candidate: Highlight): Boolean =
        existing.any { it.pageNumber == candidate.pageNumber && it.sameSpotAs(candidate) }

    private fun Highlight.sameSpotAs(other: Highlight): Boolean = when {
        text.isNotBlank() || other.text.isNotBlank() ->
            text.trim().equals(other.text.trim(), ignoreCase = true)

        !stroke.isNullOrBlank() || !other.stroke.isNullOrBlank() ->
            stroke.orEmpty() == other.stroke.orEmpty()

        else -> rectKey() == other.rectKey()
    }

    private fun Highlight.rectKey(): String =
        listOf(rectLeft, rectTop, rectRight, rectBottom).joinToString(",") { v ->
            v?.let { Math.round(it * 100).toFloat() / 100f }?.toString() ?: "-"
        }

    // ---- Pen stroke codec (normalized points, 0..1) ----

    fun encodeStroke(points: List<Pair<Float, Float>>): String =
        points.joinToString(";") { "${round3(it.first)},${round3(it.second)}" }

    fun decodeStroke(encoded: String): List<Pair<Float, Float>> =
        encoded.split(';').mapNotNull { token ->
            val parts = token.split(',')
            val x = parts.getOrNull(0)?.toFloatOrNull()
            val y = parts.getOrNull(1)?.toFloatOrNull()
            if (x != null && y != null) x to y else null
        }

    private fun round3(value: Float): Float = Math.round(value * 1000f) / 1000f

    // ---- Colour helpers ----

    /** Combine an opaque RGB colour with an opacity into an ARGB int. */
    fun withOpacity(rgb: Int, opacity: Float): Int {
        val alpha = (opacity.coerceIn(0f, 1f) * 255f).toInt()
        return (alpha shl 24) or (rgb and 0xFFFFFF)
    }

    fun rgbOf(argb: Int): Int = argb and 0xFFFFFF

    /** A CSS rgba() string for the EPUB highlighter. */
    fun cssColor(argb: Int): String {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        val a = ((argb ushr 24) and 0xFF) / 255f
        return "rgba($r,$g,$b,${String.format(Locale.US, "%.3f", a)})"
    }
}

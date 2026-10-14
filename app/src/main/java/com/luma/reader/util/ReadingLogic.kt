package com.luma.reader.util

import com.luma.reader.data.Highlight

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
     * the same spot: same trimmed text for EPUB selections, or the same rounded
     * rectangle for text-free PDF pencil marks.
     */
    fun isDuplicate(existing: List<Highlight>, candidate: Highlight): Boolean =
        existing.any { it.pageNumber == candidate.pageNumber && it.sameSpotAs(candidate) }

    private fun Highlight.sameSpotAs(other: Highlight): Boolean =
        if (text.isNotBlank() || other.text.isNotBlank()) {
            text.trim().equals(other.text.trim(), ignoreCase = true)
        } else {
            rectKey() == other.rectKey()
        }

    private fun Highlight.rectKey(): String =
        listOf(rectLeft, rectTop, rectRight, rectBottom).joinToString(",") { v ->
            v?.let { Math.round(it * 100).toFloat() / 100f }?.toString() ?: "-"
        }
}

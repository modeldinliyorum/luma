package com.luma.reader

import com.luma.reader.data.Highlight
import com.luma.reader.util.ReadingLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingLogicTest {

    @Test
    fun clampsOutOfRangePages() {
        assertEquals(0, ReadingLogic.clampPage(-5, 10))
        assertEquals(9, ReadingLogic.clampPage(99, 10))
        assertEquals(0, ReadingLogic.clampPage(3, 0))
    }

    @Test
    fun validatesPageNumbers() {
        assertTrue(ReadingLogic.isValidPage(0, 5))
        assertTrue(ReadingLogic.isValidPage(4, 5))
        assertFalse(ReadingLogic.isValidPage(5, 5))
        assertFalse(ReadingLogic.isValidPage(-1, 5))
        assertFalse(ReadingLogic.isValidPage(0, 0))
    }

    @Test
    fun progressesFromOneBasedPageFraction() {
        assertEquals(0.5f, ReadingLogic.progressFor(0, 2), 0.0001f)
        assertEquals(1f, ReadingLogic.progressFor(4, 5), 0.0001f)
        assertEquals(0f, ReadingLogic.progressFor(0, 0), 0.0001f)
    }

    @Test
    fun detectsDuplicateTextHighlights() {
        val existing = listOf(Highlight(id = 1, bookId = 1, pageNumber = 2, text = "Hello world"))
        val duplicate = Highlight(bookId = 1, pageNumber = 2, text = "  hello WORLD ")
        val differentText = Highlight(bookId = 1, pageNumber = 2, text = "Another line")
        val differentPage = Highlight(bookId = 1, pageNumber = 3, text = "Hello world")

        assertTrue(ReadingLogic.isDuplicate(existing, duplicate))
        assertFalse(ReadingLogic.isDuplicate(existing, differentText))
        assertFalse(ReadingLogic.isDuplicate(existing, differentPage))
    }

    @Test
    fun detectsDuplicatePencilRegions() {
        val existing = listOf(
            Highlight(bookId = 1, pageNumber = 0, text = "", rectLeft = 0.1f, rectTop = 0.1f, rectRight = 0.5f, rectBottom = 0.2f)
        )
        val duplicate = Highlight(
            bookId = 1, pageNumber = 0, text = "", rectLeft = 0.1f, rectTop = 0.1f, rectRight = 0.5f, rectBottom = 0.2f
        )
        val different = Highlight(
            bookId = 1, pageNumber = 0, text = "", rectLeft = 0.3f, rectTop = 0.3f, rectRight = 0.6f, rectBottom = 0.4f
        )

        assertTrue(ReadingLogic.isDuplicate(existing, duplicate))
        assertFalse(ReadingLogic.isDuplicate(existing, different))
    }

    @Test
    fun encodesAndDecodesStrokes() {
        val points = listOf(0.1f to 0.2f, 0.5f to 0.6f, 0.9f to 0.4f)
        val decoded = ReadingLogic.decodeStroke(ReadingLogic.encodeStroke(points))
        assertEquals(3, decoded.size)
        assertEquals(0.1f, decoded[0].first, 0.001f)
        assertEquals(0.4f, decoded[2].second, 0.001f)
        assertTrue(ReadingLogic.decodeStroke("").isEmpty())
    }

    @Test
    fun detectsDuplicateStrokes() {
        val stroke = ReadingLogic.encodeStroke(listOf(0.1f to 0.1f, 0.3f to 0.3f))
        val existing = listOf(Highlight(bookId = 1, pageNumber = 0, text = "", stroke = stroke))
        val duplicate = Highlight(bookId = 1, pageNumber = 0, text = "", stroke = stroke)
        val different = Highlight(
            bookId = 1,
            pageNumber = 0,
            text = "",
            stroke = ReadingLogic.encodeStroke(listOf(0.4f to 0.4f, 0.6f to 0.6f))
        )
        assertTrue(ReadingLogic.isDuplicate(existing, duplicate))
        assertFalse(ReadingLogic.isDuplicate(existing, different))
    }

    @Test
    fun appliesOpacityToColour() {
        val argb = ReadingLogic.withOpacity(0xF5C518, 0.5f)
        assertEquals(127, (argb ushr 24) and 0xFF)
        assertEquals(0xF5C518, ReadingLogic.rgbOf(argb))
    }

    @Test
    fun buildsCssColour() {
        val argb = ReadingLogic.withOpacity(0xF5C518, 0.4f)
        assertEquals("rgba(245,197,24,0.400)", ReadingLogic.cssColor(argb))
    }
}

package com.luma.reader.data

import android.content.ContentResolver
import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.luma.reader.epub.EpubParser
import java.io.File

/**
 * Copies a picked document into app-specific internal storage so no external
 * path or permission is needed after the picker closes.
 */
object BookImporter {

    fun import(context: Context, uri: Uri): Book? {
        val resolver = context.contentResolver
        val displayName = queryName(resolver, uri) ?: "document"
        val format = detectFormat(resolver, uri, displayName) ?: return null

        val booksDir = File(context.filesDir, "books").apply { mkdirs() }
        val stored = File(booksDir, "${System.currentTimeMillis()}_${sanitize(displayName)}")
        val copied = runCatching {
            resolver.openInputStream(uri)?.use { input ->
                stored.outputStream().use { output -> input.copyTo(output) }
            } != null
        }.getOrDefault(false)
        if (!copied) return null

        val pageCount = runCatching {
            when (format) {
                BookFormat.PDF -> pdfPageCount(stored)
                BookFormat.EPUB -> EpubParser.parse(stored, epubRootDir(context, stored.nameWithoutExtension))
                    .chapters.size
            }
        }.getOrDefault(0)

        return Book(
            path = stored.absolutePath,
            title = displayName.substringBeforeLast('.').ifBlank { displayName },
            format = format,
            pageCount = pageCount
        )
    }

    /** Extracted-chapter directory for a stored document, keyed by file name. */
    fun epubRootDir(context: Context, storedName: String): File =
        File(context.filesDir, "epub/$storedName")

    fun epubRootDir(context: Context, book: Book): File =
        epubRootDir(context, File(book.path).nameWithoutExtension)

    private fun detectFormat(resolver: ContentResolver, uri: Uri, name: String): BookFormat? {
        val mime = resolver.getType(uri)?.lowercase()
        return when {
            mime == "application/pdf" -> BookFormat.PDF
            mime == "application/epub+zip" -> BookFormat.EPUB
            name.endsWith(".pdf", ignoreCase = true) -> BookFormat.PDF
            name.endsWith(".epub", ignoreCase = true) -> BookFormat.EPUB
            else -> null
        }
    }

    private fun pdfPageCount(file: File): Int =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { it.pageCount }
        }

    private fun queryName(resolver: ContentResolver, uri: Uri): String? =
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            } else {
                null
            }
        }

    private fun sanitize(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "document" }
}

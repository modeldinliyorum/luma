package com.luma.reader.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class BookFormat { PDF, EPUB }

/** Amber yellow at roughly 40% alpha. The default highlight colour. */
const val DEFAULT_HIGHLIGHT_COLOR: Int = 0x66F5C518

/**
 * Core spec: id, path, lastReadPosition, lastPageNumber.
 * Extras the UI needs: title (shown in the library and reader), format (chooses
 * a renderer), pageCount (progress bar), addedAt (sort order).
 */
@Entity(tableName = "books")
data class Book(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val path: String,
    val title: String,
    val format: BookFormat,
    val lastReadPosition: Float = 0f,
    val lastPageNumber: Int = 0,
    val pageCount: Int = 0,
    val addedAt: Long = System.currentTimeMillis()
)

/**
 * Core spec: id, bookId, pageNumber, text, timestamp.
 * Extras so a mark can be redrawn after relaunch: `color` carries the chosen
 * ARGB, EPUB text marks store character offsets, and PDF marks store a
 * normalized freehand stroke path.
 */
@Entity(
    tableName = "highlights",
    foreignKeys = [
        ForeignKey(
            entity = Book::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("bookId")]
)
data class Highlight(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val bookId: Long,
    val pageNumber: Int,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    /** ARGB including the chosen opacity. */
    val color: Int = DEFAULT_HIGHLIGHT_COLOR,
    /** Freehand pen stroke as normalized "x,y;x,y" pairs. Null for text marks. */
    val stroke: String? = null,
    val startOffset: Int? = null,
    val endOffset: Int? = null,
    val rectLeft: Float? = null,
    val rectTop: Float? = null,
    val rectRight: Float? = null,
    val rectBottom: Float? = null
)

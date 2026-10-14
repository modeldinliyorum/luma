package com.luma.reader.data

import com.luma.reader.util.ReadingLogic
import kotlinx.coroutines.flow.Flow

class LibraryRepository(
    private val bookDao: BookDao,
    private val highlightDao: HighlightDao
) {
    fun observeBooks(): Flow<List<Book>> = bookDao.observeAll()
    fun observeBook(id: Long): Flow<Book?> = bookDao.observeById(id)
    fun observeHighlights(bookId: Long): Flow<List<Highlight>> = highlightDao.observeForBook(bookId)

    suspend fun getBook(id: Long): Book? = bookDao.getById(id)
    suspend fun addBook(book: Book): Long = bookDao.insert(book)
    suspend fun updateBook(book: Book) = bookDao.update(book)
    suspend fun deleteBook(book: Book) = bookDao.delete(book)

    suspend fun highlightsForPage(bookId: Long, page: Int): List<Highlight> =
        highlightDao.getForPage(bookId, page)

    /** Returns the new row id, or null when the mark already exists on that page. */
    suspend fun addHighlightIfNew(candidate: Highlight): Long? {
        val existing = highlightDao.getForPage(candidate.bookId, candidate.pageNumber)
        if (ReadingLogic.isDuplicate(existing, candidate)) return null
        return highlightDao.insert(candidate)
    }

    suspend fun deleteHighlight(highlight: Highlight) = highlightDao.delete(highlight)
}

package com.luma.reader.ui.reader

import android.app.Application
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.luma.reader.data.Book
import com.luma.reader.data.BookFormat
import com.luma.reader.data.BookImporter
import com.luma.reader.data.Highlight
import com.luma.reader.data.LibraryRepository
import com.luma.reader.epub.EpubBook
import com.luma.reader.epub.EpubParser
import com.luma.reader.util.ReadingLogic
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class ReaderUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val book: Book? = null,
    val page: Int = 0,
    val pageCount: Int = 0,
    val highlights: List<Highlight> = emptyList(),
    val epubChapters: List<File> = emptyList()
)

class ReaderViewModel(
    private val repository: LibraryRepository,
    private val application: Application,
    private val bookId: Long
) : AndroidViewModel(application) {

    private var renderer: PdfRenderer? = null
    private var descriptor: ParcelFileDescriptor? = null
    private var epub: EpubBook? = null
    private val pdfLock = Mutex()

    private val _ui = MutableStateFlow(ReaderUiState())
    val ui: StateFlow<ReaderUiState> = _ui.asStateFlow()

    private val _pageBitmaps = MutableStateFlow<Map<Int, Bitmap>>(emptyMap())
    val pageBitmaps: StateFlow<Map<Int, Bitmap>> = _pageBitmaps.asStateFlow()

    init {
        viewModelScope.launch {
            val book = repository.getBook(bookId)
            if (book == null) {
                _ui.update { it.copy(loading = false, error = "This book is no longer available.") }
                return@launch
            }
            openDocument(book)
            val detected = when (book.format) {
                BookFormat.PDF -> renderer?.pageCount ?: 0
                BookFormat.EPUB -> epub?.chapters?.size ?: 0
            }
            val count = if (detected > 0) detected else book.pageCount
            val start = ReadingLogic.clampPage(book.lastPageNumber, count)
            _ui.update {
                it.copy(
                    loading = false,
                    book = book,
                    page = start,
                    pageCount = count,
                    epubChapters = epub?.chapters?.map { chapter -> chapter.file }.orEmpty()
                )
            }
            repository.observeHighlights(bookId).collect { marks ->
                _ui.update { state -> state.copy(highlights = marks) }
            }
        }
    }

    private fun openDocument(book: Book) {
        when (book.format) {
            BookFormat.PDF -> runCatching {
                val fd = ParcelFileDescriptor.open(File(book.path), ParcelFileDescriptor.MODE_READ_ONLY)
                descriptor = fd
                renderer = PdfRenderer(fd)
            }.onFailure {
                _ui.update { state -> state.copy(error = "Could not open this PDF.") }
            }

            BookFormat.EPUB -> runCatching {
                epub = EpubParser.parse(File(book.path), BookImporter.epubRootDir(application, book))
            }.onFailure {
                _ui.update { state -> state.copy(error = "Could not open this EPUB.") }
            }
        }
    }

    /** Render a PDF page into the small LRU cache if it is not there yet. */
    fun ensurePageRendered(page: Int) {
        if (_pageBitmaps.value.containsKey(page)) return
        val pdf = renderer ?: return
        if (pdf.pageCount == 0) return
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                pdfLock.withLock {
                    runCatching {
                        pdf.openPage(page.coerceIn(0, pdf.pageCount - 1)).use { pdfPage ->
                            val scale = PDF_RENDER_WIDTH.toFloat() / pdfPage.width.toFloat()
                            val height = (pdfPage.height * scale).toInt().coerceAtLeast(1)
                            val target = Bitmap.createBitmap(PDF_RENDER_WIDTH, height, Bitmap.Config.ARGB_8888)
                            target.eraseColor(android.graphics.Color.WHITE)
                            pdfPage.render(target, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            target
                        }
                    }.getOrNull()
                }
            } ?: return@launch

            if (_pageBitmaps.value.containsKey(page)) return@launch
            val updated = LinkedHashMap(_pageBitmaps.value)
            updated[page] = bitmap
            val keys = updated.keys.toList()
            if (keys.size > MAX_CACHED_PAGES) {
                keys.take(keys.size - MAX_CACHED_PAGES).forEach { updated.remove(it) }
            }
            _pageBitmaps.value = updated
        }
    }

    fun goToPage(page: Int) {
        val state = _ui.value
        if (!ReadingLogic.isValidPage(page, state.pageCount)) return
        if (page == state.page) return
        _ui.update { it.copy(page = page) }
        val book = state.book ?: return
        persistPosition(book, page, state.pageCount)
    }

    fun nextPage() = goToPage(_ui.value.page + 1)

    fun previousPage() = goToPage(_ui.value.page - 1)

    private fun persistPosition(book: Book, page: Int, pageCount: Int) {
        viewModelScope.launch {
            repository.updateBook(
                book.copy(
                    lastPageNumber = page,
                    lastReadPosition = ReadingLogic.progressFor(page, pageCount)
                )
            )
        }
    }

    fun addPenHighlight(points: List<Pair<Float, Float>>, color: Int) {
        val state = _ui.value
        val book = state.book ?: return
        if (book.format != BookFormat.PDF) return
        if (points.size < 2) return
        val encoded = ReadingLogic.encodeStroke(points)
        viewModelScope.launch {
            repository.addHighlightIfNew(
                Highlight(
                    bookId = book.id,
                    pageNumber = state.page,
                    text = "",
                    color = color,
                    stroke = encoded
                )
            )
        }
    }

    fun addEpubHighlight(selectedText: String, color: Int) {
        val text = selectedText.trim()
        if (text.isEmpty()) return
        val state = _ui.value
        val book = state.book ?: return
        if (book.format != BookFormat.EPUB) return
        viewModelScope.launch {
            repository.addHighlightIfNew(
                Highlight(bookId = book.id, pageNumber = state.page, text = text, color = color)
            )
        }
    }

    fun deleteHighlight(highlight: Highlight) {
        viewModelScope.launch { repository.deleteHighlight(highlight) }
    }

    override fun onCleared() {
        super.onCleared()
        runCatching { renderer?.close() }
        runCatching { descriptor?.close() }
        renderer = null
        descriptor = null
    }

    companion object {
        private const val PDF_RENDER_WIDTH = 1240
        private const val MAX_CACHED_PAGES = 3
    }
}

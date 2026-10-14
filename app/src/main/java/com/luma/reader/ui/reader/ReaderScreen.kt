package com.luma.reader.ui.reader

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.luma.reader.AppContainer
import com.luma.reader.LumaApp
import com.luma.reader.data.BookFormat
import com.luma.reader.data.Highlight
import com.luma.reader.data.ThemeMode
import com.luma.reader.util.ReadingLogic
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(container: AppContainer, onBack: () -> Unit) {
    val viewModel: ReaderViewModel = viewModel(factory = viewModelFactory {
        initializer {
            val app = this[APPLICATION_KEY] as LumaApp
            val handle = createSavedStateHandle()
            val bookId = handle.get<Long>("bookId") ?: -1L
            ReaderViewModel(app.container.repository, app, bookId)
        }
    })

    val ui by viewModel.ui.collectAsState()
    val bitmap by viewModel.pageBitmap.collectAsState()
    val fontScale by container.settings.fontScale.collectAsState()
    val themeMode by container.settings.theme.collectAsState()

    val context = LocalContext.current
    val controller = remember { WebViewController() }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showPanel by remember { mutableStateOf(false) }

    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val chapterFile = ui.epubChapters.getOrNull(ui.page)

    LaunchedEffect(chapterFile, fontScale, dark) {
        val file = chapterFile ?: return@LaunchedEffect
        controller.loadChapter(context, file, fontScale, dark)
    }

    LaunchedEffect(ui.highlights, ui.page) {
        controller.applyHighlights(
            ui.highlights.filter { it.pageNumber == ui.page && it.text.isNotBlank() }.map { it.text }
        )
    }

    val pageHighlights = ui.highlights.filter { it.pageNumber == ui.page }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(ui.book?.title ?: "Reading", maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (ui.book?.format == BookFormat.EPUB) {
                        IconButton(onClick = {
                            controller.readSelection { text ->
                                if (text.isBlank()) {
                                    scope.launch { snackbarHostState.showSnackbar("Select some text first") }
                                } else {
                                    viewModel.addEpubHighlight(text)
                                }
                            }
                        }) {
                            Icon(Icons.Default.Add, contentDescription = "Highlight selection")
                        }
                    }
                    IconButton(onClick = { showPanel = true }, enabled = ui.highlights.isNotEmpty()) {
                        Icon(Icons.Default.Star, contentDescription = "Highlights")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (!ui.loading && ui.error == null && ui.pageCount > 0) {
                Column {
                    LinearProgressIndicator(
                        progress = { ReadingLogic.progressFor(ui.page, ui.pageCount) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = viewModel::previousPage, enabled = ui.page > 0) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous page")
                        }
                        Text("${ui.page + 1} / ${ui.pageCount}", style = MaterialTheme.typography.labelLarge)
                        IconButton(onClick = viewModel::nextPage, enabled = ui.page < ui.pageCount - 1) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next page")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                ui.loading -> CenteredMessage(text = "Opening…", loading = true)
                ui.error != null -> CenteredMessage(text = ui.error!!)
                ui.book == null -> CenteredMessage(text = "Nothing to show.")
                ui.pageCount == 0 -> CenteredMessage(text = "This document has no readable pages.")
                ui.book?.format == BookFormat.PDF -> PdfPage(
                    bitmap = bitmap,
                    highlights = pageHighlights,
                    onHighlight = viewModel::addPdfHighlight,
                    modifier = Modifier.fillMaxSize()
                )
                else -> EpubWebView(controller = controller, modifier = Modifier.fillMaxSize())
            }
        }
    }

    if (showPanel) {
        ModalBottomSheet(onDismissRequest = { showPanel = false }) {
            HighlightPanel(
                highlights = pageHighlights,
                pageNumber = ui.page,
                onDelete = viewModel::deleteHighlight
            )
        }
    }
}

@Composable
private fun CenteredMessage(text: String, loading: Boolean = false) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (loading) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HighlightPanel(
    highlights: List<Highlight>,
    pageNumber: Int,
    onDelete: (Highlight) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Text(
            "Highlights on page ${pageNumber + 1}",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        )
        if (highlights.isEmpty()) {
            Text(
                "No highlights on this page yet.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        } else {
            LazyColumn {
                items(highlights, key = { it.id }) { highlight ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = highlight.text.ifBlank { "Pencil mark" },
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(onClick = { onDelete(highlight) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete highlight")
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

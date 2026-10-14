package com.luma.reader.ui.reader

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
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
import com.luma.reader.data.SettingsStore
import com.luma.reader.data.ThemeMode
import com.luma.reader.ui.theme.HighlightColorOptions
import com.luma.reader.ui.theme.opaqueColor
import com.luma.reader.util.ReadingLogic
import kotlin.math.abs
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
    val bitmaps by viewModel.pageBitmaps.collectAsState()
    val fontScale by container.settings.fontScale.collectAsState()
    val themeMode by container.settings.theme.collectAsState()
    val highlightRgb by container.settings.highlightColor.collectAsState()
    val highlightOpacity by container.settings.highlightOpacity.collectAsState()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val controllers = remember { mutableStateMapOf<Int, WebViewController>() }
    val dragAccum = remember { FloatArray(1) }
    val swipeThreshold = with(LocalDensity.current) { 64.dp.toPx() }

    var penMode by remember { mutableStateOf(false) }
    var showColors by remember { mutableStateOf(false) }
    var showPanel by remember { mutableStateOf(false) }
    var hasSelection by remember { mutableStateOf(false) }

    val highlightArgb = ReadingLogic.withOpacity(highlightRgb, highlightOpacity)

    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    LaunchedEffect(ui.page) { hasSelection = false }

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
                    if (ui.book?.format == BookFormat.PDF) {
                        IconButton(onClick = { penMode = !penMode }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Highlight mode",
                                tint = if (penMode) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                    IconButton(onClick = { showColors = true }) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(opaqueColor(highlightRgb))
                        )
                    }
                    if (ui.book?.format == BookFormat.EPUB && hasSelection) {
                        IconButton(onClick = {
                            controllers[ui.page]?.readSelection { text ->
                                if (text.isBlank()) {
                                    scope.launch { snackbarHostState.showSnackbar("Select text first") }
                                } else {
                                    viewModel.addEpubHighlight(text, highlightArgb)
                                }
                            }
                        }) {
                            Icon(Icons.Default.Check, contentDescription = "Highlight selection")
                        }
                    }
                    IconButton(onClick = { showPanel = true }, enabled = ui.highlights.isNotEmpty()) {
                        Icon(Icons.Default.List, contentDescription = "Highlights")
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .pointerInput(ui.book?.format, penMode) {
                    val pdf = ui.book?.format == BookFormat.PDF
                    if (pdf && penMode) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { dragAccum[0] = 0f },
                        onDragEnd = {
                            if (dragAccum[0] > swipeThreshold) viewModel.nextPage()
                            else if (dragAccum[0] < -swipeThreshold) viewModel.previousPage()
                        }
                    ) { change, dragAmount ->
                        dragAccum[0] += dragAmount
                        if (abs(dragAccum[0]) > 8f) change.consume()
                    }
                }
        ) {
            when {
                ui.loading -> CenteredMessage(text = "Opening…", loading = true)
                ui.error != null -> CenteredMessage(text = ui.error!!)
                ui.book == null -> CenteredMessage(text = "Nothing to show.")
                ui.pageCount == 0 -> CenteredMessage(text = "This document has no readable pages.")
                else -> AnimatedContent(
                    targetState = ui.page,
                    transitionSpec = {
                        val forward = targetState > initialState
                        val spec = tween<IntOffset>(durationMillis = 280)
                        val enter = slideInHorizontally(spec) { if (forward) -it else it } + fadeIn(tween(280))
                        val exit = slideOutHorizontally(spec) { if (forward) it else -it } + fadeOut(tween(280))
                        enter togetherWith exit
                    },
                    label = "page",
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (ui.book?.format) {
                        BookFormat.PDF -> PdfPageContent(
                            page = page,
                            bitmaps = bitmaps,
                            highlights = ui.highlights.filter { it.pageNumber == page },
                            penMode = penMode,
                            color = highlightArgb,
                            ensureRendered = viewModel::ensurePageRendered,
                            onStroke = { points -> viewModel.addPenHighlight(points, highlightArgb) },
                            modifier = Modifier.fillMaxSize()
                        )

                        BookFormat.EPUB -> {
                            val controller = remember(page) { WebViewController() }
                            DisposableEffect(page) {
                                controllers[page] = controller
                                onDispose { if (controllers[page] === controller) controllers.remove(page) }
                            }
                            LaunchedEffect(controller) {
                                controller.onSelectionChanged = { active -> hasSelection = active }
                            }
                            EpubChapterContent(
                                controller = controller,
                                chapterFile = ui.epubChapters.getOrNull(page),
                                marks = ui.highlights
                                    .filter { it.pageNumber == page && it.text.isNotBlank() }
                                    .map { HighlightMark(it.text, ReadingLogic.cssColor(it.color)) },
                                fontScale = fontScale,
                                dark = dark,
                                onSwipe = { forward -> if (forward) viewModel.nextPage() else viewModel.previousPage() },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        null -> Unit
                    }
                }
            }
        }
    }

    if (showPanel) {
        ModalBottomSheet(onDismissRequest = { showPanel = false }) {
            HighlightPanel(
                highlights = ui.highlights.filter { it.pageNumber == ui.page },
                pageNumber = ui.page,
                onDelete = viewModel::deleteHighlight
            )
        }
    }

    if (showColors) {
        ModalBottomSheet(onDismissRequest = { showColors = false }) {
            ColorPicker(
                selectedRgb = highlightRgb,
                opacity = highlightOpacity,
                previewArgb = highlightArgb,
                onColorSelected = container.settings::setHighlightColor,
                onOpacityChange = container.settings::setHighlightOpacity
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
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(Color(highlight.color))
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = highlight.text.ifBlank { "Pen highlight" },
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

@Composable
private fun ColorPicker(
    selectedRgb: Int,
    opacity: Float,
    previewArgb: Int,
    onColorSelected: (Int) -> Unit,
    onOpacityChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp)
    ) {
        Text("Highlight", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        Text("Color", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HighlightColorOptions.forEach { option ->
                val selected = option.rgb == selectedRgb
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(opaqueColor(option.rgb))
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                            shape = CircleShape
                        )
                        .clickable { onColorSelected(option.rgb) },
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color(0xFF141416)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("Opacity", style = MaterialTheme.typography.labelLarge)
        Slider(
            value = opacity,
            onValueChange = onOpacityChange,
            valueRange = SettingsStore.MIN_HIGHLIGHT_OPACITY..SettingsStore.MAX_HIGHLIGHT_OPACITY
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(previewArgb)),
            contentAlignment = Alignment.Center
        ) {
            Text("Sample text", color = Color(0xFF141416))
        }
    }
}

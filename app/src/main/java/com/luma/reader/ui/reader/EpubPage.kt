package com.luma.reader.ui.reader

import android.content.Context
import android.graphics.Color as AndroidColor
import android.view.MotionEvent
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.delay
import org.json.JSONTokener

/** A single text mark to re-apply inside a chapter. */
data class HighlightMark(val text: String, val cssColor: String)

private const val SWIPE_MIN_DISTANCE_DP = 80f
private const val SWIPE_MIN_VELOCITY = 0.2f
private const val SELECTION_POLL_MS = 400L

/**
 * Owns one chapter's WebView. It loads XHTML with the reader stylesheet
 * inlined, re-applies saved highlights, scales text with WebView text zoom,
 * applies Compatibility Mode inversion, and reports the live text selection.
 */
class WebViewController {
    var webView: WebView? = null
    var pendingMarks: List<HighlightMark> = emptyList()
    private var pendingFontScale = 1f
    private var pendingCompatibility = false
    var onPageLoaded: (() -> Unit)? = null
    private var loadedKey: String? = null

    fun loadChapter(context: Context, file: File, dark: Boolean, compatibility: Boolean) {
        pendingCompatibility = compatibility
        val key = "${file.absolutePath}|$dark|$compatibility"
        if (key == loadedKey) return
        val web = webView ?: return
        loadedKey = key
        val html = buildChapterHtml(context, file, dark, compatibility)
        val baseUrl = file.parentFile?.toURI()?.toString() ?: "file:///"
        web.loadDataWithBaseURL(baseUrl, html, "text/html", "utf-8", null)
    }

    fun applyFontScale(scale: Float) {
        pendingFontScale = scale
        webView?.settings?.textZoom = (scale * 100f).toInt().coerceAtLeast(1)
    }

    fun applyCompatibility(enabled: Boolean) {
        pendingCompatibility = enabled
        webView?.evaluateJavascript("window.lumaApplyCompatibility($enabled);", null)
    }

    fun applyHighlights(marks: List<HighlightMark>) {
        pendingMarks = marks
        val json = marks.joinToString(separator = ",", prefix = "[", postfix = "]") { mark ->
            "{\"t\":${jsonString(mark.text)},\"c\":${jsonString(mark.cssColor)}}"
        }
        webView?.evaluateJavascript("window.lumaSetHighlights($json);", null)
    }

    fun readSelection(onResult: (String) -> Unit) {
        val web = webView
        if (web == null) {
            onResult("")
            return
        }
        web.evaluateJavascript("window.getSelection ? window.getSelection().toString() : '';") { value ->
            val text = runCatching { JSONTokener(value).nextValue() as? String }.getOrNull().orEmpty()
            onResult(text)
        }
    }

    fun clearSelection() {
        webView?.evaluateJavascript(
            "window.getSelection && window.getSelection().removeAllRanges();",
            null
        )
    }

    internal fun applyPendingState() {
        applyFontScale(pendingFontScale)
        applyHighlights(pendingMarks)
        applyCompatibility(pendingCompatibility)
    }
}

@Composable
fun EpubChapterContent(
    controller: WebViewController,
    chapterFile: File?,
    marks: List<HighlightMark>,
    fontScale: Float,
    dark: Boolean,
    compatibility: Boolean,
    onSwipe: (forward: Boolean) -> Unit,
    onSelectionText: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            LumaWebView(ctx).apply {
                configureForReading()
                swipeListener = onSwipe
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        controller.webView = view
                        controller.applyPendingState()
                        controller.onPageLoaded?.invoke()
                    }
                }
                controller.webView = this
            }
        },
        update = { it.swipeListener = onSwipe }
    )

    LaunchedEffect(chapterFile, dark, compatibility) {
        val file = chapterFile ?: return@LaunchedEffect
        controller.loadChapter(context, file, dark, compatibility)
    }

    // Font size is applied with WebView text zoom, so it updates without a reload.
    LaunchedEffect(fontScale) { controller.applyFontScale(fontScale) }

    LaunchedEffect(marks) { controller.applyHighlights(marks) }

    // Track the live selection so the Highlight action appears for long-press
    // selections and for Select All, and disappears when the selection clears.
    LaunchedEffect(controller, chapterFile) {
        if (chapterFile == null) return@LaunchedEffect
        while (true) {
            controller.readSelection { onSelectionText(it) }
            delay(SELECTION_POLL_MS)
        }
    }
}

/**
 * A WebView that observes horizontal swipes in onTouchEvent. It never consumes
 * and never installs an OnTouchListener, so native scrolling, text selection,
 * and the Copy / Share / Select All menu keep working.
 */
private class LumaWebView(context: Context) : WebView(context) {
    var swipeListener: ((forward: Boolean) -> Unit)? = null
    private var minDistancePx = 0f
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L

    fun configureForReading() {
        minDistancePx = SWIPE_MIN_DISTANCE_DP * resources.displayMetrics.density
        settings.javaScriptEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.defaultTextEncodingName = "utf-8"
        setBackgroundColor(AndroidColor.TRANSPARENT)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                downTime = event.eventTime
            }

            MotionEvent.ACTION_UP -> {
                val dx = event.x - downX
                val dy = event.y - downY
                val elapsed = (event.eventTime - downTime).coerceAtLeast(1L)
                val horizontal = abs(dx) > abs(dy) * 1.5f
                if (abs(dx) > minDistancePx && horizontal && abs(dx) / elapsed > SWIPE_MIN_VELOCITY) {
                    // Swiping right-to-left moves to the next page.
                    swipeListener?.invoke(dx < 0)
                }
            }
        }
        return super.onTouchEvent(event)
    }
}

private val scriptBlock = Regex("(?is)<script\\b[^>]*>.*?</script>")
private val scriptSelfClosing = Regex("(?is)<script\\b[^>]*/>")
private val eventHandlerAttribute = Regex("(?i)\\son[a-z]+\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)")

/** Strip author scripts and inline handlers so only our own script runs. */
private fun sanitizeChapterHtml(html: String): String =
    html.replace(scriptBlock, "")
        .replace(scriptSelfClosing, "")
        .replace(eventHandlerAttribute, "")

private fun buildChapterHtml(context: Context, file: File, dark: Boolean, compatibility: Boolean): String {
    val raw = runCatching { file.readText(Charsets.UTF_8) }
        .getOrElse { "<html><body><p>This chapter could not be read.</p></body></html>" }
    val sanitized = sanitizeChapterHtml(raw)
    val css = readAsset(context, "reader.css")
    val js = readAsset(context, "reader.js")

    // In Compatibility Mode the document keeps its own colours and is inverted
    // only when it is dark text on a light page. Otherwise the theme wins.
    val colourStyle = if (compatibility) {
        "html.luma-compat{filter:invert(1) hue-rotate(180deg);}"
    } else {
        val background = if (dark) "#0A0A0B" else "#FAFAF9"
        val foreground = if (dark) "#E8E8EA" else "#141416"
        "html,body{background:$background;color:$foreground;}"
    }

    val inject = buildString {
        append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=3.0\">")
        append("<style>").append(css).append("</style>")
        append("<style>html,body{font-size:18px;}").append(colourStyle).append("</style>")
        append("<script>").append(js).append("</script>")
    }
    val headEnd = Regex("</head>", RegexOption.IGNORE_CASE)
    return if (headEnd.containsMatchIn(sanitized)) {
        headEnd.replaceFirst(sanitized, inject + "</head>")
    } else {
        inject + sanitized
    }
}

private fun readAsset(context: Context, name: String): String =
    runCatching { context.assets.open(name).bufferedReader().use { it.readText() } }.getOrDefault("")

private fun jsonString(value: String): String {
    val builder = StringBuilder("\"")
    for (c in value) {
        when (c) {
            '"' -> builder.append("\\\"")
            '\\' -> builder.append("\\\\")
            '\n' -> builder.append("\\n")
            '\r' -> builder.append("\\r")
            '\t' -> builder.append("\\t")
            else -> if (c < ' ') builder.append(String.format("\\u%04x", c.code)) else builder.append(c)
        }
    }
    return builder.append('"').toString()
}

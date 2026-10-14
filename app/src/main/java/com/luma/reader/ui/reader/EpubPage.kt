package com.luma.reader.ui.reader

import android.content.Context
import android.graphics.Color as AndroidColor
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
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
import org.json.JSONTokener

/** A single text mark to re-apply inside a chapter. */
data class HighlightMark(val text: String, val cssColor: String)

private const val SWIPE_MIN_DISTANCE_DP = 80f
private const val SWIPE_MIN_VELOCITY = 0.2f

/**
 * Owns one chapter's WebView: loads XHTML with the reader stylesheet inlined,
 * re-applies saved highlights, reports selection state to Compose, and turns a
 * horizontal swipe into a page change without stealing touches from the WebView.
 */
class WebViewController {
    var webView: WebView? = null
    var pendingMarks: List<HighlightMark> = emptyList()
    var onPageLoaded: (() -> Unit)? = null
    var onSelectionChanged: ((Boolean) -> Unit)? = null
    private var loadedKey: String? = null

    fun loadChapter(context: Context, file: File, fontScale: Float, dark: Boolean) {
        val key = "${file.absolutePath}|$fontScale|$dark"
        if (key == loadedKey) return
        val web = webView ?: return
        loadedKey = key
        val html = buildChapterHtml(context, file, fontScale, dark)
        val baseUrl = file.parentFile?.toURI()?.toString() ?: "file:///"
        web.loadDataWithBaseURL(baseUrl, html, "text/html", "utf-8", null)
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
}

@Composable
fun EpubChapterContent(
    controller: WebViewController,
    chapterFile: File?,
    marks: List<HighlightMark>,
    fontScale: Float,
    dark: Boolean,
    onSwipe: (forward: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            LumaWebView(ctx).apply {
                configureForReading()
                selectionListener = { active -> controller.onSelectionChanged?.invoke(active) }

                val minDistancePx = SWIPE_MIN_DISTANCE_DP * ctx.resources.displayMetrics.density
                var downX = 0f
                var downY = 0f
                var downTime = 0L
                setOnTouchListener { _, event ->
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
                                onSwipe(dx > 0)
                            }
                        }
                    }
                    // Observe only: returning false keeps scrolling and selection intact.
                    false
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        controller.webView = view
                        controller.applyHighlights(controller.pendingMarks)
                        controller.onPageLoaded?.invoke()
                    }
                }
                controller.webView = this
            }
        }
    )

    LaunchedEffect(chapterFile, fontScale, dark) {
        val file = chapterFile ?: return@LaunchedEffect
        controller.loadChapter(context, file, fontScale, dark)
    }

    LaunchedEffect(marks) { controller.applyHighlights(marks) }
}

/** WebView that reports when the text-selection action mode starts and ends. */
private class LumaWebView(context: Context) : WebView(context) {
    var selectionListener: ((Boolean) -> Unit)? = null

    fun configureForReading() {
        settings.javaScriptEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.defaultTextEncodingName = "utf-8"
        setBackgroundColor(AndroidColor.TRANSPARENT)
    }

    override fun startActionMode(callback: ActionMode.Callback?, type: Int): ActionMode? {
        if (callback != null) selectionListener?.invoke(true)
        return super.startActionMode(callback?.let { wrap(it) }, type)
    }

    @Deprecated("Deprecated in Java")
    override fun startActionMode(callback: ActionMode.Callback?): ActionMode? {
        if (callback != null) selectionListener?.invoke(true)
        return super.startActionMode(callback?.let { wrap(it) })
    }

    private fun wrap(callback: ActionMode.Callback) = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean =
            callback.onCreateActionMode(mode, menu)

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean =
            callback.onPrepareActionMode(mode, menu)

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean =
            callback.onActionItemClicked(mode, item)

        override fun onDestroyActionMode(mode: ActionMode) {
            callback.onDestroyActionMode(mode)
            selectionListener?.invoke(false)
        }
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

private fun buildChapterHtml(context: Context, file: File, fontScale: Float, dark: Boolean): String {
    val raw = runCatching { file.readText(Charsets.UTF_8) }
        .getOrElse { "<html><body><p>This chapter could not be read.</p></body></html>" }
    val sanitized = sanitizeChapterHtml(raw)
    val css = readAsset(context, "reader.css")
    val js = readAsset(context, "reader.js")
    val background = if (dark) "#0A0A0B" else "#FAFAF9"
    val foreground = if (dark) "#E8E8EA" else "#141416"
    val fontSize = (18 * fontScale).toInt()
    val inject = buildString {
        append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=3.0\">")
        append("<style>").append(css).append("</style>")
        append("<style>html,body{background:").append(background)
            .append(";color:").append(foreground).append(";font-size:").append(fontSize).append("px;}</style>")
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

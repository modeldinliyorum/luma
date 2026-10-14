package com.luma.reader.ui.reader

import android.content.Context
import android.graphics.Color as AndroidColor
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File
import org.json.JSONTokener

/**
 * Owns the WebView used for EPUB chapters: loads chapter XHTML with the reader
 * stylesheet inlined, re-applies saved highlights after each load, and reports
 * the current text selection back to Compose.
 */
class WebViewController {
    var webView: WebView? = null
    var pendingHighlights: List<String> = emptyList()
    var onPageLoaded: (() -> Unit)? = null
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

    fun applyHighlights(texts: List<String>) {
        pendingHighlights = texts
        val json = texts.joinToString(separator = ",", prefix = "[", postfix = "]") { jsonString(it) }
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
fun EpubWebView(controller: WebViewController, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.defaultTextEncodingName = "utf-8"
                setBackgroundColor(AndroidColor.TRANSPARENT)
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        controller.webView = view
                        controller.applyHighlights(controller.pendingHighlights)
                        controller.onPageLoaded?.invoke()
                    }
                }
                controller.webView = this
            }
        }
    )
}

private fun buildChapterHtml(context: Context, file: File, fontScale: Float, dark: Boolean): String {
    val raw = runCatching { file.readText(Charsets.UTF_8) }
        .getOrElse { "<html><body><p>This chapter could not be read.</p></body></html>" }
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
    return if (headEnd.containsMatchIn(raw)) {
        headEnd.replaceFirst(raw, inject + "</head>")
    } else {
        inject + raw
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

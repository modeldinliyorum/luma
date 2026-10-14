package com.luma.reader.epub

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

data class EpubChapter(
    val index: Int,
    val title: String,
    val file: File
)

data class EpubBook(
    val title: String,
    val chapters: List<EpubChapter>,
    val rootDir: File
)

/**
 * Minimal EPUB reader: unzip into a private directory, resolve the OPF through
 * the container, and return the spine chapters in order. No third-party parser.
 */
object EpubParser {

    fun parse(epubFile: File, extractDir: File): EpubBook {
        if (!extractDir.isDirectory || extractDir.list().isNullOrEmpty()) {
            extract(epubFile, extractDir)
        }

        val container = File(extractDir, "META-INF/container.xml")
        val opfRelative = readOpfPath(container)
            ?: error("Not a valid EPUB: META-INF/container.xml has no rootfile")
        val opfFile = File(extractDir, opfRelative)
        val opfDir = opfFile.parentFile ?: extractDir

        val opf = parseXml(opfFile)
        val title = firstText(opf, "title") ?: epubFile.nameWithoutExtension

        val manifest = mutableMapOf<String, String>()
        val items = opf.getElementsByTagNameNS("*", "item")
        for (i in 0 until items.length) {
            val item = items.item(i) as Element
            val id = item.getAttribute("id")
            val href = item.getAttribute("href")
            if (id.isNotEmpty() && href.isNotEmpty()) manifest[id] = href
        }

        val chapters = mutableListOf<EpubChapter>()
        val spine = opf.getElementsByTagNameNS("*", "itemref")
        for (i in 0 until spine.length) {
            val idref = (spine.item(i) as Element).getAttribute("idref")
            val href = manifest[idref] ?: continue
            val file = File(opfDir, href.substringBefore('#')).canonicalFile
            if (file.isFile) {
                chapters += EpubChapter(chapters.size, "Chapter ${chapters.size + 1}", file)
            }
        }
        require(chapters.isNotEmpty()) { "EPUB spine is empty" }
        return EpubBook(title, chapters, extractDir)
    }

    private fun readOpfPath(container: File): String? {
        if (!container.isFile) return null
        val doc = parseXml(container)
        val rootfiles = doc.getElementsByTagNameNS("*", "rootfile")
        if (rootfiles.length == 0) return null
        val fullPath = (rootfiles.item(0) as Element).getAttribute("full-path")
        return fullPath.ifEmpty { null }
    }

    private fun firstText(doc: Document, localName: String): String? {
        val nodes = doc.getElementsByTagNameNS("*", localName)
        if (nodes.length == 0) return null
        return nodes.item(0).textContent?.trim()?.ifEmpty { null }
    }

    private fun parseXml(file: File): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        factory.setExpandEntityReferences(false)
        // Hardening against XXE from untrusted EPUB XML.
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        return factory.newDocumentBuilder().parse(file)
    }

    private fun extract(epubFile: File, dir: File) {
        dir.mkdirs()
        val root = dir.canonicalPath
        ZipFile(epubFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val out = File(dir, entry.name)
                // Zip-slip guard: never write outside the extract directory.
                if (!out.canonicalPath.startsWith(root + File.separator)) continue
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        out.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
        }
    }
}

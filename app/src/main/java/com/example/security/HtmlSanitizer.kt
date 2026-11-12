package com.example.security

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.safety.Safelist

object HtmlSanitizer {

    private val editorSafelist: Safelist = Safelist.relaxed()
        .addTags(
            "h1", "h2", "h3", "h4", "h5", "h6",
            "p", "br", "hr", "blockquote", "pre", "code",
            "b", "strong", "i", "em", "u", "s", "strike", "del", "sup", "sub",
            "ul", "ol", "li",
            "table", "thead", "tbody", "tfoot", "tr", "th", "td",
            "div", "span", "a", "img", "mark",
            "svg", "rect", "circle", "line", "polygon", "path"
        )
        .addAttributes(
            ":all", "style", "class", "dir", "align", "id",
            "contenteditable", "spellcheck",
            "data-smartart-id", "data-smartart-json", "data-chart-id", "data-chart-json", "data-anchor-id",
            "data-field", "data-date-format", "data-format",
            "data-remo-img", "data-remo-table-id", "data-header-repeat", "data-remo-id",
            "data-shape-id", "data-shape-json", "data-group-id", "data-group-json",
            "data-pdf-fallback", "data-comment-id"
        )
        .addAttributes("a", "href", "target", "rel")
        .addAttributes("img", "src", "alt", "width", "height")
        .addAttributes("td", "colspan", "rowspan")
        .addAttributes("th", "colspan", "rowspan")
        .addAttributes("mark", "data-comment-id")
        .addAttributes("svg", "width", "height", "viewBox")
        .addAttributes("rect", "x", "y", "width", "height", "rx", "ry", "fill", "stroke", "stroke-width")
        .addAttributes("circle", "cx", "cy", "r", "fill", "stroke", "stroke-width")
        .addAttributes("line", "x1", "y1", "x2", "y2", "stroke", "stroke-width")
        .addAttributes("polygon", "points", "fill", "stroke", "stroke-width")
        .addAttributes("path", "d", "fill", "stroke", "stroke-width")
        .addProtocols("a", "href", "http", "https", "mailto")
        .addProtocols("img", "src", "http", "https", "data")

    fun sanitizeHtml(rawHtml: String): String {
        if (rawHtml.isBlank()) return "<p><br></p>"
        val outputSettings = Document.OutputSettings().prettyPrint(false)
        val cleaned = Jsoup.clean(rawHtml, "", editorSafelist, outputSettings)
        return cleaned.ifBlank { "<p><br></p>" }
    }

    fun isValidSafeUrl(url: String): Boolean {
        val trimmed = url.trim().lowercase()
        return trimmed.startsWith("https://") || trimmed.startsWith("http://")
    }

    fun sanitizeFileName(name: String): String {
        val cleaned = name
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace("..", "_")
            .trim()
        return cleaned.ifBlank { "Document" }
    }
}

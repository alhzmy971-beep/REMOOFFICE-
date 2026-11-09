package com.example.bridge

import android.util.Base64
import com.example.model.DocumentPayload
import com.example.model.MarginPreset
import com.example.model.PageConfig
import com.example.model.PageOrientation
import com.example.security.HtmlSanitizer
import org.apache.poi.common.usermodel.PictureType
import org.apache.poi.util.Units
import org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy
import org.apache.poi.xwpf.usermodel.BodyElementType
import org.apache.poi.xwpf.usermodel.BreakType
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.UnderlinePatterns
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFRun
import org.apache.poi.xwpf.usermodel.XWPFTable
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STPageOrientation
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.math.BigInteger

object DocxConverter {

    private fun mmToTwips(mm: Float): BigInteger {
        return BigInteger.valueOf((mm * 56.692913f).toLong())
    }

    fun htmlToDocx(payload: DocumentPayload, outputStream: OutputStream) {
        val document = XWPFDocument()
        configurePageLayout(document, payload.pageConfig)

        if (payload.headerHtml.isNotBlank() || payload.footerHtml.isNotBlank()) {
            val policy = XWPFHeaderFooterPolicy(document)
            if (payload.headerHtml.isNotBlank()) {
                val header = policy.createHeader(XWPFHeaderFooterPolicy.DEFAULT)
                val hp = header.createParagraph()
                hp.createRun().setText(Jsoup.parse(payload.headerHtml).text())
            }
            if (payload.footerHtml.isNotBlank()) {
                val footer = policy.createFooter(XWPFHeaderFooterPolicy.DEFAULT)
                val fp = footer.createParagraph()
                fp.createRun().setText(Jsoup.parse(payload.footerHtml).text())
            }
        }

        val sanitized = HtmlSanitizer.sanitizeHtml(payload.bodyHtml)
        val doc = Jsoup.parseBodyFragment(sanitized)
        val body = doc.body()

        for (child in body.children()) {
            processBlockElement(document, child, payload.pageConfig)
        }

        if (document.paragraphs.isEmpty() && document.tables.isEmpty()) {
            document.createParagraph()
        }

        document.write(outputStream)
        document.close()
    }

    private fun configurePageLayout(document: XWPFDocument, config: PageConfig) {
        val sectPr = document.document.body.addNewSectPr()
        val pageSize = sectPr.addNewPgSz()
        val wMm = if (config.orientation == PageOrientation.LANDSCAPE) config.size.heightMm else config.size.widthMm
        val hMm = if (config.orientation == PageOrientation.LANDSCAPE) config.size.widthMm else config.size.heightMm
        pageSize.w = mmToTwips(wMm.toFloat())
        pageSize.h = mmToTwips(hMm.toFloat())
        pageSize.orient = if (config.orientation == PageOrientation.LANDSCAPE) {
            STPageOrientation.LANDSCAPE
        } else {
            STPageOrientation.PORTRAIT
        }

        val marginMm = if (config.marginPreset == MarginPreset.CUSTOM) {
            config.customMarginMm
        } else {
            config.marginPreset.mm
        }
        val marginTwips = mmToTwips(marginMm)
        val pageMar = sectPr.addNewPgMar()
        pageMar.top = marginTwips
        pageMar.bottom = marginTwips
        pageMar.left = marginTwips
        pageMar.right = marginTwips

        if (config.columns > 1) {
            val cols = sectPr.addNewCols()
            cols.num = BigInteger.valueOf(config.columns.toLong())
        }
    }

    private fun processBlockElement(document: XWPFDocument, element: Element, pageConfig: PageConfig) {
        val tag = element.tagName().lowercase()
        if (element.hasClass("page-break")) {
            val p = document.createParagraph()
            p.createRun().addBreak(BreakType.PAGE)
            return
        }

        if (element.hasClass("pdf-page")) {
            processPdfPage(document, element, pageConfig)
            return
        }

        when (tag) {
            "table" -> {
                val rows = element.select("tr")
                if (rows.isNotEmpty()) {
                    val maxCols = rows.maxOfOrNull { it.select("td, th").size } ?: 1
                    val table = document.createTable(rows.size, maxCols.coerceAtLeast(1))
                    rows.forEachIndexed { rIdx, tr ->
                        val cells = tr.select("td, th")
                        val row = table.getRow(rIdx)
                        cells.forEachIndexed { cIdx, td ->
                            if (cIdx < row.tableCells.size) {
                                val cell = row.getCell(cIdx)
                                cell.text = td.text()
                            }
                        }
                    }
                }
            }
            "ul", "ol" -> {
                val items = element.select("> li")
                items.forEachIndexed { index, li ->
                    val p = document.createParagraph()
                    val prefix = if (tag == "ol") "${index + 1}. " else "• "
                    val run = p.createRun()
                    run.setText(prefix)
                    processInlineNodes(p, li.childNodes(), InlineStyle())
                }
            }
            else -> {
                val p = document.createParagraph()
                applyParagraphAlignment(p, element)
                processInlineNodes(p, element.childNodes(), InlineStyle())
            }
        }
    }

    private fun processPdfPage(document: XWPFDocument, page: Element, pageConfig: PageConfig) {
        data class PageItem(
            val element: Element?,
            val topPt: Float,
            val leftPt: Float,
            val widthPt: Float,
            val heightPt: Float,
            val svgBytes: ByteArray? = null
        )

        val positioned = mutableListOf<PageItem>()
        page.children().filter {
            it.hasClass("pdf-text-line") || it.hasClass("pdf-import-image") || it.hasClass("pdf-page-image-fallback")
        }.forEach { element ->
            val style = element.attr("style")
            val widthPt = cssPoint(style, "width") ?: if (element.hasClass("pdf-page-image-fallback")) pageWidthPt(pageConfig) else 0f
            val heightPt = cssPoint(style, "height") ?: if (element.hasClass("pdf-page-image-fallback")) pageHeightPt(pageConfig) else 0f
            positioned.add(
                PageItem(
                    element = element,
                    topPt = cssPoint(style, "top") ?: 0f,
                    leftPt = cssPoint(style, "left") ?: 0f,
                    widthPt = widthPt,
                    heightPt = heightPt
                )
            )
        }

        val svgLayer = page.children().firstOrNull { it.tagName().equals("svg", ignoreCase = true) }
        if (svgLayer != null) {
            val sourceWidth = svgLayer.attr("width").toFloatOrNull() ?: 0f
            val scale = if (sourceWidth > 0f) pageWidthPt(pageConfig) / sourceWidth else 1f
            for (path in svgLayer.children().filter { it.tagName().equals("path", ignoreCase = true) }) {
                val d = path.attr("d")
                val bounds = svgPathBounds(d) ?: continue
                val strokeWidth = path.attr("stroke-width").toFloatOrNull() ?: 0f
                val pad = maxOf(0.5f, strokeWidth / 2f)
                val left = bounds[0] - pad
                val top = bounds[1] - pad
                val width = maxOf(1f, bounds[2] - bounds[0] + pad * 2f)
                val height = maxOf(1f, bounds[3] - bounds[1] + pad * 2f)
                val svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"${width}\" height=\"${height}\" viewBox=\"${left} ${top} ${width} ${height}\"><path d=\"${xmlEscape(d)}\" fill=\"${xmlEscape(path.attr("fill"))}\" stroke=\"${xmlEscape(path.attr("stroke"))}\" stroke-width=\"${strokeWidth}\" /></svg>"
                positioned.add(
                    PageItem(
                        element = null,
                        topPt = top * scale,
                        leftPt = left * scale,
                        widthPt = width * scale,
                        heightPt = height * scale,
                        svgBytes = svg.toByteArray(Charsets.UTF_8)
                    )
                )
            }
        }

        val ordered = positioned.sortedBy { it.topPt }
        var cursorPt = 0f

        for (item in ordered) {
            val element = item.element
            val style = element?.attr("style").orEmpty()
            val topPt = item.topPt
            val leftPt = item.leftPt
            val gapPt = (topPt - cursorPt).coerceAtLeast(0f)
            val p = document.createParagraph()
            p.spacingBefore = (gapPt * 20f).toInt()
            p.spacingAfter = 0
            p.indentationLeft = (leftPt * 20f).toInt()

            when {
                item.svgBytes != null -> {
                    p.createRun().addPicture(
                        ByteArrayInputStream(item.svgBytes), PictureType.SVG, "pdf-vector.svg",
                        Units.toEMU(item.widthPt.toDouble()), Units.toEMU(item.heightPt.toDouble())
                    )
                    cursorPt = maxOf(cursorPt, topPt + item.heightPt)
                }
                element?.hasClass("pdf-text-line") == true -> {
                if (style.contains("direction:rtl")) p.alignment = ParagraphAlignment.RIGHT
                val inlineStyle = parseInlineCss(style, InlineStyle())
                processInlineNodes(p, element.childNodes(), inlineStyle)
                val lineHeight = cssPoint(style, "min-height") ?: inlineStyle.fontSizePt?.toFloat() ?: 12f
                cursorPt = maxOf(cursorPt, topPt + lineHeight)
                }
                element != null -> {
                val source = element.attr("src")
                insertPositionedPdfImage(p, source, item.widthPt.takeIf { it > 0f } ?: pageWidthPt(pageConfig),
                    item.heightPt.takeIf { it > 0f } ?: pageHeightPt(pageConfig))
                cursorPt = maxOf(cursorPt, topPt + item.heightPt)
                }
            }
        }
    }

    private fun svgPathBounds(d: String): FloatArray? {
        val tokens = Regex("[MLCZ]|[-+]?(?:[0-9]*\\.)?[0-9]+(?:[eE][-+]?[0-9]+)?").findAll(d)
            .map { it.value }.toList()
        val points = mutableListOf<Pair<Float, Float>>()
        var command = ' '
        var index = 0
        while (index < tokens.size) {
            val token = tokens[index]
            if (token.length == 1 && token[0].isLetter()) {
                command = token[0].uppercaseChar()
                index++
                if (command == 'Z') continue
            }
            val count = when (command) { 'M', 'L' -> 2; 'C' -> 6; else -> return null }
            if (index + count > tokens.size) return null
            val values = (0 until count).map { tokens[index + it].toFloatOrNull() ?: return null }
            for (pair in values.indices step 2) points.add(values[pair] to values[pair + 1])
            index += count
        }
        if (points.isEmpty()) return null
        return floatArrayOf(
            points.minOf { it.first }, points.minOf { it.second },
            points.maxOf { it.first }, points.maxOf { it.second }
        )
    }

    private fun xmlEscape(value: String): String = value
        .replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")

    private fun cssPoint(style: String, property: String): Float? {
        val match = Regex("(?:^|;)\\s*${Regex.escape(property)}\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?)\\s*pt", RegexOption.IGNORE_CASE)
            .find(style) ?: return null
        return match.groupValues[1].toFloatOrNull()
    }

    private fun pageWidthPt(config: PageConfig): Float {
        val widthMm = if (config.orientation == PageOrientation.LANDSCAPE) config.size.heightMm else config.size.widthMm
        return widthMm * 72f / 25.4f
    }

    private fun pageHeightPt(config: PageConfig): Float {
        val heightMm = if (config.orientation == PageOrientation.LANDSCAPE) config.size.widthMm else config.size.heightMm
        return heightMm * 72f / 25.4f
    }

    private fun insertPositionedPdfImage(p: XWPFParagraph, src: String, widthPt: Float, heightPt: Float) {
        if (!src.startsWith("data:image/")) return
        val base64Part = src.substringAfter("base64,", "")
        if (base64Part.isEmpty()) return
        val bytes = Base64.decode(base64Part, Base64.DEFAULT)
        val type = when {
            src.startsWith("data:image/png") -> XWPFDocument.PICTURE_TYPE_PNG
            src.startsWith("data:image/gif") -> XWPFDocument.PICTURE_TYPE_GIF
            src.startsWith("data:image/bmp") -> XWPFDocument.PICTURE_TYPE_BMP
            else -> XWPFDocument.PICTURE_TYPE_JPEG
        }
        p.createRun().addPicture(
            ByteArrayInputStream(bytes), type, "pdf-image.png",
            Units.toEMU(widthPt.toDouble()), Units.toEMU(heightPt.toDouble())
        )
    }

    private fun applyParagraphAlignment(p: XWPFParagraph, element: Element) {
        val style = element.attr("style").lowercase()
        val alignAttr = element.attr("align").lowercase()
        when {
            style.contains("text-align: center") || alignAttr == "center" ->
                p.alignment = ParagraphAlignment.CENTER
            style.contains("text-align: right") || alignAttr == "right" ->
                p.alignment = ParagraphAlignment.RIGHT
            style.contains("text-align: justify") || alignAttr == "justify" ->
                p.alignment = ParagraphAlignment.BOTH
            else -> p.alignment = ParagraphAlignment.LEFT
        }
    }

    private data class InlineStyle(
        val bold: Boolean = false,
        val italic: Boolean = false,
        val underline: Boolean = false,
        val strike: Boolean = false,
        val colorHex: String? = null,
        val fontFamily: String? = null,
        val fontSizePt: Int? = null
    )

    private fun processInlineNodes(p: XWPFParagraph, nodes: List<Node>, currentStyle: InlineStyle) {
        for (node in nodes) {
            if (node is TextNode) {
                val text = node.wholeText
                if (text.isNotEmpty()) {
                    val run = p.createRun()
                    applyRunStyle(run, currentStyle)
                    run.setText(text)
                }
            } else if (node is Element) {
                val tag = node.tagName().lowercase()
                if (tag == "br") {
                    p.createRun().addBreak()
                    continue
                }
                if (tag == "img") {
                    insertBase64Image(p, node.attr("src"))
                    continue
                }
                var nextStyle = currentStyle
                when (tag) {
                    "b", "strong" -> nextStyle = nextStyle.copy(bold = true)
                    "i", "em" -> nextStyle = nextStyle.copy(italic = true)
                    "u" -> nextStyle = nextStyle.copy(underline = true)
                    "s", "strike", "del" -> nextStyle = nextStyle.copy(strike = true)
                }
                val styleAttr = node.attr("style")
                if (styleAttr.isNotEmpty()) {
                    nextStyle = parseInlineCss(styleAttr, nextStyle)
                }
                if (tag == "font") {
                    val color = node.attr("color")
                    if (color.isNotEmpty()) {
                        nextStyle = nextStyle.copy(colorHex = cleanHex(color))
                    }
                    val face = node.attr("face")
                    if (face.isNotEmpty()) {
                        nextStyle = nextStyle.copy(fontFamily = face)
                    }
                }
                processInlineNodes(p, node.childNodes(), nextStyle)
            }
        }
    }

    private fun parseInlineCss(css: String, base: InlineStyle): InlineStyle {
        var result = base
        val declarations = css.split(";")
        for (decl in declarations) {
            val parts = decl.split(":", limit = 2)
            if (parts.size != 2) continue
            val prop = parts[0].trim().lowercase()
            val value = parts[1].trim()
            when (prop) {
                "font-weight" -> if (value.contains("bold") || (value.toIntOrNull() ?: 0) >= 600) {
                    result = result.copy(bold = true)
                }
                "font-style" -> if (value.contains("italic")) {
                    result = result.copy(italic = true)
                }
                "text-decoration" -> {
                    if (value.contains("underline")) result = result.copy(underline = true)
                    if (value.contains("line-through")) result = result.copy(strike = true)
                }
                "color" -> {
                    val hex = cleanHex(value)
                    if (hex != null) result = result.copy(colorHex = hex)
                }
                "font-family" -> {
                    result = result.copy(fontFamily = value.replace("'", "").replace("\"", "").split(",").first().trim())
                }
                "font-size" -> {
                    val pt = value.replace("pt", "").replace("px", "").trim().toFloatOrNull()?.toInt()
                    if (pt != null && pt > 0) result = result.copy(fontSizePt = pt)
                }
            }
        }
        return result
    }

    private fun cleanHex(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.startsWith("#") && (trimmed.length == 7 || trimmed.length == 4)) {
            return trimmed.removePrefix("#").uppercase()
        }
        return null
    }

    private fun applyRunStyle(run: XWPFRun, style: InlineStyle) {
        run.isBold = style.bold
        run.isItalic = style.italic
        if (style.underline) run.underline = UnderlinePatterns.SINGLE
        run.isStrikeThrough = style.strike
        style.colorHex?.let { run.color = it }
        style.fontFamily?.let { run.fontFamily = it }
        style.fontSizePt?.let { run.fontSize = it }
    }

    private fun insertBase64Image(p: XWPFParagraph, src: String) {
        if (!src.startsWith("data:image/")) return
        try {
            val base64Part = src.substringAfter("base64,", "")
            if (base64Part.isEmpty()) return
            val bytes = Base64.decode(base64Part, Base64.DEFAULT)
            val picType = when {
                src.contains("image/png") -> XWPFDocument.PICTURE_TYPE_PNG
                src.contains("image/gif") -> XWPFDocument.PICTURE_TYPE_GIF
                else -> XWPFDocument.PICTURE_TYPE_JPEG
            }
            p.createRun().addPicture(
                ByteArrayInputStream(bytes),
                picType,
                "image.png",
                Units.toEMU(320.0),
                Units.toEMU(220.0)
            )
        } catch (_: Exception) {
        }
    }

    private fun escapeHtml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun paragraphAlignment(p: XWPFParagraph): String = when (p.alignment) {
        ParagraphAlignment.CENTER -> "center"
        ParagraphAlignment.RIGHT -> "right"
        ParagraphAlignment.BOTH -> "justify"
        else -> "left"
    }

    private fun runHtml(run: XWPFRun): String {
        val styles = mutableListOf<String>()
        run.color?.let { styles.add("color: #$it") }
        run.fontFamily?.let { styles.add("font-family: '${escapeHtml(it)}'") }
        if ((run.fontSizeAsDouble ?: 0.0) > 0) styles.add("font-size: ${run.fontSizeAsDouble.toInt()}pt")
        var text = escapeHtml(run.text() ?: "").replace("\n", "<br>").replace("\t", "&emsp;")
        if (text.isEmpty()) text = ""
        var wrapped = if (styles.isEmpty()) text else "<span style=\"${styles.joinToString("; ")}\">$text</span>"
        if (run.isBold) wrapped = "<b>$wrapped</b>"
        if (run.isItalic) wrapped = "<i>$wrapped</i>"
        if (run.underline != UnderlinePatterns.NONE) wrapped = "<u>$wrapped</u>"
        if (run.isStrikeThrough) wrapped = "<s>$wrapped</s>"
        return wrapped
    }

    private fun pictureHtml(run: XWPFRun): String {
        val out = StringBuilder()
        for (picture in run.embeddedPictures) {
            try {
                val data = picture.pictureData.data
                val mime = when (picture.pictureData.pictureType) {
                    XWPFDocument.PICTURE_TYPE_PNG -> "image/png"
                    XWPFDocument.PICTURE_TYPE_GIF -> "image/gif"
                    XWPFDocument.PICTURE_TYPE_BMP -> "image/bmp"
                    else -> "image/jpeg"
                }
                val encoded = Base64.encodeToString(data, Base64.NO_WRAP)
                out.append("<p><img src=\"data:$mime;base64,$encoded\" style=\"max-width:100%;\" /></p>")
            } catch (_: Exception) { }
        }
        return out.toString()
    }

    private fun paragraphHtml(p: XWPFParagraph): String {
        val body = StringBuilder()
        for (run in p.runs) {
            body.append(runHtml(run)).append(pictureHtml(run))
        }
        if (body.isEmpty()) body.append("<br>")
        return "<p style=\"text-align: ${paragraphAlignment(p)};\">$body</p>"
    }

    private fun tableHtml(table: XWPFTable): String {
        val sb = StringBuilder("<table><tbody>")
        for (row in table.rows) {
            sb.append("<tr>")
            for (cell in row.tableCells) {
                val content = StringBuilder()
                for (paragraph in cell.paragraphs) content.append(paragraphHtml(paragraph))
                for (nested in cell.tables) content.append(tableHtml(nested))
                sb.append("<td>").append(if (content.isEmpty()) "<br>" else content).append("</td>")
            }
            sb.append("</tr>")
        }
        return sb.append("</tbody></table>").toString()
    }

    fun docxToPayload(inputStream: InputStream): DocumentPayload {
        val document = XWPFDocument(inputStream)
        val sb = StringBuilder()
        val headerHtml = document.headerList.flatMap { it.paragraphs }.joinToString("") { paragraphHtml(it) }
        val footerHtml = document.footerList.flatMap { it.paragraphs }.joinToString("") { paragraphHtml(it) }
        for (element in document.bodyElements) {
            when (element.elementType) {
                BodyElementType.PARAGRAPH -> sb.append(paragraphHtml(element as XWPFParagraph))
                BodyElementType.TABLE -> sb.append(tableHtml(element as XWPFTable))
                else -> { }
            }
        }
        document.close()
        return DocumentPayload(
            bodyHtml = HtmlSanitizer.sanitizeHtml(sb.toString().ifBlank { "<p><br></p>" }),
            headerHtml = HtmlSanitizer.sanitizeHtml(headerHtml),
            footerHtml = HtmlSanitizer.sanitizeHtml(footerHtml)
        )
    }

    fun docToPayload(inputStream: InputStream): DocumentPayload {
        val document = org.apache.poi.hwpf.HWPFDocument(inputStream)
        val extractor = org.apache.poi.hwpf.extractor.WordExtractor(document)
        val sb = StringBuilder()
        extractor.paragraphText.forEach { paragraph ->
            val escaped = escapeHtml(paragraph.trimEnd('\r', '\n'))
            sb.append("<p>").append(if (escaped.isBlank()) "<br>" else escaped.replace("\t", "&emsp;")).append("</p>")
        }
        extractor.close(); document.close()
        return DocumentPayload(bodyHtml = HtmlSanitizer.sanitizeHtml(sb.toString().ifBlank { "<p><br></p>" }))
    }

    fun htmlToPayload(inputStream: InputStream): DocumentPayload {
        val html = inputStream.bufferedReader(Charsets.UTF_8).readText()
        val body = Jsoup.parse(html).body().html()
        return DocumentPayload(bodyHtml = HtmlSanitizer.sanitizeHtml(body.ifBlank { "<p><br></p>" }))
    }

    private fun plainTextToHtml(text: String): String {
        val sb = StringBuilder()
        text.split("\r\n", "\n", "\r").forEach { line ->
            val escaped = escapeHtml(line)
            sb.append("<p>${escaped.ifEmpty { "<br>" }}</p>")
        }
        return sb.toString().ifBlank { "<p><br></p>" }
    }

    fun rtfToHtml(inputStream: InputStream): String {
        val raw = inputStream.bufferedReader(Charsets.ISO_8859_1).readText()
        val text = raw.replace(Regex("\\\\'[0-9a-fA-F]{2}"), "")
            .replace(Regex("\\\\[a-zA-Z]+-?\\d* ?"), "")
            .replace(Regex("[{}]"), "")
        return plainTextToHtml(text)
    }

    fun txtToHtml(inputStream: InputStream): String = plainTextToHtml(
        inputStream.bufferedReader(Charsets.UTF_8).readText()
    )
}

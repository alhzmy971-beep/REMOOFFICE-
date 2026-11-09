package com.example.bridge

import android.util.Base64
import com.example.model.DocumentAnchor
import com.example.model.DocumentImportReport
import com.example.model.DocumentNote
import com.example.model.DocumentPayload
import com.example.model.MarginPreset
import com.example.model.PageConfig
import com.example.model.PageOrientation
import com.example.security.HtmlSanitizer
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
import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.hwpf.extractor.WordExtractor
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
            processBlockElement(document, child)
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

    private fun processBlockElement(document: XWPFDocument, element: Element) {
        val tag = element.tagName().lowercase()
        if (element.hasClass("page-break")) {
            val p = document.createParagraph()
            p.createRun().addBreak(BreakType.PAGE)
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
            "h1", "h2", "h3", "h4", "h5", "h6" -> {
                val p = document.createParagraph()
                applyParagraphAlignment(p, element)
                val fontSize = when (tag) {
                    "h1" -> 22
                    "h2" -> 18
                    "h3" -> 16
                    else -> 14
                }
                processInlineNodes(p, element.childNodes(), InlineStyle(bold = true, fontSizePt = fontSize))
            }
            else -> {
                val p = document.createParagraph()
                applyParagraphAlignment(p, element)
                processInlineNodes(p, element.childNodes(), InlineStyle())
            }
        }
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

    fun loadDocument(inputStream: InputStream, extension: String): DocumentPayload {
        return if (extension.lowercase() == "doc" || extension.lowercase() == "dot") {
            hwpfToPayload(inputStream)
        } else {
            docxToPayload(inputStream)
        }
    }

    private fun hwpfToPayload(inputStream: InputStream): DocumentPayload {
        val document = org.apache.poi.hwpf.HWPFDocument(inputStream)
        val extractor = org.apache.poi.hwpf.extractor.WordExtractor(document)
        val bodyHtml = extractor.paragraphText.joinToString("") { "<p>${it.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").ifBlank { "<br>" }}</p>" }
        
        document.close()
        return DocumentPayload(
            bodyHtml = bodyHtml,
            importReport = DocumentImportReport(unsupportedReasons = listOf("تم استيراد ملف DOC قديم بنمط النص فقط لتعذر التحويل المباشر عالي الدقة."))
        )
    }

    fun docxToPayload(inputStream: InputStream): DocumentPayload {
        val document = XWPFDocument(inputStream)
        val sb = StringBuilder()

        var paragraphCount = 0
        var tableCount = 0
        var imageCount = 0
        var smartArtCount = 0
        var chartCount = 0
        var fieldCount = 0

        val headerHtml = document.headerList.firstOrNull()?.text?.trim()?.let {
            if (it.isNotEmpty()) "<p>$it</p>" else ""
        } ?: ""

        val footerHtml = document.footerList.firstOrNull()?.text?.trim()?.let {
            if (it.isNotEmpty()) "<p>$it</p>" else ""
        } ?: ""

        for (element in document.bodyElements) {
            when (element.elementType) {
                BodyElementType.PARAGRAPH -> {
                    paragraphCount++
                    val p = element as XWPFParagraph
                    val align = when (p.alignment) {
                        ParagraphAlignment.CENTER -> "center"
                        ParagraphAlignment.RIGHT -> "right"
                        ParagraphAlignment.BOTH -> "justify"
                        else -> "left"
                    }
                    sb.append("<p style=\"text-align: $align;\">")

                    val ctpXml = runCatching { p.ctp?.toString() ?: "" }.getOrDefault("")
                    if (ctpXml.contains("dgm:") || ctpXml.contains("smartArt") || ctpXml.contains("diagram")) {
                        smartArtCount++
                        sb.append("<div class=\"remoffice-visual-preserved\" style=\"border: 1px dashed #2563EB; padding: 8px; margin: 6px 0; background-color: #EFF6FF; border-radius: 6px;\"><span style=\"color: #1D4ED8; font-weight: bold; font-size: 11px;\">📊 [عنصر SmartArt محفوظ بصرياً]</span></div>")
                    } else if (ctpXml.contains("c:chart") || ctpXml.contains("chart")) {
                        chartCount++
                        sb.append("<div class=\"remoffice-visual-preserved\" style=\"border: 1px dashed #059669; padding: 8px; margin: 6px 0; background-color: #ECFDF5; border-radius: 6px;\"><span style=\"color: #047857; font-weight: bold; font-size: 11px;\">📈 [مخطط بياني محفوظ بصرياً]</span></div>")
                    }
                    if (ctpXml.contains("w:fldSimple") || ctpXml.contains("w:instrText")) {
                        fieldCount++
                    }

                    if (p.runs.isEmpty()) {
                        sb.append("<br>")
                    } else {
                        for (run in p.runs) {
                            // Extract embedded pictures
                            for (pic in run.embeddedPictures) {
                                val picData = pic.pictureData
                                val dataBytes = picData?.data
                                if (dataBytes != null && dataBytes.isNotEmpty()) {
                                    val b64 = Base64.encodeToString(dataBytes, Base64.NO_WRAP)
                                    val ext = picData.suggestFileExtension() ?: "png"
                                    val mime = if (ext.equals("jpg", ignoreCase = true)) "image/jpeg" else "image/${ext.lowercase()}"
                                    sb.append("<div style=\"text-align: center; margin: 8px 0;\"><img src=\"data:$mime;base64,$b64\" style=\"max-width: 100%; height: auto; border-radius: 4px;\" alt=\"${pic.description ?: "صورة مدمجة"}\" /></div>")
                                    imageCount++
                                }
                            }

                            var text = run.text() ?: ""
                            text = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                            if (text.isEmpty()) continue
                            val styles = mutableListOf<String>()
                            run.color?.let { styles.add("color: #$it") }
                            run.fontFamily?.let { styles.add("font-family: '$it'") }
                            if (run.fontSizeAsDouble != null && run.fontSizeAsDouble > 0) {
                                styles.add("font-size: ${run.fontSizeAsDouble.toInt()}pt")
                            }
                            var wrapped = if (styles.isNotEmpty()) {
                                "<span style=\"${styles.joinToString("; ")}\">$text</span>"
                            } else {
                                text
                            }
                            if (run.isBold) wrapped = "<b>$wrapped</b>"
                            if (run.isItalic) wrapped = "<i>$wrapped</i>"
                            if (run.underline != UnderlinePatterns.NONE) wrapped = "<u>$wrapped</u>"
                            if (run.isStrikeThrough) wrapped = "<s>$wrapped</s>"
                            sb.append(wrapped)
                        }
                    }
                    sb.append("</p>")
                }
                BodyElementType.TABLE -> {
                    tableCount++
                    val table = element as XWPFTable
                    sb.append("<table border=\"1\" style=\"width: 100%; border-collapse: collapse; margin: 8px 0;\"><tbody>")
                    for (row in table.rows) {
                        sb.append("<tr>")
                        for (cell in row.tableCells) {
                            val cellSb = StringBuilder()
                            for (cp in cell.paragraphs) {
                                for (cr in cp.runs) {
                                    for (pic in cr.embeddedPictures) {
                                        val picData = pic.pictureData
                                        val dataBytes = picData?.data
                                        if (dataBytes != null && dataBytes.isNotEmpty()) {
                                            val b64 = Base64.encodeToString(dataBytes, Base64.NO_WRAP)
                                            val ext = picData.suggestFileExtension() ?: "png"
                                            val mime = if (ext.equals("jpg", ignoreCase = true)) "image/jpeg" else "image/${ext.lowercase()}"
                                            cellSb.append("<img src=\"data:$mime;base64,$b64\" style=\"max-width: 100%; height: auto;\" />")
                                            imageCount++
                                        }
                                    }
                                    val t = (cr.text() ?: "").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                                    if (t.isNotEmpty()) {
                                        var wt = t
                                        if (cr.isBold) wt = "<b>$wt</b>"
                                        if (cr.isItalic) wt = "<i>$wt</i>"
                                        cellSb.append(wt)
                                    }
                                }
                            }
                            val cellContent = cellSb.toString().ifEmpty {
                                cell.text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").ifEmpty { "<br>" }
                            }
                            sb.append("<td style=\"padding: 6px; border: 1px solid #CBD5E1;\">$cellContent</td>")
                        }
                        sb.append("</tr>")
                    }
                    sb.append("</tbody></table>")
                }
                else -> {}
            }
        }

        // Extract footnotes/endnotes
        val notes = mutableListOf<DocumentNote>()
        runCatching {
            document.footnotes?.forEachIndexed { idx, fn ->
                val txt = fn.paragraphs.joinToString("\n") { it.text }.trim()
                if (txt.isNotEmpty()) {
                    notes.add(
                        DocumentNote(
                            id = "fn_${fn.id ?: idx}",
                            type = "FOOTNOTE",
                            text = txt,
                            number = idx + 1
                        )
                    )
                }
            }
        }

        val report = DocumentImportReport(
            paragraphCount = paragraphCount,
            tableCount = tableCount,
            imageCount = imageCount,
            smartArtCount = smartArtCount,
            chartCount = chartCount,
            fieldCount = fieldCount,
            noteCount = notes.size,
            svgConvertedCount = 0,
            imageFallbackCount = imageCount,
            unsupportedCount = smartArtCount + chartCount,
            unsupportedReasons = buildList {
                if (smartArtCount > 0) add("تم الحفاظ على عناصر SmartArt بصرياً داخل المستند كعناصر محمية.")
                if (chartCount > 0) add("تم الحفاظ على المخططات البيانية بصرياً لضمان عدم فقدان أي بيانات.")
            }
        )

        document.close()
        val bodyHtml = HtmlSanitizer.sanitizeHtml(sb.toString().ifBlank { "<p><br></p>" })
        return DocumentPayload(
            bodyHtml = bodyHtml,
            headerHtml = headerHtml,
            footerHtml = footerHtml,
            notes = notes,
            importReport = report
        )
    }

    fun txtToHtml(inputStream: InputStream): String {
        val text = inputStream.bufferedReader(Charsets.UTF_8).readText()
        val sb = StringBuilder()
        text.lines().forEach { line ->
            val escaped = line.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            sb.append("<p>${escaped.ifEmpty { "<br>" }}</p>")
        }
        return sb.toString().ifBlank { "<p><br></p>" }
    }
}

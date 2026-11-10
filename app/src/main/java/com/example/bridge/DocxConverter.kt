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
import com.example.bridge.SmartArtChartUtils
import com.example.bridge.OoxmlDiagramChartParser
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

        if (element.hasClass("smartart-block")) {
            val jsonAttr = element.attr("data-smartart-json")
            val smartArt = if (jsonAttr.isNotBlank()) {
                val decoded = java.net.URLDecoder.decode(jsonAttr, "UTF-8")
                SmartArtChartUtils.jsonToSmartArt(decoded)
            } else null

            val p = document.createParagraph()
            val run = p.createRun()
            run.isBold = true
            run.color = "1A56DB"
            run.setText("📊 SmartArt: ${smartArt?.title ?: "عنصر تجميعي"}")

            if (smartArt != null && smartArt.nodes.isNotEmpty()) {
                val table = document.createTable(smartArt.nodes.size + 1, 2)
                table.getRow(0).getCell(0).text = "المستوى / Level"
                table.getRow(0).getCell(1).text = "النص / Text"
                smartArt.nodes.forEachIndexed { idx, node ->
                    val row = table.getRow(idx + 1)
                    row.getCell(0).text = node.level.toString()
                    row.getCell(1).text = node.text
                }
            }
            return
        }

        if (element.hasClass("chart-block")) {
            val jsonAttr = element.attr("data-chart-json")
            val chart = if (jsonAttr.isNotBlank()) {
                val decoded = java.net.URLDecoder.decode(jsonAttr, "UTF-8")
                SmartArtChartUtils.jsonToChart(decoded)
            } else null

            val p = document.createParagraph()
            val run = p.createRun()
            run.isBold = true
            run.color = "10B981"
            run.setText("📈 مخطط بياني / Chart: ${chart?.title ?: ""}")

            if (chart != null && chart.categories.isNotEmpty()) {
                val cols = chart.categories.size + 1
                val rows = chart.series.size + 1
                val table = document.createTable(rows, cols)

                table.getRow(0).getCell(0).text = "السلسلة / الفئة"
                chart.categories.forEachIndexed { cIdx, cat ->
                    table.getRow(0).getCell(cIdx + 1).text = cat
                }
                chart.series.forEachIndexed { sIdx, s ->
                    val row = table.getRow(sIdx + 1)
                    row.getCell(0).text = s.name
                    s.values.forEachIndexed { vIdx, v ->
                        if (vIdx + 1 < cols) {
                            row.getCell(vIdx + 1).text = v.toString()
                        }
                    }
                }
            }
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

    fun detectAndLoadDocument(bytes: ByteArray, extension: String, fileName: String = ""): DocumentPayload {
        if (bytes.isEmpty()) {
            return DocumentPayload(
                bodyHtml = "<p><br></p>",
                importReport = DocumentImportReport(unsupportedCount = 1, unsupportedReasons = listOf("الملف فارغ (0 بايت)"))
            )
        }

        val isZipOoxml = bytes.size >= 4 &&
                bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() &&
                bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()

        val isOle2Doc = bytes.size >= 8 &&
                bytes[0] == 0xD0.toByte() && bytes[1] == 0xCF.toByte() &&
                bytes[2] == 0x11.toByte() && bytes[3] == 0xE0.toByte()

        val startStr = if (bytes.size > 20) String(bytes.copyOfRange(0, 20.coerceAtMost(bytes.size)), Charsets.US_ASCII) else ""
        val isRtf = startStr.startsWith("{\\rtf")

        val ext = extension.lowercase().trim()

        return when {
            isZipOoxml || ext in listOf("docx", "docm", "dotx", "dotm") -> {
                docxToPayload(ByteArrayInputStream(bytes))
            }
            isOle2Doc || ext in listOf("doc", "dot") -> {
                hwpfToPayload(ByteArrayInputStream(bytes))
            }
            isRtf || ext == "rtf" -> {
                rtfToPayload(bytes)
            }
            ext in listOf("html", "htm") || startStr.contains("<html", ignoreCase = true) || startStr.contains("<!doctype", ignoreCase = true) -> {
                htmlFileToPayload(bytes)
            }
            ext == "txt" -> {
                val lines = String(bytes, Charsets.UTF_8).lines()
                DocumentPayload(
                    bodyHtml = txtToHtml(ByteArrayInputStream(bytes)),
                    importReport = DocumentImportReport(
                        paragraphCount = lines.count { it.isNotBlank() }
                    )
                )
            }
            else -> {
                try {
                    docxToPayload(ByteArrayInputStream(bytes))
                } catch (_: Exception) {
                    try {
                        hwpfToPayload(ByteArrayInputStream(bytes))
                    } catch (_: Exception) {
                        DocumentPayload(
                            bodyHtml = txtToHtml(ByteArrayInputStream(bytes)),
                            importReport = DocumentImportReport(unsupportedCount = 1, unsupportedReasons = listOf("صيغة غير قياسية، تم استيراد النصوص كملف نصي"))
                        )
                    }
                }
            }
        }
    }

    fun loadDocument(inputStream: InputStream, extension: String): DocumentPayload {
        val bytes = inputStream.readBytes()
        return detectAndLoadDocument(bytes, extension)
    }

    fun rtfToPayload(bytes: ByteArray): DocumentPayload {
        val raw = String(bytes, Charsets.ISO_8859_1)
        val sb = StringBuilder()
        val currentParagraph = StringBuilder()
        var paragraphCount = 0

        var isBold = false
        var isItalic = false
        var isUnderline = false
        var isStrike = false
        var alignment = "right"
        var inGroup = 0
        var skipGroupDepth = -1

        var i = 0
        val len = raw.length
        while (i < len) {
            val c = raw[i]
            when (c) {
                '{' -> {
                    inGroup++
                    if (i + 1 < len && raw[i + 1] == '\\') {
                        val wordEnd = (i + 2 until len).firstOrNull { !raw[it].isLetter() } ?: len
                        val control = raw.substring(i + 1, wordEnd)
                        if (control.startsWith("*") || control == "fonttbl" || control == "colortbl" || control == "stylesheet" || control == "info") {
                            if (skipGroupDepth == -1) skipGroupDepth = inGroup
                        }
                    }
                    i++
                }
                '}' -> {
                    if (skipGroupDepth == inGroup) {
                        skipGroupDepth = -1
                    }
                    inGroup--
                    i++
                }
                '\\' -> {
                    i++
                    if (i >= len) break
                    val nextChar = raw[i]
                    if (nextChar == '\\' || nextChar == '{' || nextChar == '}') {
                        if (skipGroupDepth == -1) currentParagraph.append(nextChar)
                        i++
                    } else if (nextChar == '\'') {
                        i++
                        if (i + 1 < len) {
                            val hex = raw.substring(i, i + 2)
                            val b = hex.toIntOrNull(16)
                            if (b != null && skipGroupDepth == -1) {
                                val decodedChar = try {
                                    String(byteArrayOf(b.toByte()), java.nio.charset.Charset.forName("windows-1256"))
                                } catch (_: Exception) {
                                    b.toChar().toString()
                                }
                                currentParagraph.append(decodedChar)
                            }
                            i += 2
                        }
                    } else {
                        val start = i
                        while (i < len && raw[i].isLetter()) {
                            i++
                        }
                        val word = raw.substring(start, i)
                        val paramStart = i
                        if (i < len && (raw[i] == '-' || raw[i].isDigit())) {
                            i++
                            while (i < len && raw[i].isDigit()) {
                                i++
                            }
                        }
                        val param = if (paramStart < i) raw.substring(paramStart, i).toIntOrNull() else null
                        if (i < len && raw[i] == ' ') {
                            i++
                        }

                        if (skipGroupDepth == -1) {
                            when (word) {
                                "par", "line" -> {
                                    paragraphCount++
                                    val text = currentParagraph.toString().trim()
                                    if (text.isNotEmpty() || sb.isEmpty()) {
                                        sb.append("<p style=\"text-align: $alignment;\">${text.ifEmpty { "<br>" }}</p>")
                                    }
                                    currentParagraph.clear()
                                }
                                "b" -> isBold = (param == null || param != 0)
                                "i" -> isItalic = (param == null || param != 0)
                                "ul" -> isUnderline = true
                                "ulnone" -> isUnderline = false
                                "strike" -> isStrike = (param == null || param != 0)
                                "qc" -> alignment = "center"
                                "qr" -> alignment = "right"
                                "ql" -> alignment = "left"
                                "qj" -> alignment = "justify"
                                "u" -> {
                                    if (param != null) {
                                        val codePoint = if (param < 0) param + 65536 else param
                                        currentParagraph.append(codePoint.toChar())
                                        if (i < len && raw[i] == '?') {
                                            i++
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                '\r', '\n' -> {
                    i++
                }
                else -> {
                    if (skipGroupDepth == -1) {
                        currentParagraph.append(c)
                    }
                    i++
                }
            }
        }

        if (currentParagraph.isNotEmpty()) {
            paragraphCount++
            sb.append("<p style=\"text-align: $alignment;\">${currentParagraph.toString().trim().ifEmpty { "<br>" }}</p>")
        }

        val report = DocumentImportReport(
            paragraphCount = paragraphCount.coerceAtLeast(1),
            tableCount = 0,
            imageCount = 0,
            smartArtCount = 0,
            chartCount = 0,
            fieldCount = 0,
            commentCount = 0,
            noteCount = 0,
            svgConvertedCount = 0,
            drawingMlConvertedCount = 0,
            imageFallbackCount = 0,
            unsupportedCount = 0,
            unsupportedReasons = emptyList()
        )
        val bodyHtml = HtmlSanitizer.sanitizeHtml(sb.toString().ifBlank { "<p><br></p>" })
        return DocumentPayload(bodyHtml = bodyHtml, importReport = report)
    }

    fun htmlFileToPayload(bytes: ByteArray): DocumentPayload {
        val htmlStr = String(bytes, Charsets.UTF_8)
        val doc = Jsoup.parse(htmlStr)
        val body = doc.body()
        val paragraphCount = body.select("p, h1, h2, h3, h4, h5, h6, li").size
        val tableCount = body.select("table").size
        val imageCount = body.select("img").size

        val sanitized = HtmlSanitizer.sanitizeHtml(body.html().ifBlank { "<p><br></p>" })
        val report = DocumentImportReport(
            paragraphCount = paragraphCount.coerceAtLeast(1),
            tableCount = tableCount,
            imageCount = imageCount,
            smartArtCount = 0,
            chartCount = 0,
            fieldCount = 0,
            commentCount = 0,
            noteCount = 0,
            svgConvertedCount = 0,
            drawingMlConvertedCount = 0,
            imageFallbackCount = imageCount,
            unsupportedCount = 0,
            unsupportedReasons = emptyList()
        )
        return DocumentPayload(bodyHtml = sanitized, importReport = report)
    }

    private fun hwpfToPayload(inputStream: InputStream): DocumentPayload {
        val bytes = inputStream.readBytes()
        val document = org.apache.poi.hwpf.HWPFDocument(ByteArrayInputStream(bytes))
        val extractor = org.apache.poi.hwpf.extractor.WordExtractor(document)
        val sb = StringBuilder()
        var paragraphCount = 0
        var tableCount = 0
        var imageCount = 0

        val embeddedImagesHtml = StringBuilder()
        try {
            val picturesTable = document.picturesTable
            if (picturesTable != null) {
                val pics = picturesTable.allPictures
                for (pic in pics) {
                    val picBytes = pic.content
                    if (picBytes != null && picBytes.isNotEmpty()) {
                        val b64 = Base64.encodeToString(picBytes, Base64.NO_WRAP)
                        val ext = pic.suggestFileExtension() ?: "png"
                        val mime = if (ext.equals("jpg", ignoreCase = true)) "image/jpeg" else "image/${ext.lowercase()}"
                        embeddedImagesHtml.append("<div style=\"text-align: center; margin: 8px 0;\"><img src=\"data:$mime;base64,$b64\" style=\"max-width: 100%; height: auto; border-radius: 4px;\" alt=\"صورة مدمجة من مستند DOC\" /></div>")
                        imageCount++
                    }
                }
            }
        } catch (_: Exception) {}

        val range = document.range
        val numParas = range.numParagraphs()
        for (i in 0 until numParas) {
            val p = range.getParagraph(i)
            paragraphCount++
            val align = when (p.justification) {
                1 -> "center"
                2 -> "right"
                3 -> "justify"
                else -> "left"
            }
            sb.append("<p style=\"text-align: $align;\">")
            val numRuns = p.numCharacterRuns()
            var pHasText = false
            for (j in 0 until numRuns) {
                val run = p.getCharacterRun(j)
                val text = run.text()?.replace("&", "&amp;")?.replace("<", "&lt;")?.replace(">", "&gt;") ?: ""
                if (text.isNotEmpty()) {
                    pHasText = true
                    var formatted = text
                    if (run.isBold) formatted = "<b>$formatted</b>"
                    if (run.isItalic) formatted = "<i>$formatted</i>"
                    if (run.isVanished) continue
                    sb.append(formatted)
                }
            }
            if (!pHasText) {
                sb.append("<br>")
            }
            sb.append("</p>")
        }

        if (embeddedImagesHtml.isNotEmpty()) {
            sb.append(embeddedImagesHtml)
        }

        val headerText = extractor.headerText?.trim() ?: ""
        val footerText = extractor.footerText?.trim() ?: ""
        val headerHtml = if (headerText.isNotEmpty()) "<p>${headerText.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")}</p>" else ""
        val footerHtml = if (footerText.isNotEmpty()) "<p>${footerText.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")}</p>" else ""

        val report = DocumentImportReport(
            paragraphCount = paragraphCount,
            tableCount = tableCount,
            imageCount = imageCount,
            smartArtCount = 0,
            chartCount = 0,
            fieldCount = 0,
            commentCount = 0,
            noteCount = extractor.footnoteText?.count { it.isNotBlank() } ?: 0,
            svgConvertedCount = 0,
            drawingMlConvertedCount = 0,
            imageFallbackCount = imageCount,
            unsupportedCount = 0,
            unsupportedReasons = emptyList()
        )
        document.close()
        val bodyHtml = HtmlSanitizer.sanitizeHtml(sb.toString().ifBlank { "<p><br></p>" })
        return DocumentPayload(
            bodyHtml = bodyHtml,
            headerHtml = headerHtml,
            footerHtml = footerHtml,
            importReport = report
        )
    }

    fun docxToPayload(inputStream: InputStream): DocumentPayload {
        val bytes = inputStream.readBytes()
        val parsedDiagramsAndCharts = try {
            OoxmlDiagramChartParser.parseDocxDiagramsAndCharts(bytes)
        } catch (_: Exception) {
            Pair(emptyList(), emptyList())
        }
        val extractedSmartArts = parsedDiagramsAndCharts.first.toMutableList()
        val extractedCharts = parsedDiagramsAndCharts.second.toMutableList()

        val document = XWPFDocument(ByteArrayInputStream(bytes))
        val sb = StringBuilder()

        var paragraphCount = 0
        var tableCount = 0
        var imageCount = 0
        var smartArtCount = extractedSmartArts.size
        var chartCount = extractedCharts.size
        var fieldCount = 0

        val headerHtml = document.headerList.firstOrNull()?.text?.trim()?.let {
            if (it.isNotEmpty()) "<p>$it</p>" else ""
        } ?: ""

        val footerHtml = document.footerList.firstOrNull()?.text?.trim()?.let {
            if (it.isNotEmpty()) "<p>$it</p>" else ""
        } ?: ""

        var smartArtIndex = 0
        var chartIndex = 0

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
                    val ctpXml = runCatching { p.ctp?.toString() ?: "" }.getOrDefault("")

                    if (ctpXml.contains("dgm:") || ctpXml.contains("smartArt") || ctpXml.contains("diagram")) {
                        if (smartArtIndex < extractedSmartArts.size) {
                            val sa = extractedSmartArts[smartArtIndex++]
                            sb.append(SmartArtChartUtils.smartArtToHtmlBlock(sa))
                        } else {
                            val defaultSa = SmartArtChartUtils.createDefaultSmartArt(
                                id = "sa_${System.currentTimeMillis()}_$smartArtIndex",
                                title = "SmartArt (مستورد)"
                            )
                            extractedSmartArts.add(defaultSa)
                            smartArtIndex++
                            smartArtCount++
                            sb.append(SmartArtChartUtils.smartArtToHtmlBlock(defaultSa))
                        }
                    } else if (ctpXml.contains("c:chart") || ctpXml.contains("chart")) {
                        if (chartIndex < extractedCharts.size) {
                            val chart = extractedCharts[chartIndex++]
                            sb.append(SmartArtChartUtils.chartToHtmlBlock(chart))
                        } else {
                            val defaultChart = SmartArtChartUtils.createDefaultChart(
                                id = "chart_${System.currentTimeMillis()}_$chartIndex",
                                title = "مخطط بياني (مستورد)"
                            )
                            extractedCharts.add(defaultChart)
                            chartIndex++
                            chartCount++
                            sb.append(SmartArtChartUtils.chartToHtmlBlock(defaultChart))
                        }
                    } else {
                        sb.append("<p style=\"text-align: $align;\">")
                    }

                    if (ctpXml.contains("w:fldSimple") || ctpXml.contains("w:instrText")) {
                        fieldCount++
                    }

                    if (p.runs.isEmpty()) {
                        if (!ctpXml.contains("dgm:") && !ctpXml.contains("smartArt") && !ctpXml.contains("diagram") && !ctpXml.contains("c:chart") && !ctpXml.contains("chart")) {
                            sb.append("<br></p>")
                        }
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
                        if (!ctpXml.contains("dgm:") && !ctpXml.contains("smartArt") && !ctpXml.contains("diagram") && !ctpXml.contains("c:chart") && !ctpXml.contains("chart")) {
                            sb.append("</p>")
                        }
                    }
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

        // If there were diagrams parsed from OOXML zip structure that weren't matched inline, append them
        while (smartArtIndex < extractedSmartArts.size) {
            val sa = extractedSmartArts[smartArtIndex++]
            sb.append(SmartArtChartUtils.smartArtToHtmlBlock(sa))
        }
        while (chartIndex < extractedCharts.size) {
            val chart = extractedCharts[chartIndex++]
            sb.append(SmartArtChartUtils.chartToHtmlBlock(chart))
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

        val commentsCount = runCatching { document.comments?.size ?: 0 }.getOrDefault(0)
        val report = DocumentImportReport(
            paragraphCount = paragraphCount,
            tableCount = tableCount,
            imageCount = imageCount,
            smartArtCount = smartArtCount,
            chartCount = chartCount,
            fieldCount = fieldCount,
            commentCount = commentsCount,
            noteCount = notes.size,
            svgConvertedCount = smartArtCount + chartCount,
            drawingMlConvertedCount = smartArtCount + chartCount + imageCount,
            imageFallbackCount = imageCount,
            unsupportedCount = 0,
            unsupportedReasons = emptyList()
        )

        document.close()
        val bodyHtml = HtmlSanitizer.sanitizeHtml(sb.toString().ifBlank { "<p><br></p>" })
        return DocumentPayload(
            bodyHtml = bodyHtml,
            headerHtml = headerHtml,
            footerHtml = footerHtml,
            notes = notes,
            smartArts = extractedSmartArts,
            charts = extractedCharts,
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

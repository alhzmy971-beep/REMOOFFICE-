package com.example.bridge

import android.util.Base64
import com.example.model.ChartData
import com.example.model.DocumentAnchor
import com.example.model.DocumentImportReport
import com.example.model.DocumentNote
import com.example.model.DocumentPayload
import com.example.model.MarginPreset
import com.example.model.PageConfig
import com.example.model.PageOrientation
import com.example.model.SmartArtData
import com.example.security.HtmlSanitizer
import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.hwpf.extractor.WordExtractor
import org.apache.poi.hwpf.usermodel.CharacterRun
import org.apache.poi.hwpf.usermodel.HeaderStories
import org.apache.poi.hwpf.usermodel.Paragraph
import org.apache.poi.util.Units
import org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy
import org.apache.poi.xwpf.usermodel.BodyElementType
import org.apache.poi.xwpf.usermodel.BreakType
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.UnderlinePatterns
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFTable
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STPageOrientation
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
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

        val smartArtsToExport = mutableListOf<SmartArtData>()
        val chartsToExport = mutableListOf<ChartData>()

        val sanitized = HtmlSanitizer.sanitizeHtml(payload.bodyHtml)
        val doc = Jsoup.parseBodyFragment(sanitized)
        val body = doc.body()

        for (child in body.children()) {
            processBlockElement(document, child, payload, smartArtsToExport, chartsToExport)
        }

        // Ensure any smartArts or charts on the payload are preserved even if not present as HTML blocks
        payload.smartArts.forEach { sa ->
            if (smartArtsToExport.none { it.id == sa.id }) {
                smartArtsToExport.add(sa)
                val p = document.createParagraph()
                p.createRun().setText("___REMO_SMARTART_MARKER_${sa.id}___")
            }
        }
        payload.charts.forEach { ch ->
            if (chartsToExport.none { it.id == ch.id }) {
                chartsToExport.add(ch)
                val p = document.createParagraph()
                p.createRun().setText("___REMO_CHART_MARKER_${ch.id}___")
            }
        }

        if (document.paragraphs.isEmpty() && document.tables.isEmpty()) {
            document.createParagraph()
        }

        val tempBaos = ByteArrayOutputStream()
        document.write(tempBaos)
        document.close()
        val tempBytes = tempBaos.toByteArray()

        try {
            OoxmlPackageEnricher.enrichDocxPackage(tempBytes, smartArtsToExport, chartsToExport, outputStream)
        } catch (_: Exception) {
            outputStream.write(tempBytes)
        }
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

    private fun processBlockElement(
        document: XWPFDocument,
        element: Element,
        payload: DocumentPayload,
        smartArtsToExport: MutableList<SmartArtData>,
        chartsToExport: MutableList<ChartData>
    ) {
        val tag = element.tagName().lowercase()
        if (element.hasClass("page-break")) {
            val p = document.createParagraph()
            p.createRun().addBreak(BreakType.PAGE)
            return
        }

        if (element.hasClass("smartart-block") || element.hasAttr("data-smartart-id")) {
            val saId = element.attr("data-smartart-id")
            val jsonAttr = element.attr("data-smartart-json")
            val smartArt = (if (jsonAttr.isNotBlank()) {
                runCatching {
                    val decoded = java.net.URLDecoder.decode(jsonAttr, "UTF-8")
                    SmartArtChartUtils.jsonToSmartArt(decoded)
                }.getOrNull()
            } else null) ?: payload.smartArts.find { it.id == saId }

            if (smartArt != null) {
                smartArtsToExport.add(smartArt)
                val p = document.createParagraph()
                val r = p.createRun()
                r.setText("___REMO_SMARTART_MARKER_${smartArt.id}___")
            }
            return
        }

        if (element.hasClass("chart-block") || element.hasAttr("data-chart-id")) {
            val chartId = element.attr("data-chart-id")
            val jsonAttr = element.attr("data-chart-json")
            val chart = (if (jsonAttr.isNotBlank()) {
                runCatching {
                    val decoded = java.net.URLDecoder.decode(jsonAttr, "UTF-8")
                    SmartArtChartUtils.jsonToChart(decoded)
                }.getOrNull()
            } else null) ?: payload.charts.find { it.id == chartId }

            if (chart != null) {
                chartsToExport.add(chart)
                val p = document.createParagraph()
                val r = p.createRun()
                r.setText("___REMO_CHART_MARKER_${chart.id}___")
            }
            return
        }

        when (tag) {
            "table" -> {
                val rows = element.select("tr")
                if (rows.isNotEmpty()) {
                    val maxCols = rows.maxOfOrNull { it.select("td, th").size } ?: 1
                    val table = document.createTable(rows.size, maxCols)
                    rows.forEachIndexed { rIdx, row ->
                        val cells = row.select("td, th")
                        cells.forEachIndexed { cIdx, cell ->
                            if (cIdx < maxCols) {
                                val cellObj = table.getRow(rIdx).getCell(cIdx)
                                cellObj.removeParagraph(0)
                                val cellParas = cell.select("p")
                                if (cellParas.isEmpty()) {
                                    val cp = cellObj.addParagraph()
                                    processInlineNodes(cp, cell)
                                } else {
                                    cellParas.forEach { pEl ->
                                        val cp = cellObj.addParagraph()
                                        processInlineNodes(cp, pEl)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            "p", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "div" -> {
                val p = document.createParagraph()
                when (tag) {
                    "h1" -> { p.style = "Heading 1" }
                    "h2" -> { p.style = "Heading 2" }
                    "h3" -> { p.style = "Heading 3" }
                }

                val textAlign = element.attr("style").split(";")
                    .map { it.trim().lowercase() }
                    .firstOrNull { it.startsWith("text-align:") }
                    ?.substringAfter(":")?.trim()

                when (textAlign) {
                    "center" -> p.alignment = ParagraphAlignment.CENTER
                    "right" -> p.alignment = ParagraphAlignment.RIGHT
                    "justify" -> p.alignment = ParagraphAlignment.BOTH
                    else -> p.alignment = ParagraphAlignment.LEFT
                }

                processInlineNodes(p, element)
                if (p.runs.isEmpty()) {
                    p.createRun().setText("")
                }
            }
            "ul", "ol" -> {
                val items = element.select("li")
                items.forEach { li ->
                    val p = document.createParagraph()
                    val bullet = if (tag == "ol") "1. " else "• "
                    p.createRun().setText(bullet)
                    processInlineNodes(p, li)
                }
            }
            else -> {
                val p = document.createParagraph()
                processInlineNodes(p, element)
            }
        }
    }

    private fun processInlineNodes(
        p: XWPFParagraph,
        container: Element,
        inheritedBold: Boolean = false,
        inheritedItalic: Boolean = false,
        inheritedUnderline: Boolean = false,
        inheritedColor: String? = null,
        inheritedFont: String? = null,
        inheritedSize: Double? = null
    ) {
        for (node in container.childNodes()) {
            when (node) {
                is TextNode -> {
                    val text = node.text()
                    if (text.isNotEmpty()) {
                        val run = p.createRun()
                        run.setText(text)
                        if (inheritedBold) run.isBold = true
                        if (inheritedItalic) run.isItalic = true
                        if (inheritedUnderline) run.underline = UnderlinePatterns.SINGLE
                        inheritedColor?.let { run.color = it }
                        inheritedFont?.let { run.fontFamily = it }
                        inheritedSize?.let { run.fontSize = it.toInt() }
                    }
                }
                is Element -> {
                    val tagName = node.tagName().lowercase()
                    if (tagName == "br") {
                        p.createRun().addBreak()
                        continue
                    }
                    if (tagName == "img") {
                        val src = node.attr("src")
                        if (src.startsWith("data:image/")) {
                            addBase64Picture(p, src)
                        }
                        continue
                    }

                    var isBold = inheritedBold || tagName in listOf("b", "strong")
                    var isItalic = inheritedItalic || tagName in listOf("i", "em")
                    var isUnderline = inheritedUnderline || tagName == "u"
                    var color = inheritedColor
                    var font = inheritedFont
                    var size = inheritedSize

                    val style = node.attr("style").lowercase()
                    if (style.isNotEmpty()) {
                        if (style.contains("font-weight: bold") || style.contains("font-weight:bold") || style.contains("font-weight: 700")) isBold = true
                        if (style.contains("font-style: italic") || style.contains("font-style:italic")) isItalic = true
                        if (style.contains("text-decoration: underline") || style.contains("text-decoration:underline")) isUnderline = true

                        val colorMatch = Regex("color:\\s*#?([0-9a-f]{6})", RegexOption.IGNORE_CASE).find(style)
                        colorMatch?.let { color = it.groupValues[1].uppercase() }

                        val fontMatch = Regex("font-family:\\s*['\"]?([^;'\",]+)['\"]?").find(style)
                        fontMatch?.let { font = it.groupValues[1].trim() }

                        val sizeMatch = Regex("font-size:\\s*([0-9]+(?:\\.[0-9]+)?)(?:pt|px)?").find(style)
                        sizeMatch?.let { size = it.groupValues[1].toDoubleOrNull() }
                    }

                    processInlineNodes(p, node, isBold, isItalic, isUnderline, color, font, size)
                }
            }
        }
    }

    private fun addBase64Picture(p: XWPFParagraph, src: String) {
        try {
            val base64Data = src.substringAfter("base64,")
            val bytes = Base64.decode(base64Data, Base64.DEFAULT)
            val picType = when {
                src.contains("image/jpeg") || src.contains("image/jpg") -> XWPFDocument.PICTURE_TYPE_JPEG
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
                    i++
                }
                '}' -> {
                    if (inGroup == skipGroupDepth) {
                        skipGroupDepth = -1
                    }
                    inGroup--
                    i++
                }
                '\\' -> {
                    i++
                    if (i >= len) break
                    val nextChar = raw[i]
                    if (nextChar == '*' || nextChar == '{' || nextChar == '}' || nextChar == '\\') {
                        if (nextChar == '*' && skipGroupDepth == -1) {
                            skipGroupDepth = inGroup
                        }
                        if (skipGroupDepth == -1 && (nextChar == '{' || nextChar == '}' || nextChar == '\\')) {
                            currentParagraph.append(nextChar)
                        }
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
            imageFallbackCount = 0,
            unsupportedCount = 0,
            unsupportedReasons = emptyList()
        )
        return DocumentPayload(bodyHtml = sanitized, importReport = report)
    }

    private fun hwpfToPayload(inputStream: InputStream): DocumentPayload {
        val bytes = inputStream.readBytes()
        val document = HWPFDocument(ByteArrayInputStream(bytes))
        val sb = StringBuilder()
        var paragraphCount = 0
        var tableCount = 0
        var imageCount = 0
        val unsupportedReasons = mutableListOf<String>()
        var unsupportedCount = 0

        val picturesTable = runCatching { document.picturesTable }.getOrNull()
        val embeddedImagesMap = mutableSetOf<ByteArray>()

        val range = document.range
        val numParas = range.numParagraphs()
        var i = 0

        while (i < numParas) {
            val p = range.getParagraph(i)
            if (p.isInTable) {
                val table = runCatching { range.getTable(p) }.getOrNull()
                if (table != null) {
                    tableCount++
                    sb.append("<table border=\"1\" style=\"width:100%; border-collapse:collapse; margin:10px 0;\"><tbody>")
                    for (r in 0 until table.numRows()) {
                        val row = table.getRow(r)
                        sb.append("<tr>")
                        for (c in 0 until row.numCells()) {
                            val cell = row.getCell(c)
                            sb.append("<td style=\"border:1px solid #CBD5E1; padding:6px;\">")
                            for (cp in 0 until cell.numParagraphs()) {
                                val cellP = cell.getParagraph(cp)
                                renderHwpfParagraphContent(cellP, picturesTable, embeddedImagesMap, sb) { imageCount++ }
                            }
                            sb.append("</td>")
                        }
                        sb.append("</tr>")
                    }
                    sb.append("</tbody></table>")

                    val tableEnd = table.endOffset
                    while (i < numParas && range.getParagraph(i).endOffset <= tableEnd) {
                        i++
                    }
                    continue
                }
            }

            paragraphCount++
            val align = when (p.justification) {
                1 -> "center"
                2 -> "right"
                3 -> "justify"
                else -> "left"
            }
            sb.append("<p style=\"text-align: $align;\">")
            renderHwpfParagraphContent(p, picturesTable, embeddedImagesMap, sb) { imageCount++ }
            sb.append("</p>")
            i++
        }

        // Append any remaining pictures not referenced in character runs (e.g. floating images)
        picturesTable?.allPictures?.forEach { pic ->
            val picBytes = pic.content
            if (picBytes != null && picBytes.isNotEmpty() && !embeddedImagesMap.contains(picBytes)) {
                val b64 = Base64.encodeToString(picBytes, Base64.NO_WRAP)
                val ext = pic.suggestFileExtension() ?: "png"
                val mime = if (ext.equals("jpg", ignoreCase = true)) "image/jpeg" else "image/${ext.lowercase()}"
                sb.append("<div style=\"text-align: center; margin: 8px 0;\"><img src=\"data:$mime;base64,$b64\" style=\"max-width: 100%; height: auto; border-radius: 4px;\" alt=\"صورة عائمة من مستند DOC\" /></div>")
                imageCount++
            }
        }

        // Headers and footers via HeaderStories
        var headerHtml = ""
        var footerHtml = ""
        runCatching {
            val stories = HeaderStories(document)
            val hText = (stories.firstHeader ?: stories.oddHeader ?: stories.evenHeader)?.trim() ?: ""
            val fText = (stories.firstFooter ?: stories.oddFooter ?: stories.evenFooter)?.trim() ?: ""
            if (hText.isNotBlank()) headerHtml = "<p>${escapeHtml(hText)}</p>"
            if (fText.isNotBlank()) footerHtml = "<p>${escapeHtml(fText)}</p>"
        }

        // Footnotes
        val notes = mutableListOf<DocumentNote>()
        runCatching {
            val fnRange = document.footnoteRange
            if (fnRange != null && fnRange.text().isNotBlank()) {
                val fnText = fnRange.text().trim()
                notes.add(DocumentNote(id = "fn_1", type = "FOOTNOTE", text = fnText, number = 1))
            }
        }

        // Page Config from section
        val pageConfig = runCatching {
            val section = range.getSection(0)
            val isLandscape = section.pageWidth > section.pageHeight
            val marginMm = (section.marginLeft / 56.692913f)
            PageConfig(
                orientation = if (isLandscape) PageOrientation.LANDSCAPE else PageOrientation.PORTRAIT,
                marginPreset = MarginPreset.CUSTOM,
                customMarginMm = marginMm
            )
        }.getOrDefault(PageConfig())

        val report = DocumentImportReport(
            paragraphCount = paragraphCount,
            tableCount = tableCount,
            imageCount = imageCount,
            smartArtCount = 0,
            chartCount = 0,
            fieldCount = 0,
            commentCount = 0,
            noteCount = notes.size,
            svgConvertedCount = 0,
            drawingMlConvertedCount = 0,
            imageFallbackCount = 0,
            unsupportedCount = unsupportedCount,
            unsupportedReasons = unsupportedReasons
        )
        document.close()
        val bodyHtml = HtmlSanitizer.sanitizeHtml(sb.toString().ifBlank { "<p><br></p>" })
        return DocumentPayload(
            bodyHtml = bodyHtml,
            headerHtml = headerHtml,
            footerHtml = footerHtml,
            pageConfig = pageConfig,
            notes = notes,
            importReport = report
        )
    }

    private fun renderHwpfParagraphContent(
        p: Paragraph,
        picturesTable: org.apache.poi.hwpf.model.PicturesTable?,
        embeddedImagesMap: MutableSet<ByteArray>,
        sb: StringBuilder,
        onImage: () -> Unit
    ) {
        val numRuns = p.numCharacterRuns()
        var pHasText = false
        for (j in 0 until numRuns) {
            val run = p.getCharacterRun(j)
            if (picturesTable != null && (picturesTable.hasPicture(run) || picturesTable.hasEscherPicture(run))) {
                val pic = runCatching { picturesTable.extractPicture(run, true) }.getOrNull()
                if (pic != null && pic.content != null && pic.content.isNotEmpty()) {
                    val picBytes = pic.content
                    embeddedImagesMap.add(picBytes)
                    val b64 = Base64.encodeToString(picBytes, Base64.NO_WRAP)
                    val ext = pic.suggestFileExtension() ?: "png"
                    val mime = if (ext.equals("jpg", ignoreCase = true)) "image/jpeg" else "image/${ext.lowercase()}"
                    sb.append("<div style=\"text-align: center; margin: 8px 0;\"><img src=\"data:$mime;base64,$b64\" style=\"max-width: 100%; height: auto; border-radius: 4px;\" alt=\"صورة مدمجة\" /></div>")
                    onImage()
                    pHasText = true
                }
            }

            val rawText = run.text() ?: ""
            val cleanText = rawText.replace("\u0001", "").replace("\u0008", "")
            val text = escapeHtml(cleanText)
            if (text.isNotEmpty()) {
                pHasText = true
                var formatted = text
                val styles = mutableListOf<String>()
                val font = run.fontName
                if (!font.isNullOrBlank()) styles.add("font-family: '$font'")
                val fontSizePt = run.fontSize / 2
                if (fontSizePt > 0) styles.add("font-size: ${fontSizePt}pt")
                val colorIdx = run.color
                val colorHex = if (colorIdx in 1..16) getColorHexForIndex(colorIdx) else null
                colorHex?.let { styles.add("color: $it") }

                if (styles.isNotEmpty()) {
                    formatted = "<span style=\"${styles.joinToString("; ")}\">$formatted</span>"
                }
                if (run.isBold) formatted = "<b>$formatted</b>"
                if (run.isItalic) formatted = "<i>$formatted</i>"
                if (run.underlineCode != 0) formatted = "<u>$formatted</u>"
                if (run.isStrikeThrough) formatted = "<s>$formatted</s>"
                sb.append(formatted)
            }
        }
        if (!pHasText) {
            sb.append("<br>")
        }
    }

    private fun getColorHexForIndex(index: Int): String? {
        return when (index) {
            1 -> "#000000" // Black
            2 -> "#0000FF" // Blue
            3 -> "#00FFFF" // Cyan
            4 -> "#00FF00" // Green
            5 -> "#FF00FF" // Magenta
            6 -> "#FF0000" // Red
            7 -> "#FFFF00" // Yellow
            8 -> "#FFFFFF" // White
            9 -> "#000080" // Dark Blue
            10 -> "#008080" // Dark Cyan
            11 -> "#008000" // Dark Green
            12 -> "#800080" // Dark Magenta
            13 -> "#800000" // Dark Red
            14 -> "#808000" // Dark Yellow
            15 -> "#808080" // Dark Gray
            16 -> "#C0C0C0" // Light Gray
            else -> null
        }
    }

    private fun escapeHtml(s: String): String {
        return s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
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
        val unsupportedReasons = mutableListOf<String>()
        var unsupportedCount = 0

        val headerHtml = document.headerList.firstOrNull()?.text?.trim()?.let {
            if (it.isNotEmpty()) "<p>${escapeHtml(it)}</p>" else ""
        } ?: ""

        val footerHtml = document.footerList.firstOrNull()?.text?.trim()?.let {
            if (it.isNotEmpty()) "<p>${escapeHtml(it)}</p>" else ""
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
                            unsupportedCount++
                            unsupportedReasons.add("مخطط SmartArt غير مدعوم أو تالف في المستند")
                            sb.append("<div class=\"unsupported-block\" style=\"border:1px dashed #CBD5E1; padding:8px; margin:8px 0; color:#64748B;\">[مخطط SmartArt غير مدعوم]</div>")
                        }
                    } else if (ctpXml.contains("c:chart") || ctpXml.contains("chart")) {
                        if (chartIndex < extractedCharts.size) {
                            val chart = extractedCharts[chartIndex++]
                            sb.append(SmartArtChartUtils.chartToHtmlBlock(chart))
                        } else {
                            unsupportedCount++
                            unsupportedReasons.add("مخطط بياني غير مدعوم أو تالف في المستند")
                            sb.append("<div class=\"unsupported-block\" style=\"border:1px dashed #CBD5E1; padding:8px; margin:8px 0; color:#64748B;\">[مخطط بياني غير مدعوم]</div>")
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
                            text = escapeHtml(text)
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
                    val tbl = element as XWPFTable
                    sb.append("<table border=\"1\" style=\"width: 100%; border-collapse: collapse; margin: 10px 0;\"><tbody>")
                    for (row in tbl.rows) {
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
                                    val t = escapeHtml(cr.text() ?: "")
                                    if (t.isNotEmpty()) {
                                        var wt = t
                                        if (cr.isBold) wt = "<b>$wt</b>"
                                        if (cr.isItalic) wt = "<i>$wt</i>"
                                        cellSb.append(wt)
                                    }
                                }
                            }
                            val cellContent = cellSb.toString().ifEmpty {
                                escapeHtml(cell.text).ifEmpty { "<br>" }
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

        // If there were diagrams or charts parsed from OOXML zip structure that weren't matched inline, append them
        while (smartArtIndex < extractedSmartArts.size) {
            val sa = extractedSmartArts[smartArtIndex++]
            sb.append(SmartArtChartUtils.smartArtToHtmlBlock(sa))
        }
        while (chartIndex < extractedCharts.size) {
            val chart = extractedCharts[chartIndex++]
            sb.append(SmartArtChartUtils.chartToHtmlBlock(chart))
        }

        // Footnotes
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

        // Strict calculation of import metrics
        val drawingMlCount = smartArtCount + chartCount
        val svgCount = chartCount // chartToHtmlBlock produces valid SVG!

        val report = DocumentImportReport(
            paragraphCount = paragraphCount,
            tableCount = tableCount,
            imageCount = imageCount,
            smartArtCount = smartArtCount,
            chartCount = chartCount,
            fieldCount = fieldCount,
            commentCount = commentsCount,
            noteCount = notes.size,
            svgConvertedCount = svgCount,
            drawingMlConvertedCount = drawingMlCount,
            imageFallbackCount = 0,
            unsupportedCount = unsupportedCount,
            unsupportedReasons = unsupportedReasons
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
            val escaped = escapeHtml(line)
            sb.append("<p>${escaped.ifEmpty { "<br>" }}</p>")
        }
        return sb.toString().ifBlank { "<p><br></p>" }
    }
}

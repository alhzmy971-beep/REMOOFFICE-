package com.example.bridge

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.PointF
import android.util.Base64
import com.example.model.DocumentPayload
import com.example.model.MarginPreset
import com.example.model.PageConfig
import com.example.model.PageOrientation
import com.example.model.PageSize
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.contentstream.PDFGraphicsStreamEngine
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImage
import com.tom_roush.pdfbox.rendering.PDFRenderer
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.IOException
import java.util.IdentityHashMap
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

object PdfImporter {
    @Volatile private var initialized = false

    private data class PdfTextStyle(
        val color: String,
        val fontName: String,
        val bold: Boolean,
        val italic: Boolean
    )

    private data class PositionedText(
        val text: String,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val fontSize: Float,
        val direction: Int,
        val style: PdfTextStyle
    )

    private data class PositionedImage(
        val dataUrl: String,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float
    )

    fun pdfToHtml(context: Context, inputStream: InputStream): String =
        pdfToPayload(context, inputStream).bodyHtml

    fun pdfToPayload(context: Context, inputStream: InputStream): DocumentPayload {
        if (!initialized) synchronized(this) {
            if (!initialized) {
                PDFBoxResourceLoader.init(context.applicationContext)
                initialized = true
            }
        }

        PDDocument.load(inputStream).use { document ->
            if (document.numberOfPages == 0) return DocumentPayload()

            val firstPage = document.getPage(0)
            val firstBox = firstPage.cropBox
            val firstRotated = firstPage.rotation % 180 != 0
            val pageConfig = pageConfigFor(
                if (firstRotated) firstBox.height else firstBox.width,
                if (firstRotated) firstBox.width else firstBox.height
            )
            val targetWidthPt = pageWidthMm(pageConfig).toFloat() * 72f / 25.4f
            val renderer = PDFRenderer(document)
            val html = StringBuilder()

            for (index in 0 until document.numberOfPages) {
                val page = document.getPage(index)
                val box = page.cropBox
                val rotated = page.rotation % 180 != 0
                val pageWidth = max(1f, if (rotated) box.height else box.width)
                val pageHeight = max(1f, if (rotated) box.width else box.height)
                val scale = targetWidthPt / pageWidth

                val graphics = PageGraphicsCollector(page, box.lowerLeftX, box.lowerLeftY, pageWidth, pageHeight)
                graphics.processPage(page)

                val textStripper = PositionedTextStripper()
                textStripper.sortByPosition = true
                textStripper.startPage = index + 1
                textStripper.endPage = index + 1
                textStripper.getText(document)
                val lines = textStripper.positionedText

                val fallbackImage = if (lines.isEmpty() && graphics.images.isEmpty() && graphics.svgPaths.isEmpty()) {
                    renderPageAsImage(renderer, index)
                } else null

                html.append("<div class=\"pdf-page\" data-pdf-page=\"").append(index + 1)
                    .append("\" style=\"position:relative;width:100%;height:")
                    .append(cssNumber(pageHeight * scale)).append("pt;min-height:")
                    .append(cssNumber(pageHeight * scale)).append("pt;overflow:hidden;\">")

                if (fallbackImage != null) {
                    html.append("<img class=\"pdf-page-image-fallback\" alt=\"صفحة PDF ممسوحة ")
                        .append(index + 1).append("\" src=\"").append(fallbackImage)
                        .append("\" style=\"position:absolute;inset:0;width:100%;height:100%;object-fit:fill;z-index:1;\" />")
                }

                graphics.images.forEach { image ->
                    val left = (image.x * scale).coerceIn(-10000f, 100000f)
                    val top = (image.y * scale).coerceIn(-10000f, 100000f)
                    val width = (image.width * scale).coerceIn(0.1f, 100000f)
                    val height = (image.height * scale).coerceIn(0.1f, 100000f)
                    html.append("<img class=\"pdf-import-image\" alt=\"صورة من صفحة ").append(index + 1)
                        .append("\" src=\"").append(image.dataUrl)
                        .append("\" style=\"position:absolute;left:").append(cssNumber(left))
                        .append("pt;top:").append(cssNumber(top)).append("pt;width:").append(cssNumber(width))
                        .append("pt;height:").append(cssNumber(height)).append("pt;object-fit:fill;z-index:2;\" />")
                }

                if (graphics.svgPaths.isNotEmpty()) {
                    html.append("<svg class=\"pdf-vector-layer\" width=\"").append(cssNumber(pageWidth))
                        .append("\" height=\"").append(cssNumber(pageHeight)).append("\" viewBox=\"0 0 ")
                        .append(cssNumber(pageWidth)).append(' ').append(cssNumber(pageHeight))
                        .append("\" style=\"position:absolute;inset:0;width:100%;height:100%;overflow:visible;pointer-events:none;z-index:2;\">")
                    graphics.svgPaths.forEach { html.append(it) }
                    html.append("</svg>")
                }

                lines.forEach { line ->
                    if (line.text.isNotBlank()) {
                        val leftPt = (line.x * scale).coerceIn(-10000f, 100000f)
                        val topPt = (line.y * scale).coerceIn(-10000f, 100000f)
                        val widthPt = max(line.width * scale, line.fontSize * scale).coerceAtMost(100000f)
                        val fontPt = line.fontSize.coerceIn(3f, 144f) * scale
                        val fontName = localFontFor(line.text, line.style.fontName)
                        val rtl = containsRtl(line.text)
                        val rotation = if (line.direction == 90) "transform:rotate(90deg);transform-origin:top left;" else ""
                        html.append("<p class=\"pdf-text-line\" style=\"position:absolute;left:")
                            .append(cssNumber(leftPt)).append("pt;top:").append(cssNumber(topPt))
                            .append("pt;width:").append(cssNumber(widthPt)).append("pt;min-height:")
                            .append(cssNumber(max(line.height * scale, fontPt))).append("pt;margin:0;padding:0;line-height:1.05;white-space:pre;overflow:visible;z-index:3;color:")
                            .append(line.style.color).append(";font-family:'").append(fontName)
                            .append("';font-size:").append(cssNumber(fontPt)).append("pt;font-weight:")
                            .append(if (line.style.bold) "700" else "400").append(";font-style:")
                            .append(if (line.style.italic) "italic" else "normal").append(";direction:")
                            .append(if (rtl) "rtl" else "ltr").append(";unicode-bidi:plaintext;text-align:")
                            .append(if (rtl) "right" else "left").append(';').append(rotation).append("\">")
                            .append(escapeHtml(line.text)).append("</p>")
                    }
                }
                html.append("</div>")
                if (index < document.numberOfPages - 1) {
                    html.append("<div class=\"page-break\" contenteditable=\"false\" aria-label=\"فاصل صفحة PDF\"></div>")
                }
            }

            return DocumentPayload(
                bodyHtml = html.toString().ifBlank { "<p><br></p>" },
                pageConfig = pageConfig
            )
        }
    }

    private fun pageConfigFor(widthPt: Float, heightPt: Float): PageConfig {
        val widthMm = widthPt * 25.4f / 72f
        val heightMm = heightPt * 25.4f / 72f
        data class Candidate(val size: PageSize, val orientation: PageOrientation, val width: Float, val height: Float)
        val candidates = PageSize.values().flatMap { size ->
            listOf(
                Candidate(size, PageOrientation.PORTRAIT, size.widthMm.toFloat(), size.heightMm.toFloat()),
                Candidate(size, PageOrientation.LANDSCAPE, size.heightMm.toFloat(), size.widthMm.toFloat())
            )
        }
        val chosen = candidates.minByOrNull { candidate ->
            abs(ln(max(candidate.width, 1f) / max(widthMm, 1f))) +
                abs(ln(max(candidate.height, 1f) / max(heightMm, 1f)))
        } ?: Candidate(PageSize.A4, PageOrientation.PORTRAIT, 210f, 297f)
        return PageConfig(
            size = chosen.size,
            orientation = chosen.orientation,
            marginPreset = MarginPreset.CUSTOM,
            customMarginMm = 0f,
            columns = 1
        )
    }

    private fun pageWidthMm(config: PageConfig): Int = when (config.orientation) {
        PageOrientation.PORTRAIT -> config.size.widthMm
        PageOrientation.LANDSCAPE -> config.size.heightMm
    }

    private fun renderPageAsImage(renderer: PDFRenderer, index: Int): String? = try {
        val bitmap = renderer.renderImageWithDPI(index, 220f)
        val bytes = ByteArrayOutputStream()
        val ok = bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes)
        bitmap.recycle()
        if (!ok) null else "data:image/png;base64," + Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
    } catch (_: Exception) {
        null
    }

    private fun colorHex(rgb: Int): String = String.format(Locale.US, "#%06X", rgb and 0xFFFFFF)

    private fun cssNumber(value: Float): String = String.format(Locale.US, "%.2f", value)

    private fun escapeHtml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private fun containsRtl(value: String): Boolean = value.any {
        Character.getDirectionality(it) == Character.DIRECTIONALITY_RIGHT_TO_LEFT ||
            Character.getDirectionality(it) == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
    }

    private fun localFontFor(text: String, pdfFontName: String): String {
        val name = pdfFontName.lowercase(Locale.ROOT)
        return when {
            name.contains("amiri") -> "Amiri"
            name.contains("cairo") -> "Cairo"
            name.contains("tajawal") -> "Tajawal"
            name.contains("naskh") -> "Noto Naskh Arabic"
            name.contains("kufi") -> "Noto Kufi Arabic"
            containsRtl(text) -> "REMOO Noto Sans Arabic"
            else -> "REMOO Noto Sans"
        }
    }

    private class PositionedTextStripper : PDFTextStripper() {
        val positionedText = mutableListOf<PositionedText>()
        private val styles = IdentityHashMap<TextPosition, PdfTextStyle>()

        override fun processTextPosition(text: TextPosition) {
            val fontName = try { text.font?.name ?: "" } catch (_: Exception) { "" }
            val color = try { colorHex(graphicsState.nonStrokingColor.toRGB()) } catch (_: Exception) { "#000000" }
            val fontLower = fontName.lowercase(Locale.ROOT)
            styles[text] = PdfTextStyle(
                color = color,
                fontName = fontName,
                bold = fontLower.contains("bold") || fontLower.contains("black") || fontLower.contains("demi"),
                italic = fontLower.contains("italic") || fontLower.contains("oblique")
            )
            super.processTextPosition(text)
        }

        override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
            if (text.isBlank()) return
            val valid = textPositions.filter { it.unicode != null }
            if (valid.isEmpty()) {
                positionedText.add(PositionedText(text, 0f, 0f, 0f, 12f, 12f, 0, PdfTextStyle("#000000", "", false, false)))
                return
            }
            val left = valid.minOf { it.x }
            val top = valid.minOf { (it.y - it.height).coerceAtLeast(0f) }
            val right = valid.maxOf { it.x + it.width }
            val bottom = valid.maxOf { it.y }
            val fontSize = valid.map { it.fontSizeInPt }.filter { it > 0f }.average().toFloat().takeIf { it.isFinite() } ?: 12f
            val style = styles[valid.first()] ?: PdfTextStyle("#000000", "", false, false)
            positionedText.add(
                PositionedText(
                    text = text,
                    x = left,
                    y = top,
                    width = max(1f, right - left),
                    height = max(1f, bottom - top),
                    fontSize = fontSize,
                    direction = if (abs(valid.first().dir - 90f) < 45f) 90 else 0,
                    style = style
                )
            )
        }

        override fun writeString(text: String) {
            if (text.isNotBlank()) {
                positionedText.add(PositionedText(text, 0f, 0f, 0f, 12f, 12f, 0, PdfTextStyle("#000000", "", false, false)))
            }
        }
    }

    private class PageGraphicsCollector(
        page: PDPage,
        private val cropLeft: Float,
        private val cropBottom: Float,
        private val pageWidth: Float,
        private val pageHeight: Float
    ) : PDFGraphicsStreamEngine(page) {
        val images = mutableListOf<PositionedImage>()
        val svgPaths = mutableListOf<String>()
        private val pathData = StringBuilder()
        private var currentPoint = PointF(0f, 0f)

        override fun drawImage(pdImage: PDImage) {
            try {
                val matrix = graphicsState.currentTransformationMatrix
                val points = listOf(
                    matrix.transformPoint(0f, 0f), matrix.transformPoint(1f, 0f),
                    matrix.transformPoint(0f, 1f), matrix.transformPoint(1f, 1f)
                ).map { displayPoint(it.x, it.y) }
                val left = points.minOf { it.x }
                val right = points.maxOf { it.x }
                val top = points.minOf { it.y }
                val bottom = points.maxOf { it.y }
                val width = right - left
                val height = bottom - top
                if (width <= 0f || height <= 0f) return

                val bitmap = pdImage.image ?: return
                val stream = ByteArrayOutputStream()
                val encoded = bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                bitmap.recycle()
                if (!encoded) return
                images.add(
                    PositionedImage(
                        dataUrl = "data:image/png;base64," + Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP),
                        x = left,
                        y = top,
                        width = min(width, pageWidth * 4f),
                        height = min(height, pageHeight * 4f)
                    )
                )
            } catch (_: Exception) {
                // Keep processing the page; an unreadable XObject must not discard its text.
            }
        }

        override fun appendRectangle(p0: PointF, p1: PointF, p2: PointF, p3: PointF) {
            moveTo(p0.x, p0.y); lineTo(p1.x, p1.y); lineTo(p2.x, p2.y); lineTo(p3.x, p3.y); closePath()
        }

        override fun clip(windingRule: Path.FillType) = Unit

        override fun moveTo(x: Float, y: Float) {
            val point = displayPoint(x, y)
            pathData.append("M ").append(svgNumber(point.x)).append(' ').append(svgNumber(point.y)).append(' ')
            currentPoint = PointF(x, y)
        }

        override fun lineTo(x: Float, y: Float) {
            val point = displayPoint(x, y)
            pathData.append("L ").append(svgNumber(point.x)).append(' ').append(svgNumber(point.y)).append(' ')
            currentPoint = PointF(x, y)
        }

        override fun curveTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
            val p1 = displayPoint(x1, y1)
            val p2 = displayPoint(x2, y2)
            val p3 = displayPoint(x3, y3)
            pathData.append("C ")
                .append(svgNumber(p1.x)).append(' ').append(svgNumber(p1.y)).append(' ')
                .append(svgNumber(p2.x)).append(' ').append(svgNumber(p2.y)).append(' ')
                .append(svgNumber(p3.x)).append(' ').append(svgNumber(p3.y)).append(' ')
            currentPoint = PointF(x3, y3)
        }

        private fun displayPoint(x: Float, y: Float): PointF {
            val rotation = ((getPage().rotation % 360) + 360) % 360
            val localX = x - cropLeft
            val localY = y - cropBottom
            return when (rotation) {
                90 -> PointF(localY, localX)
                180 -> PointF(pageWidth - localX, localY)
                270 -> PointF(pageWidth - localY, pageHeight - localX)
                else -> PointF(localX, pageHeight - localY)
            }
        }

        override fun getCurrentPoint(): PointF = PointF(currentPoint.x, currentPoint.y)
        override fun closePath() { pathData.append("Z ") }
        override fun endPath() { pathData.setLength(0) }

        override fun strokePath() {
            addVectorPath("none", safeColor(true), graphicsState.lineWidth)
        }

        override fun fillPath(windingRule: Path.FillType) {
            addVectorPath(safeColor(false), "none", 0f)
        }

        override fun fillAndStrokePath(windingRule: Path.FillType) {
            addVectorPath(safeColor(false), safeColor(true), graphicsState.lineWidth)
        }

        override fun shadingFill(shadingName: com.tom_roush.pdfbox.cos.COSName) = Unit

        private fun safeColor(stroke: Boolean): String = try {
            val color = if (stroke) graphicsState.strokingColor else graphicsState.nonStrokingColor
            colorHex(color.toRGB())
        } catch (_: Exception) { "#000000" }

        private fun addVectorPath(fill: String, stroke: String, strokeWidth: Float) {
            val d = pathData.toString().trim()
            if (d.isNotEmpty()) {
                svgPaths.add(
                    "<path d=\"$d\" fill=\"$fill\" stroke=\"$stroke\" stroke-width=\"${cssNumber(max(strokeWidth, 0.25f))}\" />"
                )
            }
            pathData.setLength(0)
        }

        private fun svgNumber(value: Float): String = String.format(Locale.US, "%.2f", value)
    }
}

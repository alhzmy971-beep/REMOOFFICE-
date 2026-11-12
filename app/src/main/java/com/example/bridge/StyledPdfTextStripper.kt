package com.example.bridge

import com.tom_roush.pdfbox.contentstream.operator.color.SetNonStrokingColor
import com.tom_roush.pdfbox.contentstream.operator.color.SetNonStrokingColorN
import com.tom_roush.pdfbox.contentstream.operator.color.SetNonStrokingColorSpace
import com.tom_roush.pdfbox.contentstream.operator.color.SetNonStrokingDeviceCMYKColor
import com.tom_roush.pdfbox.contentstream.operator.color.SetNonStrokingDeviceGrayColor
import com.tom_roush.pdfbox.contentstream.operator.color.SetNonStrokingDeviceRGBColor
import com.tom_roush.pdfbox.contentstream.operator.color.SetStrokingColor
import com.tom_roush.pdfbox.contentstream.operator.color.SetStrokingColorN
import com.tom_roush.pdfbox.contentstream.operator.color.SetStrokingColorSpace
import com.tom_roush.pdfbox.contentstream.operator.color.SetStrokingDeviceCMYKColor
import com.tom_roush.pdfbox.contentstream.operator.color.SetStrokingDeviceGrayColor
import com.tom_roush.pdfbox.contentstream.operator.color.SetStrokingDeviceRGBColor
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.util.IdentityHashMap

/**
 * A [PDFTextStripper] that keeps per-run typography so PDF import can preserve
 * font size, bold/italic **and text colour** instead of flattening everything
 * into plain paragraphs.
 *
 * ## How colour is recovered
 * `PDFStreamEngine.getGraphicsState()` is public in pdfbox-android 2.0.27.0 and
 * is inherited by [PDFTextStripper]. The *live* graphics state is only valid
 * while the content stream is being interpreted, i.e. inside
 * [processTextPosition]. By the time [writeString] is called (from
 * `writePage()` after the whole document has been parsed) the state has already
 * advanced past the last glyph, so reading it there would yield wrong colours.
 *
 * We therefore snapshot `graphicsState.nonStrokingColor` for every glyph in
 * [processTextPosition] and look the snapshot up by identity in [writeString].
 *
 * Each physical line is exposed as an ordered list of [Run]s. Runs are split
 * whenever size, bold, italic or colour changes.
 */
class StyledPdfTextStripper : PDFTextStripper() {

    init {
        // ------------------------------------------------------------------
        // ROOT CAUSE FIX for missing text colour:
        // PDFTextStripper extends LegacyPDFStreamEngine, which deliberately
        // registers ONLY the 22 text/state operators (BT, ET, Tf, Tj, TJ, Td,
        // TD, Tm, T*, Tc, Tw, Tz, Ts, Tr, cm, q, Q, gs, Do, ...). None of the
        // colour operators are registered, so `rg` / `g` / `k` / `sc` / `scn`
        // are silently dropped by PDFStreamEngine.unsupportedOperator() and
        // getGraphicsState().getNonStrokingColor() therefore never leaves its
        // default (DeviceGray 0.0 = black).
        //
        // Registering the standard colour operator processors makes the live
        // graphics state actually track the text fill colour.
        // ------------------------------------------------------------------
        addOperator(SetNonStrokingDeviceRGBColor())
        addOperator(SetNonStrokingDeviceGrayColor())
        addOperator(SetNonStrokingDeviceCMYKColor())
        addOperator(SetNonStrokingColor())
        addOperator(SetNonStrokingColorN())
        addOperator(SetNonStrokingColorSpace())
        addOperator(SetStrokingDeviceRGBColor())
        addOperator(SetStrokingDeviceGrayColor())
        addOperator(SetStrokingDeviceCMYKColor())
        addOperator(SetStrokingColor())
        addOperator(SetStrokingColorN())
        addOperator(SetStrokingColorSpace())
    }

    data class Run(
        val text: String,
        val sizePt: Float,
        val bold: Boolean,
        val italic: Boolean,
        val colorHex: String = DEFAULT_COLOR
    )

    /** One entry per visual line, in reading order. */
    val lines = mutableListOf<List<Run>>()

    /** Identity-keyed colour snapshots, captured while the stream is live. */
    private val colorByPosition = IdentityHashMap<TextPosition, String>()

    /**
     * Returns the current non-stroking (text fill) colour as `#RRGGBB`.
     *
     * This is the app's own `getGraphicsState` accessor: it reads
     * `PDFStreamEngine.getGraphicsState()` and converts the resulting
     * [com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor] to RGB. Returns
     * [DEFAULT_COLOR] when the colour cannot be resolved (pattern fills,
     * unreadable colour spaces, etc.).
     */
    fun currentTextColorHex(): String {
        return runCatching {
            val color = graphicsState.nonStrokingColor ?: return DEFAULT_COLOR
            if (color.isPattern) return DEFAULT_COLOR
            String.format("#%06X", color.toRGB() and 0xFFFFFF)
        }.getOrDefault(DEFAULT_COLOR)
    }

    override fun processTextPosition(text: TextPosition?) {
        super.processTextPosition(text)
        if (text != null) {
            colorByPosition[text] = currentTextColorHex()
        }
    }

    // NOTE: do not clear [colorByPosition] in startPage(). PDFTextStripper
    // parses *all* pages first and only then calls writePage() -> writeString(),
    // so per-page clearing would erase the colours of every earlier page.

    override fun writeString(text: String?, textPositions: List<TextPosition>?) {
        if (textPositions.isNullOrEmpty()) return
        val runs = mutableListOf<Run>()
        val buffer = StringBuilder()
        var curSize = -1f
        var curBold = false
        var curItalic = false
        var curColor = DEFAULT_COLOR
        var started = false

        fun flush() {
            if (buffer.isNotEmpty()) {
                runs.add(Run(buffer.toString(), curSize, curBold, curItalic, curColor))
                buffer.setLength(0)
            }
        }

        for (tp in textPositions) {
            val size = runCatching { tp.fontSizeInPt }.getOrDefault(0f)
            val font = runCatching { tp.font }.getOrNull()
            val fontName = runCatching { font?.name ?: "" }.getOrDefault("")
            val descriptor = runCatching { font?.fontDescriptor }.getOrNull()
            val bold = runCatching {
                fontName.contains("bold", true) ||
                    fontName.contains("black", true) ||
                    (descriptor?.isForceBold == true) ||
                    ((descriptor?.fontWeight ?: 0f) >= 600f)
            }.getOrDefault(false)
            val italic = runCatching {
                fontName.contains("italic", true) ||
                    fontName.contains("oblique", true) ||
                    (descriptor?.isItalic == true)
            }.getOrDefault(false)
            val color = colorByPosition[tp] ?: DEFAULT_COLOR

            if (!started) {
                curSize = size; curBold = bold; curItalic = italic; curColor = color; started = true
            } else if (size != curSize || bold != curBold || italic != curItalic || color != curColor) {
                flush()
                curSize = size; curBold = bold; curItalic = italic; curColor = color
            }
            val uni = runCatching { tp.unicode }.getOrNull()
            buffer.append(uni ?: "")
        }
        flush()
        if (runs.isNotEmpty()) lines.add(runs)
    }

    companion object {
        const val DEFAULT_COLOR = "#000000"
    }
}

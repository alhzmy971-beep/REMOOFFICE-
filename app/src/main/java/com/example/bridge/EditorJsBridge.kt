package com.example.bridge

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.example.model.EditorSelectionState
import com.example.model.MarginPreset
import com.example.model.PageConfig
import com.example.model.PageTheme
import org.json.JSONObject

class EditorJsBridge(
    private val onStateUpdated: (EditorSelectionState) -> Unit,
    private val onHtmlUpdated: (String) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onSelectionChanged(json: String) {
        try {
            val obj = JSONObject(json)
            val state = EditorSelectionState(
                isBold = obj.optBoolean("isBold", false),
                isItalic = obj.optBoolean("isItalic", false),
                isUnderline = obj.optBoolean("isUnderline", false),
                isStrikethrough = obj.optBoolean("isStrikethrough", false),
                isSuperscript = obj.optBoolean("isSuperscript", false),
                isSubscript = obj.optBoolean("isSubscript", false),
                fontName = obj.optString("fontName", "Arial"),
                fontSize = obj.optInt("fontSize", 14),
                foreColor = obj.optString("foreColor", "#1E293B"),
                backColor = obj.optString("backColor", ""),
                alignment = obj.optString("alignment", "left"),
                lineHeight = obj.optString("lineHeight", "1.5"),
                isBulletList = obj.optBoolean("isBulletList", false),
                isNumberList = obj.optBoolean("isNumberList", false),
                inTable = obj.optBoolean("inTable", false),
                formatPainterActive = obj.optBoolean("formatPainterActive", false),
                wordCount = obj.optInt("wordCount", 0),
                charCount = obj.optInt("charCount", 0),
                paragraphCount = obj.optInt("paragraphCount", 0)
            )
            mainHandler.post { onStateUpdated(state) }
        } catch (_: Exception) {
        }
    }

    @JavascriptInterface
    fun onContentChanged(html: String) {
        mainHandler.post { onHtmlUpdated(html) }
    }
}

object EditorCommands {
    private fun eval(webView: WebView?, script: String) {
        webView?.post {
            webView.evaluateJavascript(script, null)
        }
    }

    fun undo(webView: WebView?) = eval(webView, "window.editorApi.undo()")
    fun redo(webView: WebView?) = eval(webView, "window.editorApi.redo()")
    fun toggleBold(webView: WebView?) = eval(webView, "window.editorApi.toggleBold()")
    fun toggleItalic(webView: WebView?) = eval(webView, "window.editorApi.toggleItalic()")
    fun toggleUnderline(webView: WebView?) = eval(webView, "window.editorApi.toggleUnderline()")
    fun toggleStrikethrough(webView: WebView?) = eval(webView, "window.editorApi.toggleStrikethrough()")
    fun toggleSuperscript(webView: WebView?) = eval(webView, "window.editorApi.toggleSuperscript()")
    fun toggleSubscript(webView: WebView?) = eval(webView, "window.editorApi.toggleSubscript()")
    fun clearFormatting(webView: WebView?) = eval(webView, "window.editorApi.clearFormatting()")
    fun toggleFormatPainter(webView: WebView?) = eval(webView, "window.editorApi.toggleFormatPainter()")
    fun setHeading(webView: WebView?, tag: String) = eval(webView, "window.editorApi.setHeading('$tag')")
    fun setDirection(webView: WebView?, dir: String) = eval(webView, "window.editorApi.setDirection('$dir')")

    fun setFontFamily(webView: WebView?, font: String) =
        eval(webView, "window.editorApi.setFontFamily('${font.replace("'", "\\'")}')")

    fun setFontSize(webView: WebView?, pt: Int) =
        eval(webView, "window.editorApi.setFontSize($pt)")

    fun increaseFontSize(webView: WebView?) =
        eval(webView, "window.editorApi.increaseFontSize()")

    fun decreaseFontSize(webView: WebView?) =
        eval(webView, "window.editorApi.decreaseFontSize()")

    fun setForeColor(webView: WebView?, hex: String) =
        eval(webView, "window.editorApi.setForeColor('$hex')")

    fun setBackColor(webView: WebView?, hex: String) =
        eval(webView, "window.editorApi.setBackColor('$hex')")

    fun setAlignment(webView: WebView?, align: String) =
        eval(webView, "window.editorApi.setAlignment('$align')")

    fun setLineHeight(webView: WebView?, lh: String) =
        eval(webView, "window.editorApi.setLineHeight('$lh')")

    fun toggleBulletList(webView: WebView?) =
        eval(webView, "window.editorApi.toggleBulletList()")

    fun toggleNumberList(webView: WebView?) =
        eval(webView, "window.editorApi.toggleNumberList()")

    fun indent(webView: WebView?) = eval(webView, "window.editorApi.indent()")
    fun outdent(webView: WebView?) = eval(webView, "window.editorApi.outdent()")

    fun insertTable(webView: WebView?, rows: Int, cols: Int) =
        eval(webView, "window.editorApi.insertTable($rows, $cols)")

    fun addTableRow(webView: WebView?) = eval(webView, "window.editorApi.addTableRow()")
    fun addTableCol(webView: WebView?) = eval(webView, "window.editorApi.addTableCol()")
    fun deleteTableRow(webView: WebView?) = eval(webView, "window.editorApi.deleteTableRow()")
    fun deleteTableCol(webView: WebView?) = eval(webView, "window.editorApi.deleteTableCol()")
    fun mergeTableCells(webView: WebView?) = eval(webView, "window.editorApi.mergeTableCells()")
    fun setCellBackground(webView: WebView?, hex: String) =
        eval(webView, "window.editorApi.setCellBackground('$hex')")

    fun insertImage(webView: WebView?, dataUrl: String, widthPercent: Int = 80) {
        val escaped = JSONObject.quote(dataUrl)
        eval(webView, "window.editorApi.insertImage($escaped, $widthPercent)")
    }

    fun insertShape(webView: WebView?, shapeType: String) =
        eval(webView, "window.editorApi.insertShape('$shapeType')")

    fun insertTextBox(webView: WebView?, text: String) {
        val escaped = JSONObject.quote(text)
        eval(webView, "window.editorApi.insertTextBox($escaped)")
    }

    fun insertHyperlink(webView: WebView?, url: String, text: String) {
        val u = JSONObject.quote(url)
        val t = JSONObject.quote(text)
        eval(webView, "window.editorApi.insertHyperlink($u, $t)")
    }

    fun insertPageBreak(webView: WebView?) = eval(webView, "window.editorApi.insertPageBreak()")
    fun insertPageNumber(webView: WebView?) = eval(webView, "window.editorApi.insertPageNumber()")
    fun insertDateTime(webView: WebView?) = eval(webView, "window.editorApi.insertDateTime()")
    fun insertHorizontalRule(webView: WebView?) = eval(webView, "window.editorApi.insertHorizontalRule()")

    fun setPageConfig(webView: WebView?, config: PageConfig) {
        val marginName = when (config.marginPreset) {
            MarginPreset.NORMAL -> "Normal"
            MarginPreset.NARROW -> "Narrow"
            MarginPreset.WIDE -> "Wide"
            MarginPreset.CUSTOM -> "Custom"
        }
        val orientName = if (config.orientation.name == "LANDSCAPE") "Landscape" else "Portrait"
        eval(
            webView,
            "window.editorApi.setPageConfig('${config.size.label}', '$orientName', '$marginName', ${config.customMarginMm}, ${config.columns})"
        )
    }

    fun setPageBackground(webView: WebView?, theme: PageTheme) =
        eval(webView, "window.editorApi.setPageBackground('${theme.bgHex}', '${theme.textHex}')")

    fun setZoom(webView: WebView?, scale: Float) =
        eval(webView, "window.editorApi.setZoom($scale)")

    fun findAndReplace(webView: WebView?, find: String, replace: String, replaceAll: Boolean) {
        val f = JSONObject.quote(find)
        val r = JSONObject.quote(replace)
        eval(webView, "window.editorApi.findAndReplace($f, $r, $replaceAll)")
    }

    fun setReadMode(webView: WebView?, enabled: Boolean) =
        eval(webView, "window.editorApi.setReadMode($enabled)")

    fun insertHtml(webView: WebView?, html: String) {
        val escaped = JSONObject.quote(html)
        eval(webView, "window.editorApi.insertHtml($escaped)")
    }

    fun setHtml(webView: WebView?, html: String) {
        val escaped = JSONObject.quote(html)
        eval(webView, "window.editorApi.setHtml($escaped)")
    }
}

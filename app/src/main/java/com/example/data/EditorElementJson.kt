package com.example.data

import com.example.model.EditableGroup
import com.example.model.EditableImage
import com.example.model.EditableShape
import com.example.model.EditableTable
import com.example.model.EditableTableCell
import com.example.model.EditableTextBox
import com.example.model.ShapeStyle
import com.example.model.ShapeText
import com.example.model.ShapeTransform
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Serialization + HTML extraction for the structured editable element models
 * (tables, images, shapes, text boxes, groups).
 *
 * The JavaScript layer (editor_advanced.js) exposes the same JSON shape through
 * `window.editorApi.getEditorModel()`, so the payload, the repository and the
 * editor stay in sync. HTML extraction is used as a recovery path when an old
 * document was saved without the structured arrays.
 */
object EditorElementJson {

    // ------------------------------------------------------------------
    // Tables
    // ------------------------------------------------------------------
    fun tableToJson(t: EditableTable): JSONObject = JSONObject().apply {
        put("id", t.id)
        put("rows", t.rows)
        put("cols", t.cols)
        put("colWidths", JSONArray().apply { t.colWidths.forEach { put(it ?: JSONObject.NULL) } })
        put("rowHeights", JSONArray().apply { t.rowHeights.forEach { put(it ?: JSONObject.NULL) } })
        put("headerRepeat", t.headerRepeat)
        put("tableStyle", t.tableStyle)
        put("cells", JSONArray().apply {
            t.cells.forEach { c ->
                put(JSONObject().apply {
                    put("r", c.row); put("c", c.col)
                    put("rowspan", c.rowspan); put("colspan", c.colspan)
                    put("html", c.html); put("style", c.style); put("tag", c.tag)
                })
            }
        })
    }

    fun tableFromJson(o: JSONObject): EditableTable {
        val colWidths = mutableListOf<Float?>()
        o.optJSONArray("colWidths")?.let { a ->
            for (i in 0 until a.length()) {
                if (a.isNull(i)) colWidths.add(null) else colWidths.add(a.optDouble(i).toFloat())
            }
        }
        val rowHeights = mutableListOf<Float?>()
        o.optJSONArray("rowHeights")?.let { a ->
            for (i in 0 until a.length()) {
                if (a.isNull(i)) rowHeights.add(null) else rowHeights.add(a.optDouble(i).toFloat())
            }
        }
        val cells = mutableListOf<EditableTableCell>()
        o.optJSONArray("cells")?.let { a ->
            for (i in 0 until a.length()) {
                val c = a.optJSONObject(i) ?: continue
                cells.add(
                    EditableTableCell(
                        row = c.optInt("r", 0),
                        col = c.optInt("c", 0),
                        rowspan = c.optInt("rowspan", 1),
                        colspan = c.optInt("colspan", 1),
                        html = c.optString("html", "<br>"),
                        style = c.optString("style", ""),
                        tag = c.optString("tag", "td")
                    )
                )
            }
        }
        return EditableTable(
            id = o.optString("id", "tbl"),
            rows = o.optInt("rows", 0),
            cols = o.optInt("cols", 0),
            colWidths = colWidths,
            rowHeights = rowHeights,
            headerRepeat = o.optBoolean("headerRepeat", false),
            tableStyle = o.optString("tableStyle", ""),
            cells = cells
        )
    }

    // ------------------------------------------------------------------
    // Images
    // ------------------------------------------------------------------
    fun imageToJson(img: EditableImage): JSONObject = JSONObject().apply {
        put("id", img.id)
        put("src", img.src)
        put("width", img.width ?: JSONObject.NULL)
        put("height", img.height ?: JSONObject.NULL)
        put("x", img.x); put("y", img.y)
        put("rotate", img.rotate)
        put("flipH", img.flipH); put("flipV", img.flipV)
        put("crop", JSONObject().apply {
            put("t", img.cropTop); put("r", img.cropRight)
            put("b", img.cropBottom); put("l", img.cropLeft)
        })
        put("wrap", img.wrap); put("align", img.align)
        put("opacity", img.opacity)
        put("borderWidth", img.borderWidth)
        put("borderColor", img.borderColor)
        put("radius", img.radius)
        put("z", img.z)
    }

    fun imageFromJson(o: JSONObject): EditableImage {
        val crop = o.optJSONObject("crop")
        return EditableImage(
            id = o.optString("id", "img"),
            src = o.optString("src", ""),
            width = if (o.isNull("width")) null else o.optDouble("width").toFloat(),
            height = if (o.isNull("height")) null else o.optDouble("height").toFloat(),
            x = o.optDouble("x", 0.0).toFloat(),
            y = o.optDouble("y", 0.0).toFloat(),
            rotate = o.optDouble("rotate", 0.0).toFloat(),
            flipH = o.optBoolean("flipH", false),
            flipV = o.optBoolean("flipV", false),
            cropTop = crop?.optDouble("t", 0.0)?.toFloat() ?: 0f,
            cropRight = crop?.optDouble("r", 0.0)?.toFloat() ?: 0f,
            cropBottom = crop?.optDouble("b", 0.0)?.toFloat() ?: 0f,
            cropLeft = crop?.optDouble("l", 0.0)?.toFloat() ?: 0f,
            wrap = o.optString("wrap", "inline"),
            align = o.optString("align", "center"),
            opacity = o.optDouble("opacity", 1.0).toFloat(),
            borderWidth = o.optDouble("borderWidth", 0.0).toFloat(),
            borderColor = o.optString("borderColor", "#000000"),
            radius = o.optDouble("radius", 0.0).toFloat(),
            z = o.optInt("z", 1)
        )
    }

    // ------------------------------------------------------------------
    // Shapes / text boxes / groups
    // ------------------------------------------------------------------
    private fun transformToJson(t: ShapeTransform) = JSONObject().apply {
        put("x", t.x); put("y", t.y); put("w", t.w); put("h", t.h)
        put("rotation", t.rotation); put("flipH", t.flipH); put("flipV", t.flipV)
    }

    private fun transformFromJson(o: JSONObject?) = ShapeTransform(
        x = o?.optDouble("x", 0.0)?.toFloat() ?: 0f,
        y = o?.optDouble("y", 0.0)?.toFloat() ?: 0f,
        w = o?.optDouble("w", 160.0)?.toFloat() ?: 160f,
        h = o?.optDouble("h", 90.0)?.toFloat() ?: 90f,
        rotation = o?.optDouble("rotation", 0.0)?.toFloat() ?: 0f,
        flipH = o?.optBoolean("flipH", false) ?: false,
        flipV = o?.optBoolean("flipV", false) ?: false
    )

    private fun styleToJson(s: ShapeStyle) = JSONObject().apply {
        put("fill", s.fill); put("stroke", s.stroke)
        put("strokeWidth", s.strokeWidth); put("strokeStyle", s.strokeStyle)
        put("opacity", s.opacity); put("radius", s.radius)
    }

    private fun styleFromJson(o: JSONObject?) = ShapeStyle(
        fill = o?.optString("fill", "#DBEAFE") ?: "#DBEAFE",
        stroke = o?.optString("stroke", "#1A56DB") ?: "#1A56DB",
        strokeWidth = o?.optDouble("strokeWidth", 2.0)?.toFloat() ?: 2f,
        strokeStyle = o?.optString("strokeStyle", "solid") ?: "solid",
        opacity = o?.optDouble("opacity", 1.0)?.toFloat() ?: 1f,
        radius = o?.optDouble("radius", 6.0)?.toFloat() ?: 6f
    )

    private fun textToJson(t: ShapeText) = JSONObject().apply {
        put("content", t.content); put("fontFamily", t.fontFamily)
        put("fontSize", t.fontSize); put("color", t.color)
        put("align", t.align); put("dir", t.dir)
        put("bold", t.bold); put("italic", t.italic); put("underline", t.underline)
    }

    private fun textFromJson(o: JSONObject?) = ShapeText(
        content = o?.optString("content", "") ?: "",
        fontFamily = o?.optString("fontFamily", "Noto Sans Arabic") ?: "Noto Sans Arabic",
        fontSize = o?.optDouble("fontSize", 14.0)?.toFloat() ?: 14f,
        color = o?.optString("color", "#0F172A") ?: "#0F172A",
        align = o?.optString("align", "center") ?: "center",
        dir = o?.optString("dir", "rtl") ?: "rtl",
        bold = o?.optBoolean("bold", false) ?: false,
        italic = o?.optBoolean("italic", false) ?: false,
        underline = o?.optBoolean("underline", false) ?: false
    )

    fun shapeToJson(s: EditableShape): JSONObject = JSONObject().apply {
        put("id", s.id); put("type", s.type)
        put("transform", transformToJson(s.transform))
        put("style", styleToJson(s.style))
        put("text", textToJson(s.text))
        put("z", s.z); put("wrap", s.wrap)
        s.groupId?.let { put("groupId", it) }
        put("children", JSONArray().apply { s.children.forEach { put(shapeToJson(it)) } })
        s.originalXml?.let { put("originalXml", it) }
        s.originalPartName?.let { put("originalPartName", it) }
    }

    fun shapeFromJson(o: JSONObject): EditableShape {
        val children = mutableListOf<EditableShape>()
        o.optJSONArray("children")?.let { a ->
            for (i in 0 until a.length()) a.optJSONObject(i)?.let { children.add(shapeFromJson(it)) }
        }
        return EditableShape(
            id = o.optString("id", "shape"),
            type = o.optString("type", "rectangle"),
            transform = transformFromJson(o.optJSONObject("transform")),
            style = styleFromJson(o.optJSONObject("style")),
            text = textFromJson(o.optJSONObject("text")),
            z = o.optInt("z", 1),
            wrap = o.optString("wrap", "inline"),
            groupId = if (o.has("groupId")) o.optString("groupId") else null,
            children = children,
            originalXml = if (o.has("originalXml")) o.optString("originalXml") else null,
            originalPartName = if (o.has("originalPartName")) o.optString("originalPartName") else null
        )
    }

    fun textBoxToJson(t: EditableTextBox): JSONObject = JSONObject().apply {
        put("id", t.id)
        put("transform", transformToJson(t.transform))
        put("style", styleToJson(t.style))
        put("text", textToJson(t.text))
        put("z", t.z); put("wrap", t.wrap)
        t.groupId?.let { put("groupId", it) }
    }

    fun textBoxFromJson(o: JSONObject) = EditableTextBox(
        id = o.optString("id", "tb"),
        transform = transformFromJson(o.optJSONObject("transform")),
        style = styleFromJson(o.optJSONObject("style")),
        text = textFromJson(o.optJSONObject("text")),
        z = o.optInt("z", 1),
        wrap = o.optString("wrap", "inline"),
        groupId = if (o.has("groupId")) o.optString("groupId") else null
    )

    fun groupToJson(g: EditableGroup): JSONObject = JSONObject().apply {
        put("id", g.id)
        put("transform", transformToJson(g.transform))
        put("z", g.z)
        put("children", JSONArray().apply { g.children.forEach { put(shapeToJson(it)) } })
    }

    fun groupFromJson(o: JSONObject): EditableGroup {
        val children = mutableListOf<EditableShape>()
        o.optJSONArray("children")?.let { a ->
            for (i in 0 until a.length()) a.optJSONObject(i)?.let { children.add(shapeFromJson(it)) }
        }
        return EditableGroup(
            id = o.optString("id", "group"),
            transform = transformFromJson(o.optJSONObject("transform")),
            children = children,
            z = o.optInt("z", 1)
        )
    }

    // ------------------------------------------------------------------
    // HTML extraction (recovery path)
    // ------------------------------------------------------------------
    data class Extracted(
        val tables: List<EditableTable> = emptyList(),
        val images: List<EditableImage> = emptyList(),
        val shapes: List<EditableShape> = emptyList(),
        val textBoxes: List<EditableTextBox> = emptyList(),
        val groups: List<EditableGroup> = emptyList()
    )

    fun extractFromHtml(bodyHtml: String): Extracted {
        if (bodyHtml.isBlank()) return Extracted()
        val doc = runCatching { Jsoup.parse(bodyHtml) }.getOrNull() ?: return Extracted()

        val tables = doc.select("table").mapIndexedNotNull { idx, table ->
            runCatching { tableFromElement(table, idx) }.getOrNull()
        }
        val images = doc.select("img").mapIndexedNotNull { idx, img ->
            runCatching { imageFromElement(img, idx) }.getOrNull()
        }
        val shapes = doc.select(".editable-shape[data-shape-json]").mapIndexedNotNull { idx, el ->
            runCatching {
                val o = JSONObject(el.attr("data-shape-json"))
                shapeFromJson(o).copy(id = o.optString("id", "shape_$idx"))
            }.getOrNull()
        }
        val textBoxes = doc.select(".editable-textbox[data-shape-json]").mapIndexedNotNull { idx, el ->
            runCatching {
                val o = JSONObject(el.attr("data-shape-json"))
                textBoxFromJson(o).copy(id = o.optString("id", "tb_$idx"))
            }.getOrNull()
        }
        val groups = doc.select(".editable-group[data-group-json]").mapIndexedNotNull { _, el ->
            runCatching { groupFromJson(JSONObject(el.attr("data-group-json"))) }.getOrNull()
        }
        return Extracted(tables, images, shapes, textBoxes, groups)
    }

    /** Builds the descriptor grid so rowspan/colspan are preserved exactly. */
    private fun tableFromElement(table: Element, index: Int): EditableTable {
        val trs = table.select("tr")
        val grid = mutableListOf<MutableList<Pair<Int, Int>?>>() // origin coords
        val origins = mutableListOf<EditableTableCell>()
        val colWidths = mutableListOf<Float?>()
        table.select("colgroup col").forEach { col ->
            colWidths.add(parsePx(col.attr("style")))
        }
        val rowHeights = mutableListOf<Float?>()
        trs.forEachIndexed { r, tr ->
            while (grid.size <= r) grid.add(mutableListOf())
            rowHeights.add(parsePx(tr.attr("style")))
            var c = 0
            tr.select("> td, > th").forEach { cell ->
                while (c < grid[r].size && grid[r][c] != null) c++
                val rs = cell.attr("rowspan").toIntOrNull()?.coerceAtLeast(1) ?: 1
                val cs = cell.attr("colspan").toIntOrNull()?.coerceAtLeast(1) ?: 1
                origins.add(
                    EditableTableCell(
                        row = r, col = c, rowspan = rs, colspan = cs,
                        html = cell.html(), style = cell.attr("style"),
                        tag = cell.tagName().lowercase()
                    )
                )
                for (dr in 0 until rs) {
                    while (grid.size <= r + dr) grid.add(mutableListOf())
                    for (dc in 0 until cs) {
                        while (grid[r + dr].size <= c + dc) grid[r + dr].add(null)
                        grid[r + dr][c + dc] = r to c
                    }
                }
                c += cs
            }
        }
        val cols = grid.maxOfOrNull { it.size } ?: 0
        return EditableTable(
            id = table.attr("data-remo-table-id").ifBlank { "tbl_$index" },
            rows = grid.size,
            cols = cols,
            colWidths = colWidths,
            rowHeights = rowHeights,
            headerRepeat = table.attr("data-header-repeat") == "true" || table.select("thead").isNotEmpty(),
            tableStyle = table.attr("style"),
            cells = origins
        )
    }

    private fun imageFromElement(img: Element, index: Int): EditableImage {
        val base = runCatching { JSONObject(img.attr("data-remo-img")) }.getOrNull() ?: JSONObject()
        base.put("id", img.attr("data-remo-img-id").ifBlank { "img_$index" })
        base.put("src", img.attr("src"))
        if (!base.has("width")) parsePx(img.attr("style"))?.let { base.put("width", it) }
        return imageFromJson(base)
    }

    private fun parsePx(style: String): Float? {
        if (style.isBlank()) return null
        val m = Regex("(?:^|;)\\s*(?:width|height)\\s*:\\s*([0-9.]+)px").find(style) ?: return null
        return m.groupValues[1].toFloatOrNull()
    }
}

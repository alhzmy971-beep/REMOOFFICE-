package com.example.model

enum class EditorTab(val titleAr: String, val titleEn: String) {
    FILE("ملف", "File"),
    HOME("الرئيسية", "Home"),
    INSERT("إدراج", "Insert"),
    LAYOUT("تخطيط", "Layout"),
    REVIEW("مراجعة", "Review"),
    VIEW("عرض", "View")
}

enum class SortOrder(val labelAr: String) {
    DATE_DESC("الأحدث"),
    TITLE_ASC("الاسم"),
    TYPE_ASC("النوع")
}

enum class PageSize(val label: String, val widthMm: Int, val heightMm: Int) {
    A4("A4", 210, 297),
    LETTER("Letter", 216, 279),
    LEGAL("Legal", 216, 356),
    A5("A5", 148, 210)
}

enum class PageOrientation(val labelAr: String) {
    PORTRAIT("عمودي"),
    LANDSCAPE("أفقي")
}

enum class MarginPreset(val labelAr: String, val mm: Float) {
    NORMAL("عادية", 25.4f),
    NARROW("ضيقة", 12.7f),
    WIDE("واسعة", 38.1f),
    MIRRORED("معكوسة", 25.4f),
    CUSTOM("مخصصة", 20.0f)
}

enum class PageTheme(val labelAr: String, val bgHex: String, val textHex: String) {
    WHITE("أبيض", "#FFFFFF", "#1E293B"),
    SEPIA("مريح للعين", "#FAF3E0", "#3B2F2F"),
    SOFT_BLUE("أزرق هادئ", "#EFF6FF", "#1E293B"),
    SOFT_GREEN("أخضر هادئ", "#ECFDF5", "#064E3B"),
    DARK("ليلي", "#1E293B", "#F1F5F9")
}

data class PageConfig(
    val size: PageSize = PageSize.A4,
    val orientation: PageOrientation = PageOrientation.PORTRAIT,
    val marginPreset: MarginPreset = MarginPreset.NORMAL,
    val customMarginMm: Float = 20.0f,
    val columns: Int = 1,
    val pageTheme: PageTheme = PageTheme.WHITE,
    val watermarkText: String = ""
)

data class DocumentAnchor(
    val elementId: String = "",
    val startOffset: Int = 0,
    val endOffset: Int = 0,
    val quotedText: String = ""
)

data class CommentReply(
    val id: String,
    val author: String = "المستخدم",
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class DocumentComment(
    val id: String,
    val quote: String = "",
    val text: String = "",
    val author: String = "المستخدم",
    val timestamp: Long = System.currentTimeMillis(),
    val resolved: Boolean = false,
    val anchor: DocumentAnchor? = null,
    val replies: List<CommentReply> = emptyList()
)

data class TrackedChange(
    val id: String,
    val type: String, // "INSERT", "DELETE", "FORMAT"
    val elementId: String = "",
    val anchor: DocumentAnchor? = null,
    val oldText: String = "",
    val newText: String = "",
    val oldHtml: String = "",
    val newHtml: String = "",
    val author: String = "المستخدم",
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "PENDING", // "PENDING", "ACCEPTED", "REJECTED"
    val createdByUser: Boolean = true
)

data class DocumentProperties(
    val author: String = "المستخدم",
    val title: String = "",
    val subject: String = "",
    val keywords: String = "",
    val creationDate: Long = System.currentTimeMillis(),
    val modificationDate: Long = System.currentTimeMillis(),
    val revision: Int = 1
)

data class DocumentStyle(
    val name: String,
    val htmlTag: String = "p",
    val fontFamily: String = "Arial",
    val fontSizePt: Int = 14,
    val colorHex: String = "#1E293B",
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val alignment: String = "left",
    val dir: String = "rtl",
    val lineHeight: String = "1.5",
    val spaceBeforePt: Int = 0,
    val spaceAfterPt: Int = 0,
    val indentMm: Int = 0
)

data class DocumentBookmark(
    val id: String,
    val name: String,
    val anchor: DocumentAnchor? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class DocumentNote(
    val id: String,
    val type: String = "FOOTNOTE", // "FOOTNOTE" or "ENDNOTE"
    val text: String,
    val number: Int = 1,
    val anchor: DocumentAnchor? = null
)

data class DocumentImportReport(
    val paragraphCount: Int = 0,
    val tableCount: Int = 0,
    val imageCount: Int = 0,
    val smartArtCount: Int = 0,
    val chartCount: Int = 0,
    val fieldCount: Int = 0,
    val commentCount: Int = 0,
    val noteCount: Int = 0,
    val svgConvertedCount: Int = 0,
    val drawingMlConvertedCount: Int = 0,
    val imageFallbackCount: Int = 0,
    val unsupportedCount: Int = 0,
    val unsupportedReasons: List<String> = emptyList(),
    // PDF-specific, page-by-page diagnostics
    val pageCount: Int = 0,
    val directTextPages: Int = 0,
    val ocrPages: Int = 0,
    val failedOcrPages: Int = 0,
    val imageOnlyPages: Int = 0,
    val shapeCount: Int = 0,
    val linkCount: Int = 0,
    val ocrSuccessRate: Float = 0f,
    val confidence: String = "", // "high", "medium", "low"
    val failureReasons: List<String> = emptyList()
)

/** Geometry of an editable object on the page (CSS pixels, top-left origin). */
data class ShapeTransform(
    val x: Float = 0f,
    val y: Float = 0f,
    val w: Float = 160f,
    val h: Float = 90f,
    val rotation: Float = 0f,
    val flipH: Boolean = false,
    val flipV: Boolean = false
)

/** Visual formatting shared by shapes and text boxes. */
data class ShapeStyle(
    val fill: String = "#DBEAFE",
    val stroke: String = "#1A56DB",
    val strokeWidth: Float = 2f,
    val strokeStyle: String = "solid", // solid | dashed | dotted | double
    val opacity: Float = 1f,
    val radius: Float = 6f
)

/** Text run formatting inside a shape / text box. */
data class ShapeText(
    val content: String = "",
    val fontFamily: String = "Noto Sans Arabic",
    val fontSize: Float = 14f,
    val color: String = "#0F172A",
    val align: String = "center", // left | center | right | justify
    val dir: String = "rtl",      // rtl | ltr
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false
)

/**
 * Unified editable shape model. Covers native shapes, WordArt, DrawingML/VML
 * imports and composite/grouped shapes.
 */
data class EditableShape(
    val id: String,
    val type: String = "rectangle", // rectangle|circle|ellipse|line|arrow|star|callout|wordart|drawingml|vml|group
    val transform: ShapeTransform = ShapeTransform(),
    val style: ShapeStyle = ShapeStyle(),
    val text: ShapeText = ShapeText(),
    val z: Int = 1,
    val wrap: String = "inline", // inline|square|tight|front|behind
    val groupId: String? = null,
    val children: List<EditableShape> = emptyList(),
    val originalXml: String? = null,
    val originalPartName: String? = null
)

/** Real editable text box (a shape with type=textbox but its own model for clarity). */
data class EditableTextBox(
    val id: String,
    val transform: ShapeTransform = ShapeTransform(),
    val style: ShapeStyle = ShapeStyle(fill = "#F8FAFC", strokeWidth = 1.5f),
    val text: ShapeText = ShapeText(align = "right", dir = "rtl"),
    val z: Int = 1,
    val wrap: String = "inline",
    val groupId: String? = null
)

/** Group of shapes that can be moved / resized / ungrouped as one unit. */
data class EditableGroup(
    val id: String,
    val transform: ShapeTransform = ShapeTransform(),
    val children: List<EditableShape> = emptyList(),
    val z: Int = 1
)

/** One cell of an editable table, including span information. */
data class EditableTableCell(
    val row: Int = 0,
    val col: Int = 0,
    val rowspan: Int = 1,
    val colspan: Int = 1,
    val html: String = "<br>",
    val style: String = "",
    val tag: String = "td"
)

/** Editable table model mirroring the descriptor-grid used by editor_advanced.js. */
data class EditableTable(
    val id: String,
    val rows: Int = 0,
    val cols: Int = 0,
    val colWidths: List<Float?> = emptyList(),
    val rowHeights: List<Float?> = emptyList(),
    val headerRepeat: Boolean = false,
    val tableStyle: String = "",
    val cells: List<EditableTableCell> = emptyList()
)

/** Editable image model (transform + crop + wrap + border persisted as JSON). */
data class EditableImage(
    val id: String,
    val src: String = "",
    val width: Float? = null,
    val height: Float? = null,
    val x: Float = 0f,
    val y: Float = 0f,
    val rotate: Float = 0f,
    val flipH: Boolean = false,
    val flipV: Boolean = false,
    val cropTop: Float = 0f,
    val cropRight: Float = 0f,
    val cropBottom: Float = 0f,
    val cropLeft: Float = 0f,
    val wrap: String = "inline",
    val align: String = "center",
    val opacity: Float = 1f,
    val borderWidth: Float = 0f,
    val borderColor: String = "#000000",
    val radius: Float = 0f,
    val z: Int = 1
)

data class SmartArtNode(
    val id: String,
    val text: String,
    val level: Int = 0,
    val parentId: String? = null,
    val colorHex: String? = null,
    val fontName: String? = null,
    val fontSizePt: Int? = null,
    val modelId: String? = null,
    val connectionType: String? = null,
    val presId: String? = null,
    val order: Int = 0
)

data class SmartArtConnection(
    val modelId: String = "",
    val type: String = "parOf",
    val sourceId: String = "",
    val destId: String = "",
    val presId: String? = null,
    val sourceOrder: Int = 0,
    val destOrder: Int = 0
)

data class SmartArtData(
    val id: String,
    val layoutType: String = "process", // "process", "hierarchy", "cycle", "relationship", "list", "matrix", "pyramid"
    val title: String = "",
    val nodes: List<SmartArtNode> = emptyList(),
    val primaryColorHex: String = "#1A56DB",
    val accentColorHex: String = "#3B82F6",
    val direction: String = "rtl", // "rtl" or "ltr"
    val widthPx: Int = 500,
    val heightPx: Int = 260,
    val stylePreset: String = "flat",
    val originalXml: String? = null,
    val originalPartName: String? = null,
    val layoutPartName: String? = null,
    val stylePartName: String? = null,
    val colorPartName: String? = null,
    // Raw XML of the layout / quick-style / color parts, preserved so that
    // editing node text/colours in the app does NOT destroy the original
    // visual arrangement on export. The data part is always regenerated
    // from the model so user edits are never lost.
    val originalLayoutXml: String? = null,
    val originalStyleXml: String? = null,
    val originalColorsXml: String? = null,
    val connections: List<SmartArtConnection> = emptyList()
)

data class ChartSeries(
    val name: String,
    val values: List<Double> = emptyList(),
    val colorHex: String? = null
)

data class ChartData(
    val id: String,
    val type: String = "column", // "column", "bar", "line", "pie", "area", "scatter", "doughnut", "radar"
    val title: String = "",
    val categories: List<String> = emptyList(),
    val series: List<ChartSeries> = emptyList(),
    val showLegend: Boolean = true,
    val showGrid: Boolean = true,
    val direction: String = "rtl",
    val widthPx: Int = 460,
    val heightPx: Int = 280,
    val xAxisLabel: String = "",
    val yAxisLabel: String = "",
    val stackedType: String? = null, // "standard", "stacked", "percentStacked"
    val grouping: String? = null,
    val has3D: Boolean = false,
    val secondaryAxis: Boolean = false,
    val legendPosition: String = "b", // "r", "l", "t", "b"
    val numberFormat: String? = null,
    val originalXml: String? = null,
    val originalPartName: String? = null
) {
    val chartType: String get() = type
}

data class DocumentPayload(
    val bodyHtml: String = "<p><br></p>",
    val headerHtml: String = "",
    val footerHtml: String = "",
    val pageConfig: PageConfig = PageConfig(),
    val comments: List<DocumentComment> = emptyList(),
    val trackedChanges: List<TrackedChange> = emptyList(),
    val styles: List<DocumentStyle> = emptyList(),
    val bookmarks: List<DocumentBookmark> = emptyList(),
    val notes: List<DocumentNote> = emptyList(),
    val properties: DocumentProperties = DocumentProperties(),
    val smartArts: List<SmartArtData> = emptyList(),
    val charts: List<ChartData> = emptyList(),
    val tables: List<EditableTable> = emptyList(),
    val images: List<EditableImage> = emptyList(),
    val shapes: List<EditableShape> = emptyList(),
    val textBoxes: List<EditableTextBox> = emptyList(),
    val groups: List<EditableGroup> = emptyList(),
    val importReport: DocumentImportReport? = null
)

data class EditorSelectionState(
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val isStrikethrough: Boolean = false,
    val isSuperscript: Boolean = false,
    val isSubscript: Boolean = false,
    val fontName: String = "Arial",
    val fontSize: Int = 14,
    val foreColor: String = "#1E293B",
    val backColor: String = "",
    val alignment: String = "left",
    val lineHeight: String = "1.5",
    val isBulletList: Boolean = false,
    val isNumberList: Boolean = false,
    val inTable: Boolean = false,
    val inImage: Boolean = false,
    val inSmartArt: Boolean = false,
    val selectedSmartArtId: String = "",
    val inChart: Boolean = false,
    val selectedChartId: String = "",
    val formatPainterActive: Boolean = false,
    val wordCount: Int = 0,
    val charCount: Int = 0,
    val paragraphCount: Int = 0,
    val pageCount: Int = 1,
    val selectedText: String = ""
)

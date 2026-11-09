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
    val noteCount: Int = 0,
    val svgConvertedCount: Int = 0,
    val imageFallbackCount: Int = 0,
    val unsupportedCount: Int = 0,
    val unsupportedReasons: List<String> = emptyList()
)

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
    val formatPainterActive: Boolean = false,
    val wordCount: Int = 0,
    val charCount: Int = 0,
    val paragraphCount: Int = 0,
    val pageCount: Int = 1,
    val selectedText: String = ""
)

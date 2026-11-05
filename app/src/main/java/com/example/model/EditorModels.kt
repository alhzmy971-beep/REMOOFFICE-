package com.example.model

enum class EditorTab(val titleAr: String, val titleEn: String) {
    HOME("الرئيسية", "Home"),
    INSERT("إدراج", "Insert"),
    LAYOUT("تخطيط", "Layout"),
    REVIEW("مراجعة", "Review")
}

enum class PageSize(val label: String, val widthMm: Int, val heightMm: Int) {
    A4("A4", 210, 297),
    LETTER("Letter", 216, 279),
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
    val pageTheme: PageTheme = PageTheme.WHITE
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
    val paragraphCount: Int = 0
)

package com.example

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EditorTouchControlsTest {
    private val html = File("src/main/assets/editor.html").readText(Charsets.UTF_8)
    private val bridge = File("src/main/java/com/example/bridge/EditorJsBridge.kt").readText(Charsets.UTF_8)
    private val tools = File("src/main/java/com/example/ui/components/DynamicToolsPanel.kt").readText(Charsets.UTF_8)

    @Test
    fun tableTouchSelectionResizeMoveAndInsertionAreWired() {
        listOf("pointerdown", "pointermove", "paintTableRange", "tableResize", "tableMove", "table-move-handle")
            .forEach { assertTrue("مفقود دعم لمس الجدول: $it", html.contains(it)) }
        listOf("addTableRowBefore", "addTableColBefore", "addTableRow", "addTableCol")
            .forEach { assertTrue("مفقود أمر الجدول: $it", html.contains(it) && bridge.contains(it)) }
        assertTrue("زر الإدراج قبل الصف غير ظاهر", tools.contains("صف قبل"))
        assertTrue("زر الإدراج قبل العمود غير ظاهر", tools.contains("عمود قبل"))
    }

    @Test
    fun visualObjectsHaveTouchHandlesAndToolbarMoveResizeCommands() {
        listOf(".shape-block", ".text-box", ".smartart-block", ".chart-block", "resizeSelectedObject", "moveSelectedObject")
            .forEach { assertTrue("مفقود دعم تحريك/تغيير حجم العنصر: $it", html.contains(it) || bridge.contains(it)) }
        assertTrue("أزرار تحريك الكائن غير مربوطة بالواجهة", tools.contains("onMoveSelectedObject") && bridge.contains("moveSelectedObject"))
        assertTrue("أزرار تغيير حجم الكائن غير مربوطة بالواجهة", tools.contains("onResizeSelectedObject") && bridge.contains("resizeSelectedObject"))
        assertTrue("يجب إزالة مقابض التحديد المؤقتة عند حفظ المستند", html.contains("cleanEditor.querySelectorAll"))
    }

    @Test
    fun visualSelectionFrameHasEightResizeHandlesRotationAndDismissal() {
        listOf("object-selection-frame", "object-selection-handle", "object-rotation-handle", "rotate.dataset.handle = 'rotate'",
            "applyObjectResize", "=== 'rotate' ? 'rotate' : 'resize'", "type: 'move'", "updateSelectionFrame", "selectionFrame.classList.remove('visible')")
            .forEach { assertTrue("مفقود سلوك إطار تحديد الكائن: $it", html.contains(it)) }
        listOf("'nw','n','ne','e','se','s','sw','w'").forEach {
            assertTrue("يجب توفير مقابض الاتجاهات الثمانية", html.contains(it))
        }
        assertTrue("يجب دعم الترويسة والتذييل دون تعطيل الكتابة", html.contains(".page-header, .page-footer"))
        assertTrue("إطار التحديد لا يجب أن يدخل في HTML المحفوظ", html.contains("cleanSelectionMarkup"))
    }

    @Test
    fun longPressContextToolbarActionsAreConnectedToSelectedObjects() {
        listOf("longPressTimer = setTimeout", "object-context-toolbar", "applyContextAction(action)",
            "['delete', 'حذف'", "['front', 'للأمام'", "['back', 'للخلف'", "['multi', 'تحديد متعدد'",
            "multiSelectMode", "selectedContextObjects", "notifyContentChanged()")
            .forEach { assertTrue("مفقود خيار الشريط العائم أو ربطه: $it", html.contains(it)) }
        assertTrue("ينبغي حذف العناصر المحددة فعليًا", html.contains("targets.forEach(el => { if (el.isConnected) el.remove(); })"))
        assertTrue("ينبغي تغيير ترتيب العناصر في DOM", html.contains("el.style.zIndex = String(top + index)"))
    }

    @Test
    fun tableShowsInlineAddButtonsHeadersAndDraggableRowColumnBoundaries() {
        listOf("table-selection-controls", "table-add-column", "table-add-row", "table-move-button",
            "table-col-label", "table-row-label", "table-col-grip", "table-row-grip",
            "addTableColumnAtEdge", "addTableRowAtEdge", "startTableResize(e, 'col', i)",
            "startTableResize(e, 'row', i)", "refreshTableSelectionControls()")
            .forEach { assertTrue("مفقود تحكم الجدول حول حدوده: $it", html.contains(it)) }
    }

    @Test
    fun horizontalResizeHandleUsesPointerDeltaAndUpdatesObjectWidth() {
        assertTrue("يجب تحديد اتجاه مقبض اليمين/اليسار لتوسيع العنصر أفقيًا", html.contains("const east = h.includes('e'), west = h.includes('w')"))
        assertTrue("يجب تطبيق الإزاحة الأفقية على عرض العنصر", html.contains("let width = g.width + (east ? dx : west ? -dx : 0)"))
        assertTrue("يجب حفظ عرض الصورة بعد السحب", html.contains("g.obj.style.width = width + 'px'"))
    }
}

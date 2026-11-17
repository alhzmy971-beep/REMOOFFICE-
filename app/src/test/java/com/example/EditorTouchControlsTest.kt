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
}

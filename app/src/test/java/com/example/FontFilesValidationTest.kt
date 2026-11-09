package com.example

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FontFilesValidationTest {

    @Test
    fun bundledFontFiles_existAndAreNonEmpty() {
        val fontsDir = File("src/main/assets/fonts")
        assertTrue("مجلد الخطوط المحلية يجب أن يكون موجوداً", fontsDir.exists() && fontsDir.isDirectory)

        val arabicFont = File(fontsDir, "NotoSansArabic-VF.ttf")
        assertTrue("ملف خط NotoSansArabic-VF.ttf يجب أن يكون موجوداً", arabicFont.exists())
        assertTrue("حجم خط NotoSansArabic-VF.ttf يجب أن يكون أكبر من 500 كيلوبايت", arabicFont.length() > 500_000)

        val sansFont = File(fontsDir, "NotoSans-VF.ttf")
        assertTrue("ملف خط NotoSans-VF.ttf يجب أن يكون موجوداً", sansFont.exists())
        assertTrue("حجم خط NotoSans-VF.ttf يجب أن يكون أكبر من 1 ميجابايت", sansFont.length() > 1_000_000)

        val serifFont = File(fontsDir, "NotoSerif-VF.ttf")
        assertTrue("ملف خط NotoSerif-VF.ttf يجب أن يكون موجوداً", serifFont.exists())
        assertTrue("حجم خط NotoSerif-VF.ttf يجب أن يكون أكبر من 1 ميجابايت", serifFont.length() > 1_000_000)
    }

    @Test
    fun editorHtml_declaresLocalFontFaceRules() {
        val editorHtml = File("src/main/assets/editor.html")
        assertTrue("ملف editor.html يجب أن يكون موجوداً", editorHtml.exists())
        val content = editorHtml.readText(Charsets.UTF_8)

        assertTrue(content.contains("@font-face"))
        assertTrue(content.contains("fonts/NotoSansArabic-VF.ttf"))
        assertTrue(content.contains("fonts/NotoSans-VF.ttf"))
        assertTrue(content.contains("fonts/NotoSerif-VF.ttf"))
        assertTrue(content.contains("lastValidSelectionRange"))
        assertFalse("يجب عدم الاعتماد على شبكات خارجية للخطوط", content.contains("fonts.googleapis.com"))
    }
}

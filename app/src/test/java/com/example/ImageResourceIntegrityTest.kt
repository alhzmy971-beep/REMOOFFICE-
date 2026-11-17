package com.example

import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImageResourceIntegrityTest {
    private data class ImageResource(
        val id: Int,
        val name: String,
        val signature: ByteArray
    )

    @Test
    fun `all app drawable raster images have valid headers and decode`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val images = listOf(
            ImageResource(
                R.drawable.splash_screen_img,
                "splash_screen_img.png",
                byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
            ),
            ImageResource(
                R.drawable.ic_launcher_logo,
                "ic_launcher_logo.png",
                byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
            ),
            ImageResource(
                R.drawable.ic_launcher_custom_1794409971078,
                "ic_launcher_custom_1794409971078.jpg",
                byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
            ),
            ImageResource(
                R.drawable.jeeb_support_qr,
                "jeeb_support_qr.jpg",
                byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
            )
        )

        images.forEach { image ->
            val header = ByteArray(image.signature.size)
            context.resources.openRawResource(image.id).use { stream ->
                var offset = 0
                while (offset < header.size) {
                    val count = stream.read(header, offset, header.size - offset)
                    assertTrue("${image.name} ended before its image signature", count > 0)
                    offset += count
                }
            }
            assertArrayEquals("${image.name} has an invalid image signature", image.signature, header)

            val bitmap = requireNotNull(BitmapFactory.decodeResource(context.resources, image.id)) {
                "${image.name} could not be decoded"
            }
            assertTrue("${image.name} decoded with invalid dimensions", bitmap.width > 0 && bitmap.height > 0)
            bitmap.recycle()
        }
    }
}

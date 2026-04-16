package com.hearthappy.logs

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.hearthappy.log.core.ImageLogCodec
import com.hearthappy.log.core.ImageCompressionOptions
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class ImageLogCodecUnitTest {

    @Test
    fun encode_shouldGenerateThumbnailAndPayload() {
        val bytes = fakeBitmapBytes(256, 256)
        val result = ImageLogCodec.encode(bytes)
        assertNotNull(result)
        assertTrue(!result!!.thumbnailBase64.isBlank())
        assertTrue(result.compressedBytes > 0)
        assertTrue(result.compressedImage.isNotEmpty())
    }

    @Test
    fun encode_shouldShrinkWhenLarge() {
        val bytes = fakeBitmapBytes(2048, 2048)
        val result = ImageLogCodec.encode(
            bytes,
            options = ImageCompressionOptions(targetSizeKb = 200, minSidePx = 512)
        )
        assertNotNull(result)
        val target = 200 * 1024
        val tolerance = (target * 0.05).toInt()
        assertTrue(kotlin.math.abs(result!!.compressedBytes - target) <= tolerance || result.compressedBytes <= target)
        assertTrue(result.width >= 512 || result.height >= 512)
    }

    @Test
    fun encode_shouldUseLosslessMimeAndRespectSizeCap() {
        val bytes = fakeBitmapBytes(1024, 1024)
        val result = ImageLogCodec.encode(
            bytes,
            options = ImageCompressionOptions(targetSizeKb = 180, minSidePx = 512)
        )
        assertNotNull(result)
        assertTrue(result!!.mediaType == "image/webp")
        assertTrue(result.compressedBytes <= ImageLogCodec.MAX_INPUT_BYTES)
    }

    private fun fakeBitmapBytes(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(Color.BLUE)
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        return output.toByteArray()
    }
}

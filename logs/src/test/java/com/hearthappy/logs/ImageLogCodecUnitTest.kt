package com.hearthappy.logs

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.hearthappy.log.core.ImageLogCodec
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class ImageLogCodecUnitTest {

    @Test
    fun encode_shouldGenerateThumbnailAndPayload() {
        val bytes = fakeBitmapBytes(256, 256)
        val result = ImageLogCodec.encode(bytes, quality = 70)
        assertNotNull(result)
        assertTrue(!result!!.thumbnailBase64.isBlank())
        assertTrue(result.compressedBytes > 0)
    }

    @Test
    fun encode_shouldChunkWhenLarge() {
        val bytes = fakeBitmapBytes(2048, 2048)
        val result = ImageLogCodec.encode(bytes, quality = 60)
        assertNotNull(result)
        assertTrue(result!!.chunked || !result.base64Payload.isNullOrBlank())
    }

    @Test
    fun encode_shouldUseLosslessMimeAndRespectSizeCap() {
        val bytes = fakeBitmapBytes(1024, 1024)
        val result = ImageLogCodec.encode(bytes, quality = 80)
        assertNotNull(result)
        assertTrue(result!!.mimeType == "image/webp" || result.mimeType == "image/png")
        assertTrue(result.compressedBytes <= ImageLogCodec.MAX_COMPRESSED_BYTES)
    }

    private fun fakeBitmapBytes(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(Color.BLUE)
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        return output.toByteArray()
    }
}

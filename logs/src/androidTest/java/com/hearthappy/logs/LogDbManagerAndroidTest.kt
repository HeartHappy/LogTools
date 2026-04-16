package com.hearthappy.logs

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hearthappy.log.LoggerX
import com.hearthappy.log.db.LogDbManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class LogDbManagerAndroidTest {

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        LoggerX.init(context)
        LoggerX.clear()
    }

    @Test
    fun distinctValues_shouldReturnEmpty_whenTableNotExists() {
        val values = LogDbManager.getDistinctValues("NeverWrittenScope", LoggerX.COLUMN_TAG)
        assertTrue(values.isEmpty())
    }

    @Test
    fun queryLogs_shouldReturnEmpty_whenTableNotExists() {
        val logs = LogDbManager.queryLogsAdvanced(scopeTag = "NeverWrittenScope")
        assertTrue(logs.isEmpty())
    }

    @Test
    fun insertThenDistinct_shouldWork_afterTableCreated() {
        val scope = "RetryScope"
        LogDbManager.insertLog(scope, "DEBUG", "Main", "m()", "msg")
        val levels = LogDbManager.getDistinctValues(scope, LoggerX.COLUMN_LEVEL)
        assertEquals(listOf("DEBUG"), levels)
    }

    @Test
    fun imageLog_shouldSupportJpegPngGifWebpInput() {
        val proxy = LoggerX.createScope("ImageScope")
        val bytes = fakeBitmapBytes(320, 320, Bitmap.CompressFormat.PNG)
        assertTrue(proxy.image(bytes, "image/jpeg"))
        assertTrue(proxy.image(bytes, "image/png"))
        assertTrue(proxy.image(bytes, "image/gif"))
        assertTrue(proxy.image(bytes, "image/webp"))
        val logs = proxy.queryLogs(limit = 20)
        assertTrue(logs.any { it[LoggerX.COLUMN_IS_IMAGE]?.toString() == "1" })
    }

    @Test
    fun imageLog_shouldLoadPreviewData() {
        val scope = "PreviewScope"
        val proxy = LoggerX.createScope(scope)
        val payload = fakeBitmapBytes(720, 480, Bitmap.CompressFormat.JPEG)
        assertTrue(proxy.image(payload, "image/jpeg"))
        val id = LogDbManager.queryLogsAdvanced(scope, limit = 1).first()[LoggerX.COLUMN_ID].toString().toInt()
        val loaded = proxy.loadImagePreviewData(id)
        assertNotNull(loaded)
        assertTrue(!loaded!!.compressedBase64.isNullOrBlank())
    }

    @Test
    fun imageLog_shouldHandle1000RowsPerformance() {
        val proxy = LoggerX.createScope("PressureScope")
        val bytes = fakeBitmapBytes(128, 128, Bitmap.CompressFormat.JPEG)
        val start = System.currentTimeMillis()
        repeat(1000) {
            proxy.image(bytes, "image/jpeg", message = "img-$it")
        }
        val writeElapsed = System.currentTimeMillis() - start
        val loadStart = System.currentTimeMillis()
        val logs = proxy.queryLogs(limit = 1000)
        val loadElapsed = System.currentTimeMillis() - loadStart
        assertEquals(1000, logs.size)
        assertTrue(writeElapsed < 120_000)
        assertTrue(loadElapsed < 5_000)
    }

    @Test
    fun imageLog_shouldGenerateThumbnail() {
        val proxy = LoggerX.createScope("ThumbScope")
        val bytes = fakeBitmapBytes(256, 256, Bitmap.CompressFormat.JPEG)
        assertTrue(proxy.image(bytes, "image/jpeg"))
        val log = proxy.queryLogs(limit = 1).first()
        val thumb = log[LoggerX.COLUMN_THUMBNAIL]?.toString()
        assertTrue(!thumb.isNullOrBlank())
        val decoded = Base64.decode(thumb, Base64.DEFAULT)
        val bitmap = BitmapFactory.decodeByteArray(decoded, 0, decoded.size)
        assertNotNull(bitmap)
    }

    private fun fakeBitmapBytes(width: Int, height: Int, format: Bitmap.CompressFormat): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.RED)
        }
        val out = ByteArrayOutputStream()
        bitmap.compress(format, 90, out)
        return out.toByteArray()
    }
}

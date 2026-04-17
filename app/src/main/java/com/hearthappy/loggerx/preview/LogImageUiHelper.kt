package com.hearthappy.loggerx.preview

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.hearthappy.log.LoggerX
import java.io.File

object LogImageUiHelper {
    /**
     * 判断日志是否包含图片
     */
    fun isImageLog(log: Map<String, Any>): Boolean {
        val byColumn = log[LoggerX.COLUMN_IS_IMAGE]?.toString() == "1"
        if (byColumn) return true
        return log[LoggerX.COLUMN_FILE_PATH]?.toString().orEmpty().isNotBlank()
    }

    fun decodeThumbnail(log: Map<String, Any>): Bitmap? {
        val filePath = log[LoggerX.COLUMN_FILE_PATH]?.toString().orEmpty()
        if (filePath.isBlank()) return null
        thumbnailCache.get(filePath)?.let { return it }
        val file = File(filePath)
        if (!file.exists() || !file.isFile || !file.canRead() || file.length() <= 0L) {
            return null
        }
        return runCatching {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, options)
            if (options.outWidth <= 0 || options.outHeight <= 0) return null
            val sampleSize = calculateInSampleSize(options.outWidth, options.outHeight, THUMB_MAX_SIDE)
            val bitmap = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeFile(file.absolutePath, bitmap)
        }.getOrNull()?.also {
            thumbnailCache.put(filePath, it)
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxSide: Int): Int {
        var sample = 1
        var w = width
        var h = height
        while (w > maxSide || h > maxSide) {
            sample *= 2
            w /= 2
            h /= 2
        }
        return sample.coerceAtLeast(1)
    }

    private const val THUMB_MAX_SIDE = 256
    private val thumbnailCache = object : LruCache<String, Bitmap>(60) {}
}

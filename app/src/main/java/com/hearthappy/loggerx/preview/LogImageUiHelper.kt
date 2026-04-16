package com.hearthappy.loggerx.preview

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.hearthappy.log.LoggerX

object LogImageUiHelper {
    private const val BASE64_IMAGE_PREFIX = "data:image/"

    fun isImageLog(log: Map<String, Any>): Boolean {
        val byColumn = log[LoggerX.COLUMN_IS_IMAGE]?.toString() == "1"
        if (byColumn) return true
        val message = log[LoggerX.COLUMN_MESSAGE]?.toString().orEmpty()
        return message.startsWith(BASE64_IMAGE_PREFIX) || message.startsWith("/9j/")
    }

    fun decodeThumbnail(log: Map<String, Any>): Bitmap? {
        val base64 = log[LoggerX.COLUMN_THUMBNAIL]?.toString().orEmpty()
        if (base64.isBlank()) return null
        return runCatching {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }
}

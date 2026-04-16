package com.hearthappy.log.core

import com.hearthappy.log.db.LogDbManager
import java.util.UUID

object ImageLogger {
    data class ImageLogMeta(
        val scopeTag: String,
        val level: String,
        val classTag: String,
        val method: String,
        val message: String,
        val important: Boolean = false,
        val quality: Int = ImageLogCodec.DEFAULT_WEBP_QUALITY
    )

    data class ImageLogTicket(
        val requestId: String,
        val accepted: Boolean
    )

    fun logAsync(imageBlob: ByteArray, meta: ImageLogMeta): ImageLogTicket {
        val requestId = UUID.randomUUID().toString()
        val accepted = LogDbManager.enqueueImageLog(
            scopeTag = meta.scopeTag,
            level = meta.level,
            classTag = meta.classTag,
            method = meta.method,
            message = meta.message,
            imageBytes = imageBlob,
            compressor = com.hearthappy.log.LoggerX.imageCompressor,
            options = com.hearthappy.log.LoggerX.imageCompressionOptions.copy(quality = meta.quality),
            important = meta.important
        )
        return ImageLogTicket(
            requestId = requestId,
            accepted = accepted
        )
    }
}

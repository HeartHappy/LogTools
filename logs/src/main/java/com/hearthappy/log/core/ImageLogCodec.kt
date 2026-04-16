package com.hearthappy.log.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

data class EncodedImageLog(
    val mimeType: String,
    val thumbnailBase64: String,
    val base64Payload: String?,
    val chunked: Boolean,
    val chunks: List<String>,
    val originalBytes: Int,
    val compressedBytes: Int
)

object ImageLogCodec {
    const val MAX_TEXT_COLUMN_BYTES = 65_535
    const val LARGE_IMAGE_THRESHOLD_BYTES = 500 * 1024
    private const val INLINE_SAFE_BYTES = 60 * 1024
    private const val MAX_TOTAL_BASE64_BYTES = 8 * 1024 * 1024
    private const val CHUNK_BYTES = 48 * 1024
    private const val THUMB_SIZE = 128

    fun encode(
        source: ByteArray,
        sourceMime: String?,
        quality: Int = 70,
        preferWebp: Boolean = true
    ): EncodedImageLog? {
        val bitmap = BitmapFactory.decodeByteArray(source, 0, source.size) ?: return null
        val safeQuality = min(80, max(60, quality))
        val targetMime = targetMime(sourceMime, preferWebp)
        val compressed = compressBitmap(bitmap, targetMime, safeQuality) ?: return null
        val payloadBase64 = Base64.encodeToString(compressed, Base64.NO_WRAP)
        val payloadBytes = payloadBase64.toByteArray(Charsets.UTF_8).size
        if (payloadBytes > MAX_TOTAL_BASE64_BYTES) {
            return null
        }
        val thumbBase64 = createThumbnailBase64(bitmap)
        val shouldChunk = source.size > LARGE_IMAGE_THRESHOLD_BYTES || payloadBytes > INLINE_SAFE_BYTES
        if (!shouldChunk && payloadBytes <= MAX_TEXT_COLUMN_BYTES) {
            return EncodedImageLog(
                mimeType = targetMime,
                thumbnailBase64 = thumbBase64,
                base64Payload = payloadBase64,
                chunked = false,
                chunks = emptyList(),
                originalBytes = source.size,
                compressedBytes = compressed.size
            )
        }
        val chunks = payloadBase64.chunked(CHUNK_BYTES)
        if (chunks.any { it.toByteArray(Charsets.UTF_8).size > MAX_TEXT_COLUMN_BYTES }) {
            return null
        }
        return EncodedImageLog(
            mimeType = targetMime,
            thumbnailBase64 = thumbBase64,
            base64Payload = null,
            chunked = true,
            chunks = chunks,
            originalBytes = source.size,
            compressedBytes = compressed.size
        )
    }

    private fun createThumbnailBase64(source: Bitmap): String {
        val thumb = Bitmap.createScaledBitmap(source, THUMB_SIZE, THUMB_SIZE, true)
        val bytes = compressBitmap(thumb, "image/jpeg", 70) ?: byteArrayOf()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun compressBitmap(bitmap: Bitmap, mimeType: String, quality: Int): ByteArray? {
        val format = when (mimeType) {
            "image/png" -> Bitmap.CompressFormat.PNG
            "image/webp" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
            }
            else -> Bitmap.CompressFormat.JPEG
        }
        return try {
            val output = ByteArrayOutputStream()
            bitmap.compress(format, quality, output)
            output.toByteArray()
        } catch (_: Exception) {
            null
        }
    }

    private fun targetMime(sourceMime: String?, preferWebp: Boolean): String {
        if (!preferWebp) {
            return when (sourceMime) {
                "image/png" -> "image/png"
                else -> "image/jpeg"
            }
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) "image/webp" else "image/jpeg"
    }
}

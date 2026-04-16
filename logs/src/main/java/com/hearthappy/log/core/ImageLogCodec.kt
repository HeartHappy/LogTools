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
    const val MAX_COMPRESSED_BYTES = 500 * 1024
    const val MIN_TARGET_RATIO_PERCENT = 60
    const val MAX_TARGET_RATIO_PERCENT = 80
    const val DEFAULT_TARGET_RATIO_PERCENT = 60
    private const val INLINE_SAFE_BYTES = 60 * 1024
    private const val MAX_TOTAL_BASE64_BYTES = 8 * 1024 * 1024
    private const val CHUNK_BYTES = 48 * 1024
    private const val THUMB_SIZE = 128

    fun encode(
        source: ByteArray, quality: Int = DEFAULT_TARGET_RATIO_PERCENT,
        preferWebp: Boolean = true
    ): EncodedImageLog? {
        val bitmap = BitmapFactory.decodeByteArray(source, 0, source.size) ?: return null
        val targetRatioPercent = min(MAX_TARGET_RATIO_PERCENT, max(MIN_TARGET_RATIO_PERCENT, quality))
        val compressedResult = compressLossless(bitmap, preferWebp) ?: return null
        val compressed = compressedResult.bytes
        val targetLimitByRatio = (source.size * targetRatioPercent) / 100
        if (compressed.size > targetLimitByRatio || compressed.size > MAX_COMPRESSED_BYTES) {
            return null
        }
        val payloadBase64 = Base64.encodeToString(compressed, Base64.NO_WRAP)
        val payloadBytes = payloadBase64.toByteArray(Charsets.UTF_8).size
        if (payloadBytes > MAX_TOTAL_BASE64_BYTES) {
            return null
        }
        val thumbBase64 = createThumbnailBase64(bitmap)
        val shouldChunk = source.size > LARGE_IMAGE_THRESHOLD_BYTES || payloadBytes > INLINE_SAFE_BYTES
        if (!shouldChunk) {
            return EncodedImageLog(
                mimeType = compressedResult.mimeType,
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
            mimeType = compressedResult.mimeType,
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

    private data class CompressedResult(val mimeType: String, val bytes: ByteArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as CompressedResult

            if (mimeType != other.mimeType) return false
            if (!bytes.contentEquals(other.bytes)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = mimeType.hashCode()
            result = 31 * result + bytes.contentHashCode()
            return result
        }
    }

    private fun compressLossless(bitmap: Bitmap, preferWebp: Boolean): CompressedResult? {
        val candidates = mutableListOf<CompressedResult>()
        val webpBytes = compressWebpLossless(bitmap)
        if (preferWebp && webpBytes != null) {
            candidates.add(CompressedResult("image/webp", webpBytes))
        }
        val pngBytes = compressBitmap(bitmap, "image/png", 100)
        if (pngBytes != null) {
            candidates.add(CompressedResult("image/png", pngBytes))
        }
        if (!preferWebp && webpBytes != null) {
            candidates.add(CompressedResult("image/webp", webpBytes))
        }
        if (candidates.isEmpty()) return null
        return candidates.minByOrNull { it.bytes.size }
    }

    private fun compressWebpLossless(bitmap: Bitmap): ByteArray? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return try {
                val output = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, output)
                output.toByteArray()
            } catch (_: Exception) {
                null
            }
        }
        @Suppress("DEPRECATION")
        return try {
            val output = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.WEBP, 100, output)
            output.toByteArray()
        } catch (_: Exception) {
            null
        }
    }

    private fun compressBitmap(bitmap: Bitmap, mimeType: String, quality: Int): ByteArray? {
        val format = when (mimeType) {
            "image/png" -> Bitmap.CompressFormat.PNG
            "image/webp" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSLESS else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
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

}

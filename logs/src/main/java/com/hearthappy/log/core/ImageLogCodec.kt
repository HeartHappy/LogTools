package com.hearthappy.log.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.os.Build
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

data class EncodedImageLog(
    val mediaType: String,
    val thumbnailBase64: String,
    val compressedImage: ByteArray,
    val originalBytes: Int,
    val compressedBytes: Int,
    val width: Int,
    val height: Int,
    val compressionRatio: Double,
    val compressionLog: String
)

data class CompressionLogEntry(
    val stage: String,
    val width: Int,
    val height: Int,
    val quality: Int,
    val grayscale: Boolean,
    val outputBytes: Int,
    val durationMs: Long,
    val mediaType: String,
    val visualScore: Double? = null
)

data class ImageCompressionOptions(
    val quality: Int = ImageLogCodec.DEFAULT_WEBP_QUALITY,
    val preferLossless: Boolean = true,
    val targetSizeKb: Int = ImageLogCodec.DEFAULT_TARGET_SIZE_KB,
    val minSidePx: Int = ImageLogCodec.DEFAULT_MIN_SIDE_PX,
    val visualQualityFloor: Double = ImageLogCodec.DEFAULT_VISUAL_QUALITY_FLOOR
)

data class CompressedImage(
    val bytes: ByteArray,
    val mediaType: String,
    val quality: Int,
    val width: Int,
    val height: Int,
    val grayscale: Boolean = false
)

interface IImageCompressor {
    fun compress(bitmap: Bitmap, options: ImageCompressionOptions): CompressedImage?
}

class DefaultImageCompressor : IImageCompressor {
    override fun compress(bitmap: Bitmap, options: ImageCompressionOptions): CompressedImage? {
        val quality = options.quality.coerceIn(10, 100)
        val bytes = if (options.preferLossless) {
            ImageLogCodec.compressWebpLossless(bitmap)
        } else {
            ImageLogCodec.compressWebpLossy(bitmap, quality)
        } ?: return null
        return CompressedImage(
            bytes = bytes,
            mediaType = "image/webp",
            quality = if (options.preferLossless) 100 else quality,
            width = bitmap.width,
            height = bitmap.height
        )
    }
}

object ImageLogCodec {
    const val MAX_INPUT_BYTES = 32 * 1024 * 1024
    const val DEFAULT_WEBP_QUALITY = 50
    const val DEFAULT_TARGET_SIZE_KB = 200
    const val DEFAULT_MIN_SIDE_PX = 512
    const val DEFAULT_VISUAL_QUALITY_FLOOR = 0.95
    const val PERFORMANCE_WARN_MS = 500L
    private const val THUMB_MAX_SIDE = 256
    private const val SIZE_TOLERANCE_PERCENT = 0.05
    private const val QUALITY_COMPARE_SIDE = 64
    private val FALLBACK_QUALITIES = intArrayOf(85, 75, 65, 55, 45, 40, 35, 30, 25, 20, 15, 10)
    private val FALLBACK_SCALE_FACTORS = floatArrayOf(1f, 0.95f, 0.9f, 0.85f, 0.75f, 0.65f, 0.5f)

    fun encode(
        source: ByteArray,
        compressor: IImageCompressor = DefaultImageCompressor(),
        options: ImageCompressionOptions = ImageCompressionOptions(),
        maxFieldBytes: Int = MAX_INPUT_BYTES
    ): EncodedImageLog? {
        if (source.isEmpty()) return null
        val encodeStart = System.nanoTime()
        val srcBitmap = BitmapFactory.decodeByteArray(source, 0, source.size) ?: return null
        val thumbnailBase64 = createThumbnailBase64(srcBitmap)
        val targetBytes = options.targetSizeKb.coerceAtLeast(32) * 1024
        val logEntries = mutableListOf<CompressionLogEntry>()

        var best = compressByPlugin(srcBitmap, compressor, options, logEntries)
        if (best != null && canUseCandidate(srcBitmap, best, targetBytes, options, maxFieldBytes, logEntries)) {
            return buildEncoded(source.size, thumbnailBase64, best, logEntries, encodeStart)
        }

        val fallbackSource = best?.let { BitmapFactory.decodeByteArray(it.bytes, 0, it.bytes.size) } ?: srcBitmap
        best = fallbackAdaptiveCompress(
            originalBitmap = srcBitmap,
            workingBitmap = fallbackSource,
            sourceBytes = source.size,
            targetBytes = targetBytes,
            options = options,
            maxFieldBytes = maxFieldBytes,
            logs = logEntries
        )
        if (best != null && canStore(best.bytes.size, maxFieldBytes)) {
            return buildEncoded(source.size, thumbnailBase64, best, logEntries, encodeStart)
        }
        return null
    }

    private fun compressByPlugin(
        bitmap: Bitmap,
        compressor: IImageCompressor,
        options: ImageCompressionOptions,
        logs: MutableList<CompressionLogEntry>
    ): CompressedImage? {
        val start = System.nanoTime()
        return runCatching { compressor.compress(bitmap, options) }.getOrNull()?.also { compressed ->
            logs.add(
                CompressionLogEntry(
                    stage = "custom_compressor",
                    width = compressed.width,
                    height = compressed.height,
                    quality = compressed.quality,
                    grayscale = compressed.grayscale,
                    outputBytes = compressed.bytes.size,
                    durationMs = nsToMs(System.nanoTime() - start),
                    mediaType = compressed.mediaType
                )
            )
        }
    }

    private fun fallbackAdaptiveCompress(
        originalBitmap: Bitmap,
        workingBitmap: Bitmap,
        sourceBytes: Int,
        targetBytes: Int,
        options: ImageCompressionOptions,
        maxFieldBytes: Int,
        logs: MutableList<CompressionLogEntry>
    ): CompressedImage? {
        val minSide = resolveMinSide(workingBitmap, options.minSidePx)
        val minScale = (minSide.toFloat() / min(workingBitmap.width, workingBitmap.height).toFloat())
            .coerceIn(0f, 1f)
        val estimatedRatio = kotlin.math.sqrt(targetBytes.toDouble() / sourceBytes.coerceAtLeast(1).toDouble())
            .toFloat()
            .coerceIn(minScale, 1f)
        val scales = buildAdaptiveScales(estimatedRatio, minScale)
        val qualities = buildAdaptiveQualities(targetBytes, sourceBytes)
        var best: CompressedImage? = null
        var bestDistance = Long.MAX_VALUE
        for (scale in scales) {
            val scaled = resize(workingBitmap, scale)
            for (quality in qualities) {
                val start = System.nanoTime()
                val bytes = compressWebpLossy(scaled, quality) ?: continue
                val candidate = CompressedImage(
                    bytes = bytes,
                    mediaType = "image/webp",
                    quality = quality,
                    width = scaled.width,
                    height = scaled.height,
                    grayscale = false
                )
                val visualScore = estimateVisualQuality(originalBitmap, bytes)
                logs.add(
                    CompressionLogEntry(
                        stage = "webp_adaptive",
                        width = candidate.width,
                        height = candidate.height,
                        quality = quality,
                        grayscale = false,
                        outputBytes = bytes.size,
                        durationMs = nsToMs(System.nanoTime() - start),
                        mediaType = "image/webp",
                        visualScore = visualScore
                    )
                )
                val distance = kotlin.math.abs(bytes.size - targetBytes).toLong()
                if (best == null || distance < bestDistance) {
                    best = candidate
                    bestDistance = distance
                }
                if (isTargetHit(bytes.size, targetBytes) &&
                    visualScore >= options.visualQualityFloor &&
                    canStore(bytes.size, maxFieldBytes)
                ) {
                    return candidate
                }
            }
        }
        return best
    }

    private fun buildEncoded(
        originalBytes: Int,
        thumbnailBase64: String,
        compressed: CompressedImage,
        logs: List<CompressionLogEntry>,
        encodeStartNs: Long
    ): EncodedImageLog {
        val totalCostMs = nsToMs(System.nanoTime() - encodeStartNs)
        val ratio = if (originalBytes <= 0) 1.0 else compressed.bytes.size.toDouble() / originalBytes.toDouble()
        val logText = buildString {
            append("origin=").append(originalBytes).append("B")
            append(",total=").append(totalCostMs).append("ms")
            logs.forEachIndexed { index, item ->
                append(" | #").append(index + 1)
                append(" stage=").append(item.stage)
                append(",size=").append(item.outputBytes).append("B")
                append(",q=").append(item.quality)
                append(",w=").append(item.width)
                append(",h=").append(item.height)
                append(",gray=").append(item.grayscale)
                append(",mime=").append(item.mediaType)
                append(",cost=").append(item.durationMs).append("ms")
                item.visualScore?.let { append(",score=").append(String.format("%.4f", it)) }
            }
            if (totalCostMs > PERFORMANCE_WARN_MS) append(" | perf_warn=true")
        }
        return EncodedImageLog(
            mediaType = compressed.mediaType,
            thumbnailBase64 = thumbnailBase64,
            compressedImage = compressed.bytes,
            originalBytes = originalBytes,
            compressedBytes = compressed.bytes.size,
            width = compressed.width,
            height = compressed.height,
            compressionRatio = ratio,
            compressionLog = logText
        )
    }

    private fun createThumbnailBase64(source: Bitmap): String {
        val maxSide = max(source.width, source.height).coerceAtLeast(1)
        val ratio = min(1f, THUMB_MAX_SIDE.toFloat() / maxSide.toFloat())
        val thumb = resize(source, ratio)
        val bytes = compressJpeg(thumb, 70) ?: byteArrayOf()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun resize(bitmap: Bitmap, ratio: Float): Bitmap {
        if (ratio >= 0.999f) return bitmap
        val targetW = max(1, (bitmap.width * ratio).toInt())
        val targetH = max(1, (bitmap.height * ratio).toInt())
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }

    private fun toGrayscale(bitmap: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val matrix = ColorMatrix().apply { setSaturation(0f) }
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return out
    }

    private fun canStore(size: Int, maxFieldBytes: Int): Boolean {
        return size <= maxFieldBytes
    }

    private fun canUseCandidate(
        originalBitmap: Bitmap,
        candidate: CompressedImage,
        targetBytes: Int,
        options: ImageCompressionOptions,
        maxFieldBytes: Int,
        logs: MutableList<CompressionLogEntry>
    ): Boolean {
        val score = estimateVisualQuality(originalBitmap, candidate.bytes)
        logs.add(
            CompressionLogEntry(
                stage = "plugin_eval",
                width = candidate.width,
                height = candidate.height,
                quality = candidate.quality,
                grayscale = candidate.grayscale,
                outputBytes = candidate.bytes.size,
                durationMs = 0L,
                mediaType = candidate.mediaType,
                visualScore = score
            )
        )
        return canStore(candidate.bytes.size, maxFieldBytes) &&
            isTargetHit(candidate.bytes.size, targetBytes) &&
            score >= options.visualQualityFloor
    }

    private fun estimateVisualQuality(original: Bitmap, candidateBytes: ByteArray): Double {
        val candidate = BitmapFactory.decodeByteArray(candidateBytes, 0, candidateBytes.size) ?: return 0.0
        val sourceScaled = Bitmap.createScaledBitmap(original, QUALITY_COMPARE_SIDE, QUALITY_COMPARE_SIDE, true)
        val candidateScaled = Bitmap.createScaledBitmap(candidate, QUALITY_COMPARE_SIDE, QUALITY_COMPARE_SIDE, true)
        val srcPixels = IntArray(QUALITY_COMPARE_SIDE * QUALITY_COMPARE_SIDE)
        val dstPixels = IntArray(QUALITY_COMPARE_SIDE * QUALITY_COMPARE_SIDE)
        sourceScaled.getPixels(srcPixels, 0, QUALITY_COMPARE_SIDE, 0, 0, QUALITY_COMPARE_SIDE, QUALITY_COMPARE_SIDE)
        candidateScaled.getPixels(dstPixels, 0, QUALITY_COMPARE_SIDE, 0, 0, QUALITY_COMPARE_SIDE, QUALITY_COMPARE_SIDE)
        var diffSum = 0.0
        for (i in srcPixels.indices) {
            val s = srcPixels[i]
            val d = dstPixels[i]
            val srcLuma = ((s shr 16 and 0xFF) * 0.299) + ((s shr 8 and 0xFF) * 0.587) + ((s and 0xFF) * 0.114)
            val dstLuma = ((d shr 16 and 0xFF) * 0.299) + ((d shr 8 and 0xFF) * 0.587) + ((d and 0xFF) * 0.114)
            diffSum += kotlin.math.abs(srcLuma - dstLuma)
        }
        val mae = diffSum / srcPixels.size.toDouble()
        return (1.0 - (mae / 255.0)).coerceIn(0.0, 1.0)
    }

    private fun buildAdaptiveScales(estimatedRatio: Float, minScale: Float): List<Float> {
        return FALLBACK_SCALE_FACTORS
            .map { (estimatedRatio * it).coerceIn(minScale, 1f) }
            .plus(1f)
            .distinct()
            .sortedDescending()
    }

    private fun buildAdaptiveQualities(targetBytes: Int, sourceBytes: Int): IntArray {
        val ratio = targetBytes.toDouble() / sourceBytes.coerceAtLeast(1).toDouble()
        val seed = (ratio * 100.0).toInt().coerceIn(20, 92)
        val adaptive = intArrayOf(seed + 12, seed + 6, seed, seed - 6, seed - 12, seed - 18)
            .map { it.coerceIn(10, 95) }
        return (adaptive + FALLBACK_QUALITIES.toList()).distinct().toIntArray()
    }

    private fun isTargetHit(sizeBytes: Int, targetBytes: Int): Boolean {
        val tolerance = (targetBytes * SIZE_TOLERANCE_PERCENT).toInt().coerceAtLeast(1024)
        return kotlin.math.abs(sizeBytes - targetBytes) <= tolerance
    }

    private fun resolveMinSide(bitmap: Bitmap, requestedMinSide: Int): Int {
        val actualMin = min(bitmap.width, bitmap.height)
        return min(actualMin, requestedMinSide.coerceAtLeast(1))
    }

    private fun compressJpeg(bitmap: Bitmap, quality: Int): ByteArray? {
        return runCatching {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(1, 100), out)
            out.toByteArray()
        }.getOrNull()
    }

    internal fun compressWebpLossless(bitmap: Bitmap): ByteArray? {
        return runCatching {
            val output = ByteArrayOutputStream()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, output)
            } else {
                @Suppress("DEPRECATION")
                bitmap.compress(Bitmap.CompressFormat.WEBP, 100, output)
            }
            output.toByteArray()
        }.getOrNull()
    }

    internal fun compressWebpLossy(bitmap: Bitmap, quality: Int): ByteArray? {
        return runCatching {
            val output = ByteArrayOutputStream()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, quality.coerceIn(1, 100), output)
            } else {
                @Suppress("DEPRECATION")
                bitmap.compress(Bitmap.CompressFormat.WEBP, quality.coerceIn(1, 100), output)
            }
            output.toByteArray()
        }.getOrNull()
    }

    private fun nsToMs(ns: Long): Long = ns / 1_000_000L
}

package com.hearthappy.log.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import com.jakewharton.disklrucache.DiskLruCache
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.concurrent.withLock
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale

class DefaultLogImageLoader(context : Context) : ILogImageLoader, ILogImageLoaderDiagnostics, DecodeControllable {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val decodeExecutor = ThreadPoolExecutor(CORE_POOL_SIZE, CORE_POOL_SIZE, 30L, TimeUnit.SECONDS, LinkedBlockingQueue(), Executors.defaultThreadFactory()).apply {
        allowCoreThreadTimeOut(true)
    }
    private val memoryCache = object : LruCache<String, Bitmap>(resolveMemoryCacheSize()) {
        override fun sizeOf(key : String, value : Bitmap) : Int {
            return value.byteCount.coerceAtLeast(1)
        }
    }
    private val pauseLock = ReentrantLock()
    private val pauseCondition = pauseLock.newCondition()
    private val inFlightCallbacks = mutableMapOf<String, MutableList<(Bitmap?) -> Unit>>()
    private val bitmapPool = BitmapPool()
    private val memoryHits = AtomicLong(0L)
    private val diskHits = AtomicLong(0L)
    private val decodeRequests = AtomicLong(0L)
    private val decodeSuccess = AtomicLong(0L)
    private val decodeFailure = AtomicLong(0L)
    private val decodeCostMs = AtomicLong(0L)
    private val peakDecodedBytes = AtomicLong(0L)

    @Volatile private var paused = false

    @Volatile private var diskCache : DiskLruCache? = null

    override fun loadThumbnail(path : String, width : Int, height : Int, callback : (Bitmap?) -> Unit) {
        val normalizedPath = path.trim()
        if (normalizedPath.isBlank()) {
            postResult(callback, null)
            return
        }
        val requestWidth = normalizeThumbEdge(width)
        val requestHeight = normalizeThumbEdge(height)
        val key = buildThumbnailCacheKey(normalizedPath, requestWidth, requestHeight, THUMB_QUALITY)
        memoryCache.get(key)?.let {
            memoryHits.incrementAndGet()
            postResult(callback, it)
            return
        }
        enqueue(key, callback) {
            decodeThumbnail(normalizedPath, key, requestWidth, requestHeight)
        }
    }

    override fun loadOriginal(path : String, callback : (Bitmap?) -> Unit) {
        val normalizedPath = path.trim()
        if (normalizedPath.isBlank()) {
            postResult(callback, null)
            return
        }
        val key = buildOriginalCacheKey(normalizedPath)
        memoryCache.get(key)?.let {
            memoryHits.incrementAndGet()
            postResult(callback, it)
            return
        }
        enqueue(key, callback) {
            decodeOriginal(normalizedPath, key)
        }
    }

    override fun clearCache() {
        val retained = memoryCache.snapshot().values.distinctBy { System.identityHashCode(it) }
        memoryCache.evictAll()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            retained.forEach(bitmapPool::put)
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            retained.forEach(::safeRecycle)
        }
        synchronized(diskCacheLock) {
            runCatching { diskCache?.delete() }
            diskCache = null
        }
        val dir = resolveDiskCacheDir()
        if (dir.exists()) {
            dir.deleteRecursively()
        }
        dir.mkdirs()
    }

    override fun snapshot() : LogImageLoadStatsSnapshot {
        val requests = decodeRequests.get().coerceAtLeast(1L)
        return LogImageLoadStatsSnapshot(memoryHits = memoryHits.get(), diskHits = diskHits.get(), decodeRequests = decodeRequests.get(), decodeSuccess = decodeSuccess.get(), decodeFailure = decodeFailure.get(), averageDecodeMs = decodeCostMs.get().toDouble() / requests.toDouble(), peakDecodedBytes = peakDecodedBytes.get())
    }

    override fun pauseDecode() {
        paused = true
    }

    override fun resumeDecode() {
        pauseLock.withLock {
            paused = false
            pauseCondition.signalAll()
        }
    }

    private fun enqueue(key : String, callback : (Bitmap?) -> Unit, decodeAction : () -> Bitmap?) {
        val shouldStart = synchronized(inFlightCallbacks) {
            val callbacks = inFlightCallbacks[key]
            if (callbacks != null) {
                callbacks += callback
                false
            } else {
                inFlightCallbacks[key] = mutableListOf(callback)
                true
            }
        }
        if (!shouldStart) {
            return
        }
        decodeExecutor.execute {
            awaitIfPaused()
            val result = decodeAction()
            val callbacks = synchronized(inFlightCallbacks) {
                inFlightCallbacks.remove(key).orEmpty()
            }
            callbacks.forEach { consumer ->
                postResult(consumer, result)
            }
        }
    }

    private fun decodeThumbnail(path : String, key : String, width : Int, height : Int) : Bitmap? {
        val source = File(path)
        if (!source.exists() || !source.isFile || !source.canRead() || source.length() <= 0L) {
            decodeFailure.incrementAndGet()
            return null
        }
        decodeFromDiskCache(key)?.let { bitmap ->
            diskHits.incrementAndGet()
            memoryCache.put(key, bitmap)
            return bitmap
        }
        val started = System.nanoTime()
        val result = runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return@runCatching null
            }
            val inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, width, height)
            val decoded = BitmapFactory.decodeFile(source.absolutePath, BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }) ?: return@runCatching null
            val fitted = constrainThumbnailBitmap(decoded, width, height)
            if (fitted !== decoded) {
                releaseTransientBitmap(decoded)
            }
            persistBitmapToDiskCache(key, fitted, THUMB_QUALITY, Bitmap.CompressFormat.JPEG)
            fitted
        }.getOrNull()
        return finalizeDecode(started, result, cacheKey = key, cacheToMemory = true)
    }

    private fun decodeOriginal(path : String, key : String) : Bitmap? {
        val source = File(path)
        if (!source.exists() || !source.isFile || !source.canRead() || source.length() <= 0L) {
            decodeFailure.incrementAndGet()
            return null
        }
        decodeFromDiskCache(key)?.let { bitmap ->
            diskHits.incrementAndGet()
            cacheOriginalIfEligible(key, bitmap)
            return bitmap
        }
        val started = System.nanoTime()
        val result = runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return@runCatching null
            }
            val bitmap = if (shouldUseRegionDecode(bounds.outWidth, bounds.outHeight)) {
                decodeOriginalByRegion(source, bounds.outWidth, bounds.outHeight)
            } else {
                BitmapFactory.decodeFile(source.absolutePath, BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.RGB_565
                })
            }
            bitmap?.also {
                persistBitmapToDiskCache(key, it, ORIGINAL_QUALITY, resolveOriginalCompressFormat(source, it))
            }
        }.getOrNull()
        return finalizeDecode(started, result, cacheKey = key, cacheToMemory = false)?.also { cacheOriginalIfEligible(key, it) }
    }

    private fun decodeOriginalByRegion(source : File, width : Int, height : Int) : Bitmap? {
        val result = runCatching {
            createBitmap(width, height, Bitmap.Config.RGB_565)
        }.getOrNull() ?: return null
        var decoder : BitmapRegionDecoder? = null
        try {
            decoder = BitmapRegionDecoder.newInstance(source.absolutePath, false)
            val canvas = Canvas(result)
            val tileHeight = max(MIN_REGION_TILE, min(MAX_REGION_TILE, REGION_TARGET_PIXELS / width.coerceAtLeast(1)))
            var top = 0
            while (top < height) {
                awaitIfPaused()
                val bottom = min(height, top + tileHeight)
                val region = Rect(0, top, width, bottom)
                val tile = decodeRegion(decoder, region) ?: return null
                canvas.drawBitmap(tile, 0f, top.toFloat(), null)
                releaseTransientBitmap(tile)
                top = bottom
            }
            return result
        } catch (_ : OutOfMemoryError) {
            safeRecycle(result)
            return null
        } catch (_ : IOException) {
            safeRecycle(result)
            return null
        } finally {
            decoder?.recycle()
        }
    }

    private fun decodeRegion(decoder : BitmapRegionDecoder, region : Rect) : Bitmap? {
        val pooled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            bitmapPool.get(region.width(), region.height(), Bitmap.Config.RGB_565)
        } else {
            null
        }
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.RGB_565
            if (pooled != null) {
                inMutable = true
                inBitmap = pooled
            }
        }
        return try {
            decoder.decodeRegion(region, options)
        } catch (_ : IllegalArgumentException) {
            if (pooled != null) {
                bitmapPool.put(pooled)
                options.inBitmap = null
            }
            decoder.decodeRegion(region, options)
        } catch (_ : OutOfMemoryError) {
            if (pooled != null) {
                bitmapPool.put(pooled)
            }
            null
        }
    }

    private fun finalizeDecode(startedNano : Long, bitmap : Bitmap?, cacheKey : String, cacheToMemory : Boolean) : Bitmap? {
        decodeRequests.incrementAndGet()
        val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNano)
        decodeCostMs.addAndGet(elapsedMs)
        if (bitmap == null) {
            decodeFailure.incrementAndGet()
            return null
        }
        decodeSuccess.incrementAndGet()
        var current : Long
        do {
            current = peakDecodedBytes.get()
        } while (!peakDecodedBytes.compareAndSet(current, max(current, bitmap.byteCount.toLong())))
        if (cacheToMemory) {
            memoryCache.put(cacheKey, bitmap)
        }
        return bitmap
    }

    private fun cacheOriginalIfEligible(key : String, bitmap : Bitmap) {
        if (bitmap.byteCount <= MAX_MEMORY_CACHEABLE_ORIGINAL_BYTES) {
            memoryCache.put(key, bitmap)
        }
    }

    private fun decodeFromDiskCache(key : String) : Bitmap? {
        val snapshot = synchronized(diskCacheLock) {
            runCatching { getDiskCache().get(key) }.getOrNull()
        } ?: return null
        snapshot.use { cached ->
            return cached.getInputStream(0).use { input ->
                runCatching {
                    BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.RGB_565
                    })
                }.getOrNull()
            }
        }
    }

    private fun persistBitmapToDiskCache(key : String, bitmap : Bitmap, quality : Int, format : Bitmap.CompressFormat) {
        val cache = synchronized(diskCacheLock) {
            getDiskCache()
        }
        val editor = runCatching { cache.edit(key) }.getOrNull() ?: return
        try {
            editor.newOutputStream(0).use { output ->
                if (!bitmap.compress(format, quality, output)) {
                    throw IOException("compress failed")
                }
                output.flush()
            }
            editor.commit()
            cache.flush()
        } catch (_ : Exception) {
            runCatching { editor.abortUnlessCommitted() }
        }
    }

    private fun getDiskCache() : DiskLruCache {
        diskCache?.let { return it }
        return DiskLruCache.open(resolveDiskCacheDir(), 1, 1, DISK_CACHE_SIZE_BYTES).also {
            diskCache = it
        }
    }

    private fun resolveDiskCacheDir() : File {
        val directory = File(appContext.cacheDir, DISK_CACHE_DIR_NAME)
        if (!directory.exists()) {
            directory.mkdirs()
        }
        return directory
    }

    private fun normalizeThumbEdge(edge : Int) : Int {
        return edge.takeIf { it > 0 }?.coerceAtMost(MAX_THUMB_EDGE) ?: MAX_THUMB_EDGE
    }

    private fun constrainThumbnailBitmap(bitmap : Bitmap, width : Int, height : Int) : Bitmap {
        val target = min(width, height).coerceAtLeast(MIN_THUMB_EDGE)
        val scaled = scaleBitmapIfNeeded(bitmap, target)
        val bytesPerPixel = when (scaled.config) {
            Bitmap.Config.ARGB_8888 -> 4
            else -> 2
        }
        val currentBytes = scaled.width.toLong() * scaled.height.toLong() * bytesPerPixel.toLong()
        if (currentBytes <= MAX_THUMB_MEMORY_BYTES) {
            return scaled
        }
        val scaleFactor = sqrt(MAX_THUMB_MEMORY_BYTES.toDouble() / currentBytes.toDouble()).coerceAtMost(1.0)
        val constrainedSide = (max(scaled.width, scaled.height) * scaleFactor).toInt().coerceAtLeast(MIN_THUMB_EDGE)
        val constrained = scaleBitmapIfNeeded(scaled, constrainedSide)
        if (constrained !== scaled) {
            releaseTransientBitmap(scaled)
        }
        return constrained
    }

    private fun scaleBitmapIfNeeded(bitmap : Bitmap, maxEdge : Int) : Bitmap {
        val largest = max(bitmap.width, bitmap.height)
        if (largest <= maxEdge) {
            return bitmap
        }
        val scale = maxEdge.toFloat() / largest.toFloat()
        val targetWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return bitmap.scale(targetWidth, targetHeight)
    }

    private fun shouldUseRegionDecode(width : Int, height : Int) : Boolean {
        return width.toLong() * height.toLong() >= REGION_DECODE_THRESHOLD_PIXELS
    }

    private fun resolveOriginalCompressFormat(source : File, bitmap : Bitmap) : Bitmap.CompressFormat {
        val extension = source.extension.lowercase(Locale.US)
        return if (bitmap.hasAlpha() || extension == "png") {
            Bitmap.CompressFormat.PNG
        } else {
            Bitmap.CompressFormat.JPEG
        }
    }

    private fun awaitIfPaused() {
        if (!paused) {
            return
        }
        pauseLock.withLock {
            while (paused) {
                pauseCondition.await()
            }
        }
    }

    private fun postResult(callback : (Bitmap?) -> Unit, bitmap : Bitmap?) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            callback(bitmap)
        } else {
            mainHandler.post { callback(bitmap) }
        }
    }

    private fun releaseTransientBitmap(bitmap : Bitmap) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            bitmapPool.put(bitmap)
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            safeRecycle(bitmap)
        }
    }

    private fun safeRecycle(bitmap : Bitmap) {
        if (!bitmap.isRecycled) {
            runCatching { bitmap.recycle() }
        }
    }

    private fun resolveMemoryCacheSize() : Int {
        val maxMemory = Runtime.getRuntime().maxMemory().coerceAtLeast(8L * 1024L * 1024L)
        val target = (maxMemory / 8L).coerceIn(4L * 1024L * 1024L, 64L * 1024L * 1024L)
        return target.toInt()
    }

    companion object {
        internal const val MAX_THUMB_EDGE = 512
        internal const val MIN_THUMB_EDGE = 128
        internal const val THUMB_QUALITY = 60
        internal const val ORIGINAL_QUALITY = 95
        internal const val MAX_THUMB_MEMORY_BYTES = 300L * 1024L
        internal const val MAX_MEMORY_CACHEABLE_ORIGINAL_BYTES = 8 * 1024 * 1024
        internal const val REGION_TARGET_PIXELS = 1_048_576
        internal const val REGION_DECODE_THRESHOLD_PIXELS = 6_000_000L
        internal const val MIN_REGION_TILE = 512
        internal const val MAX_REGION_TILE = 2_048
        private const val DISK_CACHE_DIR_NAME = "loggerx_image_cache"
        private const val DISK_CACHE_SIZE_BYTES = 200L * 1024L * 1024L
        private val CORE_POOL_SIZE = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        private val diskCacheLock = Any()

        fun buildThumbnailCacheKey(path : String, width : Int, height : Int, quality : Int = THUMB_QUALITY) : String {
            return "${md5(path)}_${width}x${height}_$quality"
        }

        fun buildOriginalCacheKey(path : String) : String {
            return "${md5(path)}_original_$ORIGINAL_QUALITY"
        }

        internal fun calculateInSampleSize(srcWidth : Int, srcHeight : Int, reqWidth : Int, reqHeight : Int) : Int {
            var sample = 1
            var width = srcWidth
            var height = srcHeight
            while (width / 2 >= reqWidth && height / 2 >= reqHeight) {
                width /= 2
                height /= 2
                sample *= 2
            }
            return sample.coerceAtLeast(1)
        }

        private fun md5(value : String) : String {
            val digest = MessageDigest.getInstance("MD5")
            val bytes = digest.digest(value.toByteArray(Charsets.UTF_8))
            return buildString(bytes.size * 2) {
                bytes.forEach { byte ->
                    append(String.format(Locale.US, "%02x", byte))
                }
            }
        }
    }
}

private class BitmapPool {
    private val pool = ConcurrentHashMap<String, MutableList<Bitmap>>()

    fun get(width : Int, height : Int, config : Bitmap.Config) : Bitmap? {
        val key = buildKey(width, height, config)
        return synchronized(pool) {
            val bitmaps = pool[key] ?: return null
            while (bitmaps.isNotEmpty()) {
                val candidate = bitmaps.removeAt(bitmaps.lastIndex)
                if (!candidate.isRecycled && candidate.isMutable) {
                    return candidate
                }
            }
            pool.remove(key)
            null
        }
    }

    fun put(bitmap : Bitmap) {
        if (!bitmap.isMutable || bitmap.isRecycled) {
            return
        }
        val key = buildKey(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.RGB_565)
        synchronized(pool) {
            val bitmaps = pool.getOrPut(key) { mutableListOf() }
            if (bitmaps.size >= MAX_BUCKET_SIZE) {
                return
            }
            bitmaps += bitmap
        }
    }

    private fun buildKey(width : Int, height : Int, config : Bitmap.Config) : String {
        return "${width}x${height}_${config.name}"
    }

    companion object {
        private const val MAX_BUCKET_SIZE = 4
    }
}

package com.hearthappy.log.image

import android.graphics.Bitmap

interface ILogImageLoader {
    fun loadThumbnail(path: String, width: Int, height: Int, callback: (Bitmap?) -> Unit)

    fun loadOriginal(path: String, callback: (Bitmap?) -> Unit)

    fun clearCache()
}

data class LogImageLoadStatsSnapshot(
    val memoryHits: Long,
    val diskHits: Long,
    val decodeRequests: Long,
    val decodeSuccess: Long,
    val decodeFailure: Long,
    val averageDecodeMs: Double,
    val peakDecodedBytes: Long
)

interface ILogImageLoaderDiagnostics {
    fun snapshot(): LogImageLoadStatsSnapshot
}

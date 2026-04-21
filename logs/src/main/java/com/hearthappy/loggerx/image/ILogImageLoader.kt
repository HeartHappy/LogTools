package com.hearthappy.loggerx.image

import android.widget.ImageView

interface ILogImageLoader {
    fun loadThumbnail(imageView: ImageView, path: String)

    fun loadOriginal(imageView: ImageView, path: String)
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

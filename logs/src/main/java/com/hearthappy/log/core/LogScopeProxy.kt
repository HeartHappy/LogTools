package com.hearthappy.log.core

import android.graphics.Bitmap
import android.util.Log
import com.hearthappy.log.LoggerX
import com.hearthappy.log.db.LogDbManager
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * Scope代理类：封装不同等级的日志调用
 */
class LogScopeProxy(private val scope: String): LogScope {
    // VERBOSE
    fun v(message: String) = output(LogLevel.VERBOSE, message)

    // DEBUG
    fun d(message: String) = output(LogLevel.DEBUG, message)

    // INFO
    fun i(message: String) = output(LogLevel.INFO, message)

    // WARN
    fun w(message: String) = output(LogLevel.WARN, message)

    // ERROR（支持Throwable）
    fun e(message: String, throwable: Throwable? = null) = output(LogLevel.ERROR, message, throwable)

    // ASSERT
    fun wtf(message: String) = output(LogLevel.ASSERT, message)

    // JSON
    fun json(json: String?) = output(LogLevel.DEBUG, json ?: "Empty JSON")

    // XML
    fun xml(xml: String?) = output(LogLevel.DEBUG, xml ?: "Empty XML")

    /**
     * 核心输出方法
     */
    private fun output(level: LogLevel, message: String, throwable: Throwable? = null) {
        LogOutputterManager.getOutputter(scope).output(level, message, throwable)
    }

    fun getListFiles(): List<File>? {
        return LogFileManager.getListFile(scope)
    }

    fun getDirectory(): String? {
        return LogFileManager.getDirectory()
    }

    fun clearAllFiles(): Boolean {
      return  LogFileManager.clear(scope)
    }

    fun deleteOldestSingleFile(): Boolean {
        return LogFileManager.deleteOldestSingleFile(scope)
    }

    fun queryLogs(
        time: String? = null,
        tag: String? = null,
        level: String? = null,
        method: String? = null,
        isImage: Boolean? = null,
        keyword: String? = null,
        isAsc: Boolean = false,
        page: Int = 1,
        limit: Int? = 100,
        includeImagePayload: Boolean = false
    ): List<Map<String, Any>> {
        return LogDbManager.queryLogsAdvanced(
            scope,
            time,
            tag,
            level,
            method,
            isImage,
            keyword,
            isAsc,
            page,
            limit,
            includeImagePayload
        )
    }

    fun getDistinctValues(columnName: String): List<String> {
        return LogDbManager.getDistinctValues(scope, columnName)
    }

    fun deleteLogs(time: String? = null): Int {
        return LogDbManager.deleteLogs(scope, time)
    }

    fun clearAllLogs(): Boolean {
        return LogDbManager.clearAllLogs()
    }

    fun image(
        imageBytes: ByteArray,
        mimeType: String = "image/jpeg",
        message: String = "image-log",
        quality: Int = 70
    ): Boolean {
        val startNs = System.nanoTime()
        val encodeStartNs = startNs
        val encoded = ImageLogCodec.encode(imageBytes, mimeType, quality) ?: run {
            val totalMs = nsToMs(System.nanoTime() - startNs)
            Log.w(LoggerX.TAG, "Image encode failed or exceeds max payload")
            recordImageWritePerf(totalMs, totalMs, 0L, false)
            return false
        }
        val encodeMs = nsToMs(System.nanoTime() - encodeStartNs)
        val stackTraceInfo = LogContextCollector.getStackTraceInfo()
        val classTag = stackTraceInfo.className
        val methodName = LogFormatter.format(stackTraceInfo)
        val dbStartNs = System.nanoTime()
        val result = LogDbManager.insertImageLog(
            scopeTag = scope,
            level = LogLevel.INFO.value,
            classTag = classTag,
            method = methodName,
            message = "$message [${encoded.mimeType}] ${encoded.originalBytes}B -> ${encoded.compressedBytes}B",
            mimeType = encoded.mimeType,
            thumbnailBase64 = encoded.thumbnailBase64,
            payloadBase64 = encoded.base64Payload,
            chunked = encoded.chunked,
            chunks = encoded.chunks
        )
        val dbMs = nsToMs(System.nanoTime() - dbStartNs)
        val totalMs = nsToMs(System.nanoTime() - startNs)
        recordImageWritePerf(totalMs, encodeMs, dbMs, result)
        return result
    }

    fun image(bitmap: Bitmap, mimeType: String = "image/jpeg", message: String = "image-log", quality: Int = 70): Boolean {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        return image(out.toByteArray(), mimeType, message, quality)
    }

    fun getImageWritePerfSnapshot(): Map<String, Any> {
        val count = imageWriteCount.get().coerceAtLeast(1L)
        val total = imageWriteTotalMs.get()
        return mapOf(
            "count" to imageWriteCount.get(),
            "failedCount" to imageWriteFailedCount.get(),
            "over100msCount" to imageWriteOver100MsCount.get(),
            "avgTotalMs" to (total.toDouble() / count.toDouble()),
            "maxTotalMs" to imageWriteMaxMs.get()
        )
    }

    fun loadImageBase64(logId: Int): String? {
        return LogDbManager.loadImageBase64(scope, logId)
    }

    fun loadImageMimeType(logId: Int): String? {
        return LogDbManager.loadImageMimeType(scope, logId)
    }
    // 2. 导出并分享
    fun doExportAndShare(exportAll: Boolean = false, limit: Int = 1000, onProgress: ((Int) -> Unit)? = null) {
        // 异步查询并导出，避免 UI 卡顿
        Thread {
            val logs = if (exportAll) {
                LogDbManager.queryLogsAdvanced(scope, limit = null)
            } else {
                LogDbManager.queryLogsAdvanced(scope, limit = limit)
            }
            val file = LogExportManager.exportToCsv(ContextHolder.getAppContext(), scope, logs, onProgress)

            file?.let {
                LogShareManager.shareLogFile(ContextHolder.getAppContext(), it)
            }
        }.start()
    }

    override fun getTag(): String {
        return scope
    }

    override fun getProxy(): LogScopeProxy {
        return this
    }

    private fun recordImageWritePerf(totalMs: Long, encodeMs: Long, dbMs: Long, success: Boolean) {
        imageWriteCount.incrementAndGet()
        if (!success) {
            imageWriteFailedCount.incrementAndGet()
        }
        imageWriteTotalMs.addAndGet(totalMs)
        imageWriteMaxMs.updateAndGet { old -> maxOf(old, totalMs) }
        if (totalMs > 100L) {
            imageWriteOver100MsCount.incrementAndGet()
            Log.w(LoggerX.TAG, "Image log write slow: total=${totalMs}ms, encode=${encodeMs}ms, db=${dbMs}ms")
        } else {
            Log.i(LoggerX.TAG, "Image log write perf: total=${totalMs}ms, encode=${encodeMs}ms, db=${dbMs}ms")
        }
    }

    private fun nsToMs(ns: Long): Long = ns / 1_000_000L

    companion object {
        private val imageWriteCount = AtomicLong(0)
        private val imageWriteFailedCount = AtomicLong(0)
        private val imageWriteOver100MsCount = AtomicLong(0)
        private val imageWriteTotalMs = AtomicLong(0)
        private val imageWriteMaxMs = AtomicLong(0)
    }
}

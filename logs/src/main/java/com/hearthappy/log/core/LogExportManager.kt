package com.hearthappy.log.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.hearthappy.log.LoggerX
import com.hearthappy.log.db.LogDbManager
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.Locale

object LogExportManager {
    enum class ExportFormat(val extension: String, val mimeType: String) {
        CSV("csv", "text/csv"),
        TXT("txt", "text/plain")
    }

    /**
     * 将指定 Scope 的日志导出为临时文件（CSV/TXT）
     * @param onProgress 进度回调 (0..100)
     */
    fun export(
        context: Context,
        scopeTag: String,
        logs: List<Map<String, Any>>,
        format: ExportFormat = ExportFormat.CSV,
        onProgress: ((Int) -> Unit)? = null
    ): File? {
        val fileName = "${scopeTag}_logs.${format.extension}"
        val exportFile = File(context.cacheDir, fileName) // 存放在缓存目录，方便分享

        try {
            OutputStreamWriter(FileOutputStream(exportFile), StandardCharsets.UTF_8).use { writer ->
                writer.append(headerLine(format))
                val total = logs.size

                // 2. 写入数据
                logs.forEachIndexed { index, log ->
                    writer.append(formatLine(log, format))

                    // 每一百条回调一次进度
                    if (total == 0 || index % 100 == 0 || index == total - 1) {
                        onProgress?.invoke(((index + 1).toFloat() / total * 100).toInt())
                    }
                }
                if (total == 0) {
                    onProgress?.invoke(100)
                }
                writer.flush()
            }
            return exportFile
        } catch (e: IOException) {
            e.printStackTrace()
            return null
        }
    }

    internal fun exportAll(
        exportAll: Boolean,
        limit: Int,
        format: ExportFormat = ExportFormat.CSV,
        onProgress: ((Int) -> Unit)? = null
    ) {
        Thread {
            val scopes = LogOutputterManager.getScopes()
            if (scopes.isEmpty()) return@Thread

            val exportedFiles = mutableListOf<File>()
            val totalScopes = scopes.size

            scopes.forEachIndexed { index, scope ->
                val logs = if (exportAll) {
                    LogDbManager.queryLogsAdvanced(scope, limit = null, includeImagePayload = true)
                } else {
                    LogDbManager.queryLogsAdvanced(scope, limit = limit, includeImagePayload = true)
                }

                // 计算该 scope 的进度在总体进度中的占比
                val baseProgress = (index.toFloat() / totalScopes * 100).toInt()
                val nextBaseProgress = ((index + 1).toFloat() / totalScopes * 100).toInt()

                val file = export(ContextHolder.getAppContext(), scope, logs, format) { scopeProgress ->
                    // 换算成总体进度
                    val overallProgress = baseProgress + (scopeProgress * (nextBaseProgress - baseProgress) / 100)
                    onProgress?.invoke(overallProgress)
                }

                file?.let { exportedFiles.add(it) }
            }

            if (exportedFiles.isNotEmpty()) {
                onProgress?.invoke(100)
                LogShareManager.shareLogFiles(ContextHolder.getAppContext(), exportedFiles)
            }
        }.start()
    }

    private fun headerLine(format: ExportFormat): String {
        val columns = listOf(
            LoggerX.COLUMN_ID,
            LoggerX.COLUMN_TIME,
            LoggerX.COLUMN_LEVEL,
            LoggerX.COLUMN_TAG,
            LoggerX.COLUMN_METHOD,
            LoggerX.COLUMN_MESSAGE,
            LoggerX.COLUMN_THUMBNAIL,
            LoggerX.COLUMN_IMAGE_ID,
            LoggerX.COLUMN_MEDIA_TYPE,
            LoggerX.COLUMN_COMPRESSED_IMAGE,
            LoggerX.COLUMN_ORIGINAL_SIZE,
            LoggerX.COLUMN_COMPRESSED_SIZE,
            LoggerX.COLUMN_COMPRESSION_RATIO
        )
        return when (format) {
            ExportFormat.CSV -> columns.joinToString(",") + "\r\n"
            ExportFormat.TXT -> columns.joinToString("\t") + "\r\n"
        }
    }

    private fun formatLine(log: Map<String, Any>, format: ExportFormat): String {
        val thumb = toPngDataUri(log[LoggerX.COLUMN_THUMBNAIL]?.toString())
        val compressed = toPngDataUri(log[LoggerX.COLUMN_COMPRESSED_IMAGE]?.toString())
        val exportMediaType = if (compressed.isNotBlank() || thumb.isNotBlank()) {
            "image/png"
        } else {
            log[LoggerX.COLUMN_MEDIA_TYPE]?.toString().orEmpty().ifBlank { "image/png" }
        }
        val row = listOf(
            log[LoggerX.COLUMN_ID].toText(),
            log[LoggerX.COLUMN_TIME].toText(),
            log[LoggerX.COLUMN_LEVEL].toText(),
            log[LoggerX.COLUMN_TAG].toText(),
            log[LoggerX.COLUMN_METHOD].toText(),
            log[LoggerX.COLUMN_MESSAGE].toText(),
            thumb,
            log[LoggerX.COLUMN_IMAGE_ID].toText(),
            exportMediaType,
            compressed,
            formatSizeMb(log[LoggerX.COLUMN_ORIGINAL_SIZE]),
            formatSizeMb(log[LoggerX.COLUMN_COMPRESSED_SIZE]),
            formatCompressionRatio(log[LoggerX.COLUMN_COMPRESSION_RATIO])
        )
        return when (format) {
            ExportFormat.CSV -> {
                row.joinToString(",") { quoteCsv(it) } + "\r\n"
            }
            ExportFormat.TXT -> {
                row.joinToString("\t") { it.replace("\r", " ").replace("\n", "\\n") } + "\r\n"
            }
        }
    }

    private fun quoteCsv(value: String): String {
        return "\"${value.replace("\"", "\"\"")}\""
    }

    private fun toPngDataUri(base64Body: String?): String {
        if (base64Body.isNullOrBlank()) return ""
        return runCatching {
            val decoded = Base64.decode(base64Body, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(decoded, 0, decoded.size) ?: return ""
            val pngBytes = bitmapToPng(bitmap) ?: return ""
            "data:image/png;base64,${Base64.encodeToString(pngBytes, Base64.NO_WRAP)}"
        }.getOrElse { "" }
    }

    private fun Any?.toText(): String = this?.toString().orEmpty()

    private fun bitmapToPng(bitmap: Bitmap): ByteArray? {
        return runCatching {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }.getOrNull()
    }

    private fun formatSizeMb(value: Any?): String {
        val bytes = value?.toString()?.toLongOrNull() ?: return ""
        if (bytes <= 0L) return ""
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        return String.format(Locale.US, "%.3fM", mb)
    }

    private fun formatCompressionRatio(value: Any?): String {
        val ratio = value?.toString()?.toDoubleOrNull() ?: return ""
        if (ratio <= 0.0) return ""
        return String.format(Locale.US, "%.2f%%", ratio * 100.0)
    }
}

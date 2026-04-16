package com.hearthappy.log.core

import android.content.Context
import com.hearthappy.log.LoggerX
import com.hearthappy.log.db.LogDbManager
import java.io.File
import java.io.FileWriter
import java.io.IOException

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
        val fileName = "${scopeTag}_logs_${System.currentTimeMillis()}.${format.extension}"
        val exportFile = File(context.cacheDir, fileName) // 存放在缓存目录，方便分享

        try {
            FileWriter(exportFile).use { writer ->
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
                    LogDbManager.queryLogsAdvanced(scope, limit = null)
                } else {
                    LogDbManager.queryLogsAdvanced(scope, limit = limit)
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
        return when (format) {
            ExportFormat.CSV -> "ID,Time,Level,Tag,Method,Message\n"
            ExportFormat.TXT -> "ID\tTime\tLevel\tTag\tMethod\tMessage\n"
        }
    }

    private fun formatLine(log: Map<String, Any>, format: ExportFormat): String {
        val id = log[LoggerX.COLUMN_ID].toString()
        val time = log[LoggerX.COLUMN_TIME].toString()
        val level = log[LoggerX.COLUMN_LEVEL].toString()
        val tag = log[LoggerX.COLUMN_TAG].toString()
        val method = log[LoggerX.COLUMN_METHOD].toString()
        val message = log[LoggerX.COLUMN_MESSAGE].toString()
        return when (format) {
            ExportFormat.CSV -> {
                val cleanTag = tag.replace("\"", "\"\"")
                val cleanMethod = method.replace("\"", "\"\"")
                val cleanMsg = message.replace("\"", "\"\"")
                "$id,$time,$level,\"$cleanTag\",\"$cleanMethod\",\"$cleanMsg\"\n"
            }
            ExportFormat.TXT -> {
                val cleanMsg = message.replace("\n", "\\n")
                "$id\t$time\t$level\t$tag\t$method\t$cleanMsg\n"
            }
        }
    }
}

package com.hearthappy.log.core

import android.content.Context
import com.hearthappy.log.LoggerX
import com.hearthappy.log.db.LogDbManager
import java.io.File
import java.io.FileWriter
import java.io.IOException

object LogExportManager {

    /**
     * 将指定 Scope 的日志导出为 CSV 文件
     * @param onProgress 进度回调 (0..100)
     */
    fun exportToCsv(context: Context, scopeTag: String, logs: List<Map<String, Any>>, onProgress: ((Int) -> Unit)? = null): File? {
        val fileName = "${scopeTag}_logs_${System.currentTimeMillis()}.csv"
        val exportFile = File(context.cacheDir, fileName) // 存放在缓存目录，方便分享

        try {
            FileWriter(exportFile).use { writer ->
                // 1. 写入表头
                writer.append("ID,Time,Level,Tag,Method,Message\n")
                val total = logs.size

                // 2. 写入数据
                logs.forEachIndexed { index, log ->
                    writer.append("${log[LoggerX.Companion.COLUMN_ID]},")
                    writer.append("${log[LoggerX.Companion.COLUMN_TIME]},")
                    writer.append("${log[LoggerX.Companion.COLUMN_LEVEL]},")
                    writer.append("\"${log[LoggerX.Companion.COLUMN_TAG]}\",")
                    writer.append("\"${log[LoggerX.Companion.COLUMN_METHOD]}\",")
                    // 消息内容可能包含换行或逗号，用引号包裹并处理转义
                    val cleanMsg = log[LoggerX.Companion.COLUMN_MESSAGE].toString().replace("\"", "\"\"")
                    writer.append("\"$cleanMsg\"\n")

                    // 每一百条回调一次进度
                    if (index % 100 == 0 || index == total - 1) {
                        onProgress?.invoke(((index + 1).toFloat() / total * 100).toInt())
                    }
                }
                writer.flush()
            }
            return exportFile
        } catch (e: IOException) {
            e.printStackTrace()
            return null
        }
    }

    internal fun exportAll(exportAll: Boolean, limit: Int, onProgress: ((Int) -> Unit)? = null){
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

                val file = exportToCsv(ContextHolder.getAppContext(), scope, logs) { scopeProgress ->
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
}
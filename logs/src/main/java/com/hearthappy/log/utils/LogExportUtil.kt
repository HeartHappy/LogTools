package com.hearthappy.log.utils

import android.content.Context
import com.hearthappy.log.Logger
import java.io.File
import java.io.FileWriter
import java.io.IOException

object LogExportUtil {

    /**
     * 将指定 Scope 的日志导出为 CSV 文件
     */
    fun exportToCsv(context: Context, scopeTag: String, logs: List<Map<String, Any>>): File? {
        val fileName = "${scopeTag}_logs_${System.currentTimeMillis()}.csv"
        val exportFile = File(context.cacheDir, fileName) // 存放在缓存目录，方便分享

        try {
            FileWriter(exportFile).use { writer ->
                // 1. 写入表头
                writer.append("ID,Time,Level,Tag,Method,Message\n")

                // 2. 写入数据
                logs.forEach { log ->
                    writer.append("${log[Logger.Companion.COLUMN_ID]},")
                    writer.append("${log[Logger.Companion.COLUMN_TIME]},")
                    writer.append("${getLevelString(log[Logger.Companion.COLUMN_LEVEL] as Int)},")
                    writer.append("\"${log[Logger.Companion.COLUMN_TAG]}\",")
                    writer.append("\"${log[Logger.Companion.COLUMN_METHOD]}\",")
                    // 消息内容可能包含换行或逗号，用引号包裹并处理转义
                    val cleanMsg = log[Logger.Companion.COLUMN_MESSAGE].toString().replace("\"", "\"\"")
                    writer.append("\"$cleanMsg\"\n")
                }
                writer.flush()
            }
            return exportFile
        } catch (e: IOException) {
            e.printStackTrace()
            return null
        }
    }

    private fun getLevelString(level: Int): String {
        return when (level) {
            2 -> "VERBOSE"
            3 -> "DEBUG"
            4 -> "INFO"
            5 -> "WARN"
            6 -> "ERROR"
            7 -> "ASSERT"
            else -> "UNKNOWN"
        }
    }
}
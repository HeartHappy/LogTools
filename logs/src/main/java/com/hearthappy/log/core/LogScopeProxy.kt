package com.hearthappy.log.core

import com.hearthappy.log.db.LogDbManager
import com.hearthappy.log.utils.LogExportUtil
import com.hearthappy.log.utils.LogShareUtil
import java.io.File

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

    fun queryLogs(time: String? = null, tag: String? = null, level: String? = null, method: String? = null, keyword: String? = null, isAsc: Boolean = false, page: Int = 1, limit: Int? = 100): List<Map<String, Any>> {
        return LogDbManager.queryLogsAdvanced(scope, time, tag, level, method, keyword, isAsc, page, limit)
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
    // 2. 导出并分享
    fun doExportAndShare(scope: String, exportAll: Boolean = false, limit: Int = 1000) {
        // 异步查询并导出，避免 UI 卡顿
        Thread {
            val logs = if (exportAll) {
                LogDbManager.queryLogsAdvanced(scope, limit = null)
            } else {
                LogDbManager.queryLogsAdvanced(scope, limit = limit)
            }
            val file = LogExportUtil.exportToCsv(ContextHolder.getAppContext(), scope, logs)

            file?.let {
                LogShareUtil.shareLogFile(ContextHolder.getAppContext(), it)
            }
        }.start()
    }

    override fun getTag(): String {
        return scope
    }

    override fun getProxy(): LogScopeProxy {
        return this
    }
}

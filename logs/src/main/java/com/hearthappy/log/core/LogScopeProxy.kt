package com.hearthappy.log.core

import com.hearthappy.log.db.LogDbManager
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
        LogFileManager.getOutputter(scope).output(level, message, throwable)
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

    fun queryLogs(time: String? = null, tag: String? = null, level: Int? = null, method: String? = null, keyword: String? = null, isAsc: Boolean = false, limit: Int = 100): List<Map<String, Any>> {
        return LogDbManager.getInstance(ContextHolder.getAppContext()).queryLogsAdvanced(scope, time, tag, level, method, keyword, isAsc, limit)
    }

    fun getDistinctValues(columnName: String): List<String> {
        return LogDbManager.getInstance(ContextHolder.getAppContext()).getDistinctValues(scope, columnName)
    }

    fun deleteLogs(time: String? = null): Int {
        return LogDbManager.getInstance(ContextHolder.getAppContext()).deleteLogs(scope, time)
    }

    fun clearAllLogs(): Boolean {
        return LogDbManager.getInstance(ContextHolder.getAppContext()).clearAllLogs()
    }

    override fun getTag(): String {
        return scope
    }
}
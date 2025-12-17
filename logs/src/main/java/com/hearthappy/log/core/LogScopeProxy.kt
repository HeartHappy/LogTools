package com.hearthappy.log.core

import java.io.File

/**
 * Scope代理类：封装不同等级的日志调用
 */
class LogScopeProxy(private val scope: LogScope) {
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
        LogManager.getOutputter(scope).output(level, message, throwable)
    }

    fun getListFiles(): List<File>? {
        return LogManager.getListFile(scope.tag)
    }

    fun clear() {
        LogManager.clearAll()
    }
}
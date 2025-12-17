package com.hearthappy.log.core

import android.content.Context
import com.hearthappy.log.interceptor.LogInterceptor

/**
 * Created Date: 2025/12/17/周三
 * @author ChenRui
 * ClassDescription：日志输出器
 */
class LogOutputter(private val scope: LogScope, private val context: Context?, private val diskPath: String?, private val logInterceptor: LogInterceptor) {

    private val logImpl: LogImpl by lazy {
        LogImpl(scope, logInterceptor, context, diskPath)
    }

    /**
     * 输出日志（自动处理上下文+格式化）
     */
    fun output(level: LogLevel, message: String, throwable: Throwable? = null) {
        logImpl.log(level.level, message, throwable)
    }
}
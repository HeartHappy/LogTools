package com.hearthappy.log.core

import android.util.Log

/**
 * 日志等级（复用原有值，统一管理）
 */
enum class LogLevel(val level: Int, val value: String) {
    VERBOSE(Log.VERBOSE, "VERBOSE"),

    DEBUG(Log.DEBUG, "DEBUG"),

    INFO(Log.INFO, "INFO"),

    WARN(Log.WARN, "WARN"),

    ERROR(Log.ERROR, "ERROR"),

    ASSERT(Log.ASSERT, "ASSERT")
}
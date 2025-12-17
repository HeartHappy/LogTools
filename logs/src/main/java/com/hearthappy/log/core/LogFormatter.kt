package com.hearthappy.log.core

import java.text.SimpleDateFormat
import java.util.Locale

//日志格式化器
object LogFormatter {

    // [方法(行号)]: 日志内容
    const val LOG_FORMAT = "%s(%d)"

    // 日期格式化
    val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINESE)

    // 文件命名中的日期格式
//    val FILE_DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.CHINESE)

    /**
     * 格式化日志内容, 用于拼接方法名+行号
     */
    fun format(stackTraceInfo: LogContextCollector.StackTraceInfo): String {
        return String.format(Locale.getDefault(), LOG_FORMAT, stackTraceInfo.methodName, stackTraceInfo.lineNumber)
    }
}




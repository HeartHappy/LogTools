package com.hearthappy.loggerx.preview

import com.hearthappy.log.LoggerX

object LogImageUiHelper {
    fun isImageLog(log: Map<String, Any>): Boolean {
        val byColumn = log[LoggerX.COLUMN_IS_IMAGE]?.toString() == "1"
        if (byColumn) return true
        return resolveFilePath(log).isNotBlank()
    }

    fun resolveFilePath(log: Map<String, Any>): String {
        return log[LoggerX.COLUMN_FILE_PATH]?.toString().orEmpty().trim()
    }
}

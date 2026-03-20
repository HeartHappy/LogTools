package com.hearthappy.log

import android.app.Application
import android.content.Context
import com.hearthappy.log.core.ContextHolder
import com.hearthappy.log.core.LogFileManager
import com.hearthappy.log.core.LogOutputter
import com.hearthappy.log.core.LogScope
import com.hearthappy.log.core.LogScopeProxy
import com.hearthappy.log.core.OutputConfig
import com.hearthappy.log.db.LogDbManager
import com.hearthappy.log.interceptor.LogInterceptor
import java.io.File


/**
 * Created Date: 2023/9/20
 * @author ChenRui
 * ClassDescription： 日志框架
 *   极简日志调用入口（对外暴露）
 *   用法：
 *   Logger.COMMON.d("普通日志")
 *   Logger.IMPORTANT.e("重要错误日志", Throwable("异常"))
 *   Logger.KERNEL.i("核心日志")
 *   Logger.ERROR.w("错误域警告日志") // 新增的Error域
 */
class Logger {
    companion object {
        //字段常量
        const val COLUMN_ID = "id"
        const val COLUMN_TIME = "time"
        const val COLUMN_LEVEL = "level"
        const val COLUMN_TAG = "tag"
        const val COLUMN_METHOD = "method"
        const val COLUMN_MESSAGE = "message"

        // ========== 内置Scope（直接调用） ==========
        val COMMON = LogScopeProxy("Common")
        val IMPORTANT = LogScopeProxy("Important")
        val KERNEL = LogScopeProxy("Kernel")
        val ERROR = LogScopeProxy("Error")

        // ========== 自定义Scope（扩展用） ==========
        fun registerScope(scope: LogScope, logInterceptor: LogInterceptor) {
            LogFileManager.registerOutputter(scope.getTag(), LogOutputter(scope = scope, logInterceptor))
        }

        fun createScope(customScope: String): LogScopeProxy {
            return LogFileManager.create(customScope)
        }


        /**
         * 全局初始化（Application中调用）
         */
        fun init(context: Context, outputConfig: OutputConfig = OutputConfig()) {
            ContextHolder.init(context as Application)
            LogFileManager.init(outputConfig)
        }

        /**
         * 清空所有日志
         */
        fun clearAllFiles() = LogFileManager.clearAll()

        fun getAllFiles(): List<File>? {
            return LogFileManager.getAllFiles()
        }

        fun clearAllLogs(): Boolean {
            return LogDbManager.getInstance(ContextHolder.getAppContext()).clearAllLogs()
        }

    }
}
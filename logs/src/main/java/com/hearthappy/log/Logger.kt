package com.hearthappy.log

import android.app.Application
import android.content.Context
import com.hearthappy.log.core.ContextHolder
import com.hearthappy.log.core.LogManager
import com.hearthappy.log.core.LogOutputter
import com.hearthappy.log.core.LogScope
import com.hearthappy.log.core.LogScopeProxy
import com.hearthappy.log.interceptor.LogInterceptor
import java.io.File


/**
 * 极简日志调用入口（对外暴露）
 * 用法：
 * Logger.common.d("普通日志")
 * Logger.important.e("重要错误日志", Throwable("异常"))
 * Logger.kernel.i("核心日志")
 * Logger.error.w("错误域警告日志") // 新增的Error域
 */
class Logger {
    companion object {
        // ========== 内置Scope（直接调用） ==========
        val COMMON = LogScopeProxy("Common")
        val IMPORTANT = LogScopeProxy("Important")
        val KERNEL = LogScopeProxy("Kernel")
        val ERROR = LogScopeProxy("Error")

        // ========== 自定义Scope（扩展用） ==========
        fun registerScope(scope: LogScope, diskPath: String? = null, logInterceptor: LogInterceptor) {
            LogManager.registerOutputter(scope.getTag(), LogOutputter(scope =scope, context = ContextHolder.getAppContext(), diskPath = diskPath, logInterceptor))
        }

        fun createScope(customScope: String): LogScopeProxy {
           return LogManager.create(customScope)
        }


        /**
         * 全局初始化（Application中调用）
         */
        fun init(context: Context, diskPath: String? = null) {
            ContextHolder.init(context as Application)
            LogManager.init(diskPath)
        }

        /**
         * 清空所有日志
         */
        fun clearAllFiles() = LogManager.clearAll()

        fun getAllFiles(): List<File>? {
            return LogManager.getAllFiles()
        }
    }
}
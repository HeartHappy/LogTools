package com.hearthappy.log

import android.app.Application
import android.content.Context
import com.hearthappy.log.core.ContextHolder
import com.hearthappy.log.core.LogExportManager
import com.hearthappy.log.core.LogFileManager
import com.hearthappy.log.core.LogOutputter
import com.hearthappy.log.core.LogOutputterManager
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
 *   LoggerX.COMMON.d("普通日志")
 *   LoggerX.IMPORTANT.e("重要错误日志", Throwable("异常"))
 *   LoggerX.KERNEL.i("核心日志")
 *   LoggerX.ERROR.w("错误域警告日志") // 新增的Error域
 *   支持：
 *   1、拦截器：拦截日志，自定义写入方式
 *   2、查询日志：支持高级查询、智能去重查询
 *   3、删除指定Scope的所有数据，删除旧数据
 *   4、自动清理日志：支持按时间清理和按文件大小清理
 */
class LoggerX {
    companion object {
        internal const val TAG = "LoggerX"

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
        fun registerScope(logInterceptor: LogInterceptor, vararg scopes: LogScope) {
            for (scope in scopes) {
                LogOutputterManager.registerOutputter(scope.getTag(), LogOutputter(scope = scope, logInterceptor))
            }
        }

        fun createScope(customScope: String): LogScopeProxy {
            return LogOutputterManager.create(customScope)
        }


        /**
         * 全局初始化（Application中调用）
         */
        fun init(context: Context, outputConfig: OutputConfig = OutputConfig()) {
            ContextHolder.init(context as Application)
            LogFileManager.init(outputConfig.fileConfig)
            LogOutputterManager.initDefaultOutputters(outputConfig)


        }

        fun getScopes(): List<String> {
            return LogOutputterManager.getScopes().toList()
        }

        fun getOutputters(): List<LogOutputter> {
            return LogOutputterManager.getOutputters().toList()
        }


        /**
         * 清空所有日志,包括文件和数据库
         */
        fun clear() {
            LogFileManager.clearAll()
            LogDbManager.clearAllLogs()
        }

        fun getAllFiles(): List<File>? {
            return LogFileManager.getAllFiles()
        }

        /**
         * 导出并分享所有作用域的日志文件
         * @param exportAll 是否导出所有记录（true：不限制数量，false：按 limit 导出）
         * @param limit 每个作用域导出的条数限制（仅在 exportAll 为 false 时生效）
         * @param onProgress 总体导出进度回调 (0..100)
         */
        fun exportAndShareAll(exportAll: Boolean = true, limit: Int = 1000, onProgress: ((Int) -> Unit)? = null) {
            LogExportManager.exportAll(exportAll, limit, onProgress)
        }

        fun getDbFileSize(): Double {
            return LogDbManager.getDbFileSize()
        }

        /**
         * 开启数据库日志自动清理功能
         * @param retentionDays 保留天数，超过该天数的数据将被清理
         */
        fun enableAutoClean(retentionDays: Int) {
            LogDbManager.startAutoCleanByDate(retentionDays)
        }

        /**
         * 开启数据库日志自动清理功能（按文件大小）
         * @param maxSizeMb 数据库文件最大允许大小 (MB)
         * @param cleanSizeMb 每次清理尝试减少的大小 (MB)
         */
        fun enableAutoClean(maxSizeMb: Double, cleanSizeMb: Double) {
            LogDbManager.startAutoCleanBySize(maxSizeMb, cleanSizeMb)
        }

    }
}
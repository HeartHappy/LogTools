package com.hearthappy.log.core

import java.io.File

/**
 * 日志域（Scope）核心接口：开放扩展能力
 * 原有枚举的替代方案，支持自定义实现
 */
interface LogScope {
    // 获取Scope的标签（用于日志文件命名/格式化）
    fun getTag() : String

    // 获取Scope的代理对象
    fun getProxy() : LogScopeProxy

    fun delete(time : String) : Int

    fun delete() : Int


    /**
     * 导出作用域的日志文件并分享
     */
    fun doExportAndShare()

    /**
     * 导出单个作用域的日志文件
     * @param exportAll 是否导出所有记录（true：不限制数量，false：按 limit 导出）
     * @param limit 导出的条数限制（仅在 exportAll 为 false 时生效）
     * @param format 导出格式（CSV/TXT）
     * @param onProgress 导出进度回调 (0..100)
     * @param onFileReady 文件就绪回调，接收导出的文件
     */
    fun exportFile(
        exportAll: Boolean = true,
        limit: Int = 1000,
        format: LogExportManager.ExportFormat = LogExportManager.ExportFormat.CSV,
        onProgress: ((Int) -> Unit)? = null,
        onFileReady: ((File?) -> Unit)? = null
    )
}





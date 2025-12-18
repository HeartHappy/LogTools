package com.hearthappy.log.core

import android.app.Application
import android.os.Environment
import android.util.Log
import com.hearthappy.log.Logger
import com.hearthappy.log.interceptor.LogInterceptorAdapter
import com.hearthappy.logs.BuildConfig
import java.io.File
import java.util.regex.Pattern

/**
 * 日志管理器：单例管理所有Scope的输出器，统一初始化，支持扩展
 */
internal object LogManager {
    const val TAG = "Logger"
    private var diskPath: String? = null
    private val outputterMap = mutableMapOf<String, LogOutputter>()


    /**
     * 全局初始化（Application中调用）
     */
    fun init(diskPath: String? = null) {
        this.diskPath = diskPath
        LogContextCollector.init(ContextHolder.getAppContext() as Application)
        initDefaultOutputters()
    }

    /**
     * 初始化默认Scope输出器：新增Scope只需在此添加
     */
    private fun initDefaultOutputters() {
        outputterMap[Logger.COMMON.getTag()] = LogOutputter(scope = Logger.COMMON, context = ContextHolder.getAppContext(), diskPath = diskPath, object : LogInterceptorAdapter() {
            override fun isDebug(): Boolean = BuildConfig.DEBUG
            override fun isWriteFile(): Boolean = true
        })
        outputterMap[Logger.IMPORTANT.getTag()] = LogOutputter(scope = Logger.IMPORTANT, context = ContextHolder.getAppContext(), diskPath = diskPath, object : LogInterceptorAdapter() {
            override fun isDebug(): Boolean = BuildConfig.DEBUG
            override fun isWriteFile(): Boolean = true
        })
        outputterMap[Logger.KERNEL.getTag()] = LogOutputter(scope = Logger.KERNEL, context = ContextHolder.getAppContext(), diskPath = diskPath, object : LogInterceptorAdapter() {
            override fun isDebug(): Boolean = BuildConfig.DEBUG
            override fun isWriteFile(): Boolean = true
        })
        outputterMap[Logger.ERROR.getTag()] = LogOutputter(scope = Logger.ERROR, context = ContextHolder.getAppContext(), diskPath = diskPath, object : LogInterceptorAdapter() {
            override fun isDebug(): Boolean = BuildConfig.DEBUG
            override fun isWriteFile(): Boolean = true
        })
    }

    /**
     * 简易工厂方法：快速创建自定义Scope（开发者无需实现接口）
     */
    internal fun create(customScope: String): LogScopeProxy {
        require(customScope.isNotBlank()) { "Scope标签不能为空" }
        return LogScopeProxy(customScope)
    }

    /**
     * 扩展自定义Scope输出器（动态新增）
     */
    internal fun registerOutputter(scope: String, outputter: LogOutputter) {
        outputterMap[scope] = outputter
    }

    /**
     * 获取Scope对应的输出器
     */
    internal fun getOutputter(scope: String): LogOutputter {
        return outputterMap[scope] ?: throw IllegalArgumentException("未注册的Scope：${scope}")
    }

    /**
     * 清空所有日志文件
     */

    internal fun clear(scope: String): Boolean {
        val diskPath = this.diskPath ?: Environment.getExternalStorageDirectory().absolutePath
        val folder = diskPath.plus(File.separatorChar).plus("logger")
        val results = arrayListOf<Boolean>()
        val map = File(folder).listFiles()?.mapNotNull { if (it.name.contains(scope)) it else null }
        map?.forEachIndexed { _, file ->
            results.add(file.delete())
            Log.i(TAG, "Successfully deleted the $scope scope.")
        } ?: Log.i(TAG, "Failed to delete $scope file; relevant file not found.")
        val find = results.find { !it }
        return find ?: true
    }

    internal fun clearAll(): Boolean {
        val diskPath = this.diskPath ?: Environment.getExternalStorageDirectory().absolutePath
        val folder = diskPath.plus(File.separatorChar).plus("logger")
        val results = arrayListOf<Boolean>()
        File(folder).listFiles()?.forEach { results.add(it.delete()) }
        val find = results.find { !it }
        return find ?: true
    }

    internal fun getListFile(scope: String): List<File>? {
        val diskPath = this.diskPath ?: Environment.getExternalStorageDirectory().absolutePath
        val folder = diskPath.plus(File.separator).plus("logger/")
        val targetDir = File(folder)
        val files = targetDir.listFiles { file ->
            file.isFile && file.name.startsWith(scope.plus("_log_"))
        }
        return files?.toList()
    }

    internal fun getAllFiles(): List<File>? {
        val folder = getDirectory()
        val targetDir = File(folder)
        val files = targetDir.listFiles()
        return files?.toList()
    }

    internal fun getDirectory(): String {
        val diskPath = this.diskPath ?: Environment.getExternalStorageDirectory().absolutePath
        return diskPath.plus(File.separator).plus("logger/")
    }

    /**
     * 删除最旧的单个文件（保证至少保留1个文件）
     * 逻辑：
     * - 若文件数 ≤ 1：不删除任何文件（避免日志为空）
     * - 若文件数 > 1：仅删除最旧的1个文件
     */
    internal fun deleteOldestSingleFile(scope: String): Boolean {
        synchronized(this) { // 加同步锁，避免并发问题
            val diskPath = this.diskPath ?: Environment.getExternalStorageDirectory().absolutePath
            val folderPath = diskPath.plus(File.separator).plus("logger/")
            val filePrefix = scope.plus("_log_")
            val fileSuffix = ".csv"
            val fileNumPattern = Pattern.compile("${filePrefix}(\\d+)${fileSuffix}")
            val folder = File(folderPath)
            if (!folder.exists()) return false

            // 扫描并排序文件（按序号升序，最旧的在前）
            val logFiles = folder.listFiles { file ->
                file.isFile && file.name.matches(Regex("^${filePrefix}\\d+${fileSuffix}$"))
            } ?: emptyArray()

            val sortedFiles = logFiles.sortedBy { file ->
                val matcher = fileNumPattern.matcher(file.name)
                if (matcher.find()) matcher.group(1)?.toIntOrNull() ?: Int.MAX_VALUE
                else Int.MAX_VALUE
            } // 仅当文件数 > 1时，删除最旧的1个
            if (sortedFiles.size > 1) {
                val oldestFile = sortedFiles.first()
                if (oldestFile.exists()) {
                    oldestFile.delete()
                    Log.i(TAG, "删除最旧的单个日志文件：${oldestFile.name}")
                    return true
                }
            } else {
                Log.i(TAG, "当前仅保留1个日志文件，${scope} 不执行删除")
            }
            return false
        }
    }


}
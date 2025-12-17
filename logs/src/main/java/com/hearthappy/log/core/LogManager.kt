package com.hearthappy.log.core

import android.app.Application
import android.os.Environment
import com.hearthappy.logs.BuildConfig
import com.hearthappy.log.interceptor.LogInterceptorAdapter
import java.io.File

/**
 * 日志管理器：单例管理所有Scope的输出器，统一初始化，支持扩展
 */
object LogManager {
    const val TAG = "Logger"
    private var diskPath: String? = null
    private val outputterMap = mutableMapOf<LogScope, LogOutputter>()


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
        outputterMap[LogScope.COMMON] = LogOutputter(scope = LogScope.COMMON, context = ContextHolder.getAppContext(), diskPath = diskPath, object : LogInterceptorAdapter() {
            override fun isDebug(): Boolean = BuildConfig.DEBUG
            override fun isWriteFile(): Boolean = !BuildConfig.DEBUG
        })
        outputterMap[LogScope.IMPORTANT] = LogOutputter(scope = LogScope.IMPORTANT, context = ContextHolder.getAppContext(), diskPath = diskPath, object : LogInterceptorAdapter() {
            override fun isDebug(): Boolean = BuildConfig.DEBUG
            override fun isWriteFile(): Boolean = !BuildConfig.DEBUG
        })
        outputterMap[LogScope.KERNEL] = LogOutputter(scope = LogScope.KERNEL, context = ContextHolder.getAppContext(), diskPath = diskPath, object : LogInterceptorAdapter() {
            override fun isDebug(): Boolean = BuildConfig.DEBUG
            override fun isWriteFile(): Boolean = true
        })
        outputterMap[LogScope.ERROR] = LogOutputter(scope = LogScope.ERROR, context = ContextHolder.getAppContext(), diskPath = diskPath, object : LogInterceptorAdapter() {
            override fun isDebug(): Boolean = BuildConfig.DEBUG
            override fun isWriteFile(): Boolean = !BuildConfig.DEBUG
        })
    }

    /**
     * 扩展自定义Scope输出器（动态新增）
     */
    fun registerOutputter(scope: LogScope, outputter: LogOutputter) {
        outputterMap[scope] = outputter
    }

    /**
     * 获取Scope对应的输出器
     */
    fun getOutputter(scope: LogScope): LogOutputter {
        return outputterMap[scope] ?: throw IllegalArgumentException("未注册的Scope：${scope.tag}")
    }

    /**
     * 清空所有日志文件
     */

    fun clear(scope: String): Boolean {
        val diskPath = this.diskPath?:Environment.getExternalStorageDirectory().absolutePath
        val folder = diskPath + File.separatorChar + "logger"
        val results = arrayListOf<Boolean>()
        val map = File(folder).listFiles()?.mapNotNull { if (it.name.contains(scope)) it else null }
        map?.forEachIndexed { _, file ->
            results.add(file.delete())
            println("$TAG,delete $scope succeed")
        } ?: println("$TAG,delete $scope fail,not found")
        val find = results.find { !it }
        return find ?: true
    }

    fun clearAll(): Boolean {
        val diskPath = this.diskPath?:Environment.getExternalStorageDirectory().absolutePath
        val folder = diskPath + File.separatorChar + "logger"
        val results = arrayListOf<Boolean>()
        File(folder).listFiles()?.forEach { results.add(it.delete()) }
        val find = results.find { !it }
        return find ?: true
    }

    fun getListFile(scope: String): List<File>? {
        val diskPath = this.diskPath ?: Environment.getExternalStorageDirectory().absolutePath
        val folder = diskPath.plus(File.separator).plus("logger/")
        val targetDir = File(folder)
        val files = targetDir.listFiles { file ->
            file.isFile && file.name.startsWith(scope.plus("_log_"))
        }
        return files?.toList()
    }

    fun getAllFiles(): List<File>? {
        val diskPath = this.diskPath ?: Environment.getExternalStorageDirectory().absolutePath
        val folder = diskPath.plus(File.separator).plus("logger/")
        val targetDir = File(folder)
        val files = targetDir.listFiles()
        return files?.toList()
    }

    fun getDirectory(): String? {
        val diskPath = this.diskPath ?: Environment.getExternalStorageDirectory().absolutePath
        val folder = diskPath.plus(File.separator).plus("logger/")
        val targetDir = File(folder)
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        return targetDir.absolutePath
    }
}
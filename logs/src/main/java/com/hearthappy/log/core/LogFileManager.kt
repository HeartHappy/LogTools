package com.hearthappy.log.core

import android.app.Application
import android.os.Environment
import android.util.Log
import com.hearthappy.log.LoggerX
import java.io.File
import java.util.regex.Pattern

/**
 * 日志文件管理器：单例管理所有Scope的输出器，统一初始化，支持扩展
 */
internal object LogFileManager {

    private var diskPath: String? = null


    /**
     * 全局初始化（Application中调用）
     */
    fun init(fileConfig: FileConfig?) {
        this.diskPath = fileConfig?.diskPath
        LogContextCollector.init(ContextHolder.getAppContext() as Application)

    }


    internal fun getDiskPath(): String {
        return diskPath ?: Environment.getExternalStorageDirectory().absolutePath
    }

    /**
     * 清空所有日志文件
     */

    internal fun clear(scope: String): Boolean {
        val folder = getDiskPath().plus(File.separatorChar).plus("logger")
        val results = arrayListOf<Boolean>()
        val map = File(folder).listFiles()?.mapNotNull { if (it.name.contains(scope)) it else null }
        map?.forEachIndexed { _, file ->
            results.add(file.delete())
            Log.i(LoggerX.TAG, "Successfully deleted the $scope scope.")
        } ?: Log.i(LoggerX.TAG, "Failed to delete $scope file; relevant file not found.")
        val find = results.find { !it }
        return find ?: true
    }

    internal fun clearAll(): Boolean {
        val folder = getDiskPath().plus(File.separatorChar).plus("logger")
        val results = arrayListOf<Boolean>()
        File(folder).listFiles()?.forEach { results.add(it.delete()) }
        val find = results.find { !it }
        return find ?: true
    }

    internal fun getListFile(scope: String): List<File>? {
        val folder = getDiskPath().plus(File.separator).plus("logger/")
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
        return getDiskPath().plus(File.separator).plus("logger/")
    }

    /**
     * 删除最旧的单个文件（保证至少保留1个文件）
     * 逻辑：
     * - 若文件数 ≤ 1：不删除任何文件（避免日志为空）
     * - 若文件数 > 1：仅删除最旧的1个文件
     */
    internal fun deleteOldestSingleFile(scope: String): Boolean {
        synchronized(this) { // 加同步锁，避免并发问题
            val folderPath = getDiskPath().plus(File.separator).plus("logger/")
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
                    Log.i(LoggerX.TAG, "删除最旧的单个日志文件：${oldestFile.name}")
                    return true
                }
            } else {
                Log.i(LoggerX.TAG, "当前仅保留1个日志文件，${scope} 不执行删除")
            }
            return false
        }
    }

}
package com.hearthappy.logs

import android.content.Context
import android.os.Environment
import java.io.File

open class LogTools {
    private var context: Context? = null
    private var diskPath: String? = null

    companion object {
        private val logTools by lazy { LogTools() }

        //公共
        val common by lazy {
            Log("Common", logTools.context, logTools.diskPath).apply {
                this.interceptor = object : LogInterceptorAdapter() {
                    override fun isDebug(): Boolean = BuildConfig.DEBUG
                }
            }
        }

        //重要
        val important by lazy {
            Log("Important", logTools.context, logTools.diskPath).apply {
                this.interceptor = object : LogInterceptorAdapter() {
                    override fun isWriteFile(): Boolean = !BuildConfig.DEBUG
                }
            }
        }

        //核心
        val kernel by lazy { Log("Kernel", logTools.context, logTools.diskPath) }


        fun install(context: Context, diskPath: String? = null) {
            logTools.context = context
            logTools.diskPath = diskPath
        }

        fun getListFile(scope: String): List<File>? {
            val diskPath = logTools.diskPath ?: Environment.getExternalStorageDirectory().absolutePath
            val folder = diskPath.plus(File.separator).plus("logger/")
            val targetDir = File(folder)
            val files = targetDir.listFiles { file ->
                file.isFile && file.name.startsWith(scope.plus("_log_"))
            }
            return files?.toList()
        }

        fun getDirectory(): String? {
            val diskPath = logTools.diskPath ?: Environment.getExternalStorageDirectory().absolutePath
            val folder = diskPath.plus(File.separator).plus("logger/")
            val targetDir = File(folder)
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }
            return targetDir.absolutePath
        }
    }
}
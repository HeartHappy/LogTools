package com.hearthappy.log.strategy

import android.os.Handler
import android.os.Looper
import android.os.Message
import com.hearthappy.log.core.Utils
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.util.*
import java.util.regex.Pattern

/**
 * 优化文件轮转逻辑：
 * 1. 基于目录中最大序号续写/创建文件，删除旧文件后序号不重置
 * 2. 文件序号递增（0→1→2→3→4...），始终保证序号越大，日志越新
 * 3. 支持按序号删除最旧文件，新文件延续最大序号+1
 */
class DiskLogStrategy(handler: Handler) : LogStrategy {
    private val handler: Handler = Utils.checkNotNull(handler)

    override fun log(priority: Int, tag: String?, message: String) {
        Utils.checkNotNull(message)
        handler.sendMessage(handler.obtainMessage(priority, message))
    }

    internal class WriteHandler(
        looper: Looper,
        folder: String,
        private val maxFileSize: Int, // 单个文件最大大小（500KB）
        private val scope: String
    ) : Handler(Utils.checkNotNull(looper)) {
        private val folder: String = Utils.checkNotNull(folder)
        // 日志文件前缀（如：Kernel_log_）
        private val filePrefix: String by lazy { "${scope}_log_" }
        // 日志文件后缀
        private val fileSuffix = ".csv"
        // 匹配文件序号的正则（如从Kernel_log_2.csv中提取2）
        private val fileNumPattern = Pattern.compile("${filePrefix}(\\d+)${fileSuffix}")

        override fun handleMessage(msg: Message) {
            val content = msg.obj as String
            val logFile = getLogFile() // 优化后的文件获取逻辑
            try {
                FileWriter(logFile, true).use {
                    writeLog(it, content)
                    it.flush()
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        /**
         * 写入日志内容（原有逻辑保留）
         */
        @Throws(IOException::class)
        private fun writeLog(fileWriter: FileWriter, content: String) {
            Utils.checkNotNull(fileWriter)
            Utils.checkNotNull(content)
            fileWriter.append(content)
        }

        /**
         * 优化后的文件获取逻辑：
         * 1. 扫描目录下所有同前缀文件，提取序号并排序
         * 2. 找到最大序号的文件，检查大小是否超限
         * 3. 未超限则续写，超限则创建「最大序号+1」的新文件
         * 4. 无文件时从0开始创建
         */
        private fun getLogFile(): File {
            val folder = File(folder)
            if (!folder.exists()) {
                folder.mkdirs()
            }

            // 步骤1：扫描目录下所有同前缀的日志文件
            val logFiles = folder.listFiles { file ->
                file.isFile && file.name.matches(Regex("^${filePrefix}\\d+${fileSuffix}$"))
            } ?: emptyArray()

            // 步骤2：提取文件序号并排序，找到最大序号
            val fileNumbers = mutableListOf<Int>()
            logFiles.forEach { file ->
                val matcher = fileNumPattern.matcher(file.name)
                if (matcher.find()) {
                    val num = matcher.group(1)?.toIntOrNull()
                    num?.let { fileNumbers.add(it) }
                }
            }
            val maxFileNum = if (fileNumbers.isNotEmpty()) fileNumbers.max() else -1

            // 步骤3：确定要写入的文件
            val targetFile: File = if (maxFileNum == -1) {
                // 无文件，创建0号文件
                File(folder, "${filePrefix}0${fileSuffix}").apply {
                    if (!exists()) createNewFile()
                }
            } else {
                // 有文件，检查最大序号文件的大小
                val currentMaxFile = File(folder, "${filePrefix}${maxFileNum}${fileSuffix}")
                if (currentMaxFile.length() < maxFileSize) {
                    // 未超限，继续写入最大序号文件
                    currentMaxFile
                } else {
                    // 超限，创建最大序号+1的新文件
                    File(folder, "${filePrefix}${maxFileNum + 1}${fileSuffix}").apply {
                        createNewFile()
                    }
                }
            }

            return targetFile
        }

        /**
         * 删除最旧的文件（按序号从小到大删除，即最先创建的文件）
         * @param keepCount 保留的最新文件数量，超出则删除最旧的
         * 边界说明：
         * - 当文件数 ≤ keepCount时，不删除任何文件（保证至少保留1个）
         * - 当文件数 > keepCount时，仅删除超出部分（最旧的）
         */
        fun deleteOldestFiles(keepCount: Int) {
            synchronized(this) { // 加同步锁，避免并发问题
                val folder = File(folder)
                if (!folder.exists()) return

                // 扫描并排序文件（按序号升序，最旧的在前）
                val logFiles = folder.listFiles { file ->
                    file.isFile && file.name.matches(Regex("^${filePrefix}\\d+${fileSuffix}$"))
                } ?: emptyArray()

                val sortedFiles = logFiles.sortedBy { file ->
                    val matcher = fileNumPattern.matcher(file.name)
                    if (matcher.find()) matcher.group(1)?.toIntOrNull() ?: Int.MAX_VALUE
                    else Int.MAX_VALUE
                }

                // 仅当文件数 > 保留数量时，才删除超出部分
                if (sortedFiles.size > keepCount) {
                    val filesToDelete = sortedFiles.subList(0, sortedFiles.size - keepCount)
                    filesToDelete.forEach { file ->
                        if (file.exists()) {
                            file.delete()
                            android.util.Log.d("DiskLog", "删除旧日志文件：${file.name}")
                        }
                    }
                }
            }
        }

        /**
         * 新增：删除最旧的单个文件（保证至少保留1个文件）
         * 逻辑：
         * - 若文件数 ≤ 1：不删除任何文件（避免日志为空）
         * - 若文件数 > 1：仅删除最旧的1个文件
         */
        fun deleteOldestSingleFile() {
            synchronized(this) { // 加同步锁，避免并发问题
                val folder = File(folder)
                if (!folder.exists()) return

                // 扫描并排序文件（按序号升序，最旧的在前）
                val logFiles = folder.listFiles { file ->
                    file.isFile && file.name.matches(Regex("^${filePrefix}\\d+${fileSuffix}$"))
                } ?: emptyArray()

                val sortedFiles = logFiles.sortedBy { file ->
                    val matcher = fileNumPattern.matcher(file.name)
                    if (matcher.find()) matcher.group(1)?.toIntOrNull() ?: Int.MAX_VALUE
                    else Int.MAX_VALUE
                }

                // 仅当文件数 > 1时，删除最旧的1个
                if (sortedFiles.size > 1) {
                    val oldestFile = sortedFiles.first()
                    if (oldestFile.exists()) {
                        oldestFile.delete()
                        android.util.Log.d("DiskLog", "删除最旧的单个日志文件：${oldestFile.name}")
                    }
                } else {
                    android.util.Log.d("DiskLog", "当前仅保留1个日志文件，不执行删除")
                }
            }
        }
    }
}
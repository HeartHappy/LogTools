package com.hearthappy.logs

import android.os.Handler
import android.os.Looper
import android.os.Message
import java.io.File
import java.io.FileWriter
import java.io.IOException

/**
 * Abstract class that takes care of background threading the file log operation on Android.
 * implementing classes are free to directly perform I/O operations there.
 *
 * Writes all logs to the disk with CSV format.
 */
class DiskLogStrategy(handler: Handler) : LogStrategy {
    private val handler: Handler = Utils.checkNotNull(handler)

    override fun log(level: Int, tag: String?, message: String) {
        Utils.checkNotNull(message) // do nothing on the calling thread, simply pass the tag/msg to the background thread
        handler.sendMessage(handler.obtainMessage(level, message))
    }

    internal class WriteHandler(looper: Looper, folder: String, private val maxFileSize: Int, private val scope: String) : Handler(Utils.checkNotNull(looper)) {
        private val folder: String = Utils.checkNotNull(folder)

        override fun handleMessage(msg: Message) {
            val content = msg.obj as String
            val logFile = getLogFile(folder, scope.plus("_log"))
            FileWriter(logFile, true).use {
                writeLog(it, content)
                it.flush()
            }
        }

        /**
         * This is always called on a single background thread.
         * Implementing classes must ONLY write to the fileWriter and nothing more.
         * The abstract class takes care of everything else including close the stream and catching IOException
         *
         * @param fileWriter an instance of FileWriter already initialised to the correct file
         */
        @Throws(IOException::class) private fun writeLog(fileWriter: FileWriter, content: String) {
            Utils.checkNotNull(fileWriter)
            Utils.checkNotNull(content)
            fileWriter.append(content)
        }

        private fun getLogFile(folderName: String, fileName: String): File {
            Utils.checkNotNull(folderName)
            Utils.checkNotNull(fileName)
            val folder = File(folderName)
            if (!folder.exists()) {
                folder.mkdirs()
            }


            var newFileCount = 0
            val file = File(folder, String.format("%s_%s.csv", fileName, newFileCount))
            if (!file.exists()) {
                file.createNewFile()
            } else {
                if (file.length() >= maxFileSize) {
                    newFileCount++
                    return File(folder, String.format("%s_%s.csv", fileName, newFileCount))
                }
            }
            return file/*newFile = File(folder, String.format("%s_%s.csv", fileName, newFileCount))
            while (newFile.exists()) {
                existingFile = newFile
                newFileCount++
                newFile = File(folder, String.format("%s_%s.csv", fileName, newFileCount))
            }
            return existingFile?.run {
                if (this.length() >= maxFileSize) newFile else existingFile
            } ?: newFile*/
        }
    }

}
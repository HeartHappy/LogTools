package com.hearthappy.log.core

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object LogShareManager {
    fun shareLogFile(context: Context, file: File, mimeType: String = "text/csv") {
//        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileProvider", file)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.loggerx.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "分享日志文件").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    /**
     * 分享多个日志文件
     */
    fun shareLogFiles(context: Context, files: List<File>) {
        if (files.isEmpty()) return
        if (files.size == 1) {
            shareLogFile(context, files[0])
            return
        }

        val uris = files.map {
            FileProvider.getUriForFile(context, "${context.packageName}.loggerx.fileprovider", it)
//            FileProvider.getUriForFile(context, "${context.packageName}.fileProvider", it)
        }

        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "text/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "分享所有日志文件").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}

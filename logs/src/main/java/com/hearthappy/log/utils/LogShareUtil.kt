package com.hearthappy.log.utils

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object LogShareUtil {
    fun shareLogFile(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, "分享日志文件"))
    }
}
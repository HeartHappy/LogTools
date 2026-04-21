package com.hearthappy.loggerx.core

import android.content.Context
import android.util.Base64OutputStream
import android.util.Log
import com.hearthappy.loggerx.LoggerX
import com.hearthappy.loggerx.db.LogDbManager
import com.hearthappy.loggerx.BuildConfig
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

object LogExportManager {
    enum class ExportFormat(val extension: String, val mimeType: String) {
        CSV("csv", "text/csv"),
        TXT("txt", "text/plain")
    }

    private const val EXPORT_PAGE_SIZE = 100
    private const val MAX_EXPORT_RETRY = 3
    private val headerColumns = listOf(
        LoggerX.COLUMN_ID,
        LoggerX.COLUMN_TIME,
        LoggerX.COLUMN_LEVEL,
        LoggerX.COLUMN_TAG,
        LoggerX.COLUMN_METHOD,
        LoggerX.COLUMN_MESSAGE,
        LoggerX.COLUMN_FILE_PATH,
        "file_base64"
    )

    fun export(
        context: Context,
        scopeTag: String,
        logs: List<Map<String, Any>>,
        format: ExportFormat = ExportFormat.CSV,
        onProgress: ((Int) -> Unit)? = null
    ): File? {
        val exportFile = createExportFile(context, scopeTag, format)
        return runCatching {
            OutputStreamWriter(FileOutputStream(exportFile), StandardCharsets.UTF_8).use { writer ->
                appendHeader(writer, format)
                val total = logs.size.coerceAtLeast(1)
                logs.forEachIndexed { index, row ->
                    appendRow(writer, row, format)
                    onProgress?.invoke(((index + 1) * 100 / total).coerceIn(0, 100))
                }
                if (logs.isEmpty()) {
                    onProgress?.invoke(100)
                }
            }
            exportFile
        }.onFailure {
            exportFile.delete()
            Log.e(LoggerX.TAG, "export failed: ${it.message}")
        }.getOrNull()
    }

    internal fun exportScopeAndShare(
        scopeTag: String,
        exportAll: Boolean,
        limit: Int,
        format: ExportFormat = ExportFormat.CSV,
        onProgress: ((Int) -> Unit)? = null
    ) {
        Thread {
            val file = exportScopePaged(ContextHolder.getAppContext(), scopeTag, exportAll, limit, format, onProgress)
            if (file != null) {
                LogShareManager.shareLogFile(ContextHolder.getAppContext(), file, format.mimeType)
            }
        }.start()
    }

    internal fun exportAll(
        exportAll: Boolean,
        limit: Int,
        format: ExportFormat = ExportFormat.CSV,
        onProgress: ((Int) -> Unit)? = null
    ) {
        Thread {
            val scopes = LogOutputterManager.getScopes().toList()
            if (scopes.isEmpty()) return@Thread
            val exportedFiles = mutableListOf<File>()
            val totalScopes = scopes.size.coerceAtLeast(1)
            scopes.forEachIndexed { index, scope ->
                val startPercent = index * 100 / totalScopes
                val endPercent = (index + 1) * 100 / totalScopes
                val file = exportScopePaged(
                    context = ContextHolder.getAppContext(),
                    scopeTag = scope,
                    exportAll = exportAll,
                    limit = limit,
                    format = format
                ) { progress ->
                    val overall = startPercent + ((endPercent - startPercent) * progress / 100)
                    onProgress?.invoke(overall.coerceIn(0, 100))
                }
                if (file != null) {
                    exportedFiles += file
                }
            }
            if (exportedFiles.isNotEmpty()) {
                onProgress?.invoke(100)
                LogShareManager.shareLogFiles(ContextHolder.getAppContext(), exportedFiles)
            }
        }.start()
    }

    fun exportAllFiles(
        exportAll: Boolean,
        limit: Int,
        format: ExportFormat = ExportFormat.CSV,
        onProgress: ((Int) -> Unit)? = null,
        onFilesReady: ((List<File>) -> Unit)? = null
    ) {
        Thread {
            val scopes = LogOutputterManager.getScopes().toList()
            if (scopes.isEmpty()) {
                onFilesReady?.invoke(emptyList())
                return@Thread
            }
            val exportedFiles = mutableListOf<File>()
            val totalScopes = scopes.size.coerceAtLeast(1)
            scopes.forEachIndexed { index, scope ->
                val startPercent = index * 100 / totalScopes
                val endPercent = (index + 1) * 100 / totalScopes
                val file = exportScopePaged(
                    context = ContextHolder.getAppContext(),
                    scopeTag = scope,
                    exportAll = exportAll,
                    limit = limit,
                    format = format
                ) { progress ->
                    val overall = startPercent + ((endPercent - startPercent) * progress / 100)
                    onProgress?.invoke(overall.coerceIn(0, 100))
                }
                if (file != null) {
                    exportedFiles += file
                }
            }
            onProgress?.invoke(100)
            onFilesReady?.invoke(exportedFiles)
        }.start()
    }

    internal fun exportScopePaged(
        context: Context = ContextHolder.getAppContext(),
        scopeTag: String,
        exportAll: Boolean,
        limit: Int,
        format: ExportFormat,
        onProgress: ((Int) -> Unit)? = null
    ): File? {
        val exportFile = createExportFile(context, scopeTag, format)
        return runCatching {
            OutputStreamWriter(FileOutputStream(exportFile), StandardCharsets.UTF_8).use { writer ->
                appendHeader(writer, format)
                var page = 1
                var processed = 0
                var targetTotal = if (exportAll) Int.MAX_VALUE else limit.coerceAtLeast(0)
                while (true) {
                    val currentPageSize = if (exportAll) {
                        EXPORT_PAGE_SIZE
                    } else {
                        minOf(EXPORT_PAGE_SIZE, (targetTotal - processed).coerceAtLeast(0))
                    }
                    if (currentPageSize == 0) break
                    val pageResult = retry("query-page-$page") {
                        LogDbManager.queryLogsPageAdvanced(
                            scopeTag = scopeTag,
                            page = page,
                            limit = currentPageSize)
                    }
                    if (page == 1) {
                        targetTotal = if (exportAll) pageResult.totalCount else minOf(limit, pageResult.totalCount)
                    }
                    if (pageResult.rows.isEmpty()) {
                        break
                    }
                    pageResult.rows.forEach { row ->
                        appendRow(writer, row, format)
                        processed++
                        val progress = if (targetTotal <= 0) 100 else (processed * 100 / targetTotal).coerceIn(0, 100)
                        onProgress?.invoke(progress)
                    }
                    if (!pageResult.hasMore || processed >= targetTotal) {
                        break
                    }
                    page++
                }
                if (processed == 0) {
                    onProgress?.invoke(100)
                }
                writer.flush()
            }
            exportFile
        }.onFailure {
            exportFile.delete()
            Log.e(LoggerX.TAG, "exportScopePaged failed(scope=$scopeTag): ${it.message}")
        }.getOrNull()
    }

    private fun createExportFile(context: Context, scopeTag: String, format: ExportFormat): File {
        val safeScope = scopeTag.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val fileName = "${safeScope}-Log.${format.extension}"
        val exportDir = runCatching { FileLogStorageManager.resolveExportDir() }.getOrElse { context.cacheDir }
        return File(exportDir, fileName)
    }

    private fun appendHeader(writer: OutputStreamWriter, format: ExportFormat) {
        when (format) {
            ExportFormat.CSV -> writer.append(headerColumns.joinToString(",") { quoteCsv(it) }).append("\r\n")
            ExportFormat.TXT -> writer.append(headerColumns.joinToString("\t")).append("\r\n")
        }
    }

    private fun appendRow(writer: OutputStreamWriter, row: Map<String, Any>, format: ExportFormat) {
        val baseFields = listOf(
            row[LoggerX.COLUMN_ID].toText(),
            row[LoggerX.COLUMN_TIME].toText(),
            row[LoggerX.COLUMN_LEVEL].toText(),
            row[LoggerX.COLUMN_TAG].toText(),
            row[LoggerX.COLUMN_METHOD].toText(),
            row[LoggerX.COLUMN_MESSAGE].toText(),
            row[LoggerX.COLUMN_FILE_PATH].toText()
        )
        when (format) {
            ExportFormat.CSV -> {
                baseFields.forEachIndexed { index, value ->
                    if (index > 0) writer.append(',')
                    writer.append(quoteCsv(value))
                }
                writer.append(',')
                appendCsvDataUri(writer, row[LoggerX.COLUMN_FILE_PATH]?.toString())
                writer.append("\r\n")
            }
            ExportFormat.TXT -> {
                writer.append(baseFields.joinToString("\t") { sanitizeTxt(it) })
                writer.append('\t')
                appendTxtDataUri(writer, row[LoggerX.COLUMN_FILE_PATH]?.toString())
                writer.append("\r\n")
            }
        }
    }

    private fun appendCsvDataUri(writer: OutputStreamWriter, filePath: String?) {
        writer.append('"')
        appendDataUriBody(writer, filePath)
        writer.append('"')
    }

    private fun appendTxtDataUri(writer: OutputStreamWriter, filePath: String?) {
        appendDataUriBody(writer, filePath)
    }

    private fun appendDataUriBody(writer: OutputStreamWriter, filePath: String?) {
        if (filePath.isNullOrBlank()) return
        runCatching {
            val file = retry("read-file") { FileLogStorageManager.validateStoredFile(filePath) }
            val mimeType = FileLogStorageManager.detectMimeType(file)
            writer.append("data:")
            writer.append(mimeType)
            writer.append(";base64,")
            FileInputStream(file).use { input ->
                StreamingBase64Writer(writer).use { base64Out ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        base64Out.write(buffer, 0, read)
                    }
                }
            }
        }.onFailure {
            Log.e(LoggerX.TAG, "appendDataUriBody failed: ${it.message}")
        }
    }

    private fun quoteCsv(value: String): String {
        return "\"${value.replace("\"", "\"\"")}\""
    }

    private fun sanitizeTxt(value: String): String {
        return value.replace("\r", " ").replace("\n", "\\n")
    }

    private fun Any?.toText(): String = this?.toString().orEmpty()

    private fun <T> retry(name: String, block: () -> T): T {
        var lastError: Throwable? = null
        repeat(MAX_EXPORT_RETRY) { attempt ->
            try {
                return block()
            } catch (throwable: Throwable) {
                lastError = throwable
                if(BuildConfig.DEBUG){
                    Log.w(LoggerX.TAG, "retry $name failed at attempt=${attempt + 1}: ${throwable.message}")
                }
            }
        }
        throw IOException("operation failed after retry: $name", lastError)
    }

    private class StreamingBase64Writer(private val writer: OutputStreamWriter) : java.io.Closeable {
        private val output = object : java.io.OutputStream() {
            override fun write(b: Int) {
                writer.write(b)
            }

            override fun write(b: ByteArray, off: Int, len: Int) {
                writer.write(String(b, off, len, StandardCharsets.US_ASCII))
            }
        }
        private val base64Stream = Base64OutputStream(output, android.util.Base64.NO_WRAP)

        fun write(buffer: ByteArray, offset: Int, length: Int) {
            base64Stream.write(buffer, offset, length)
        }

        override fun close() {
            base64Stream.close()
        }
    }
}

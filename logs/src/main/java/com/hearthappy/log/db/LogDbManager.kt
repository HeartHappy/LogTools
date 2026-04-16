package com.hearthappy.log.db

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Base64
import android.util.Log
import com.hearthappy.log.LoggerX
import com.hearthappy.log.core.ContextHolder
import com.hearthappy.log.core.EncodedImageLog
import com.hearthappy.log.core.IImageCompressor
import com.hearthappy.log.core.ImageCompressionOptions
import com.hearthappy.log.core.ImageLogCodec
import com.hearthappy.log.core.ImageLogWriteException
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.nio.channels.FileChannel

object LogDbManager {
    data class ImageWriteTask(
        val scopeTag: String,
        val level: String,
        val classTag: String,
        val method: String,
        val message: String,
        val imageBytes: ByteArray,
        val compressor: IImageCompressor,
        val options: ImageCompressionOptions,
        val retry: Int = 0,
        val important: Boolean = false,
        val enqueueAtMs: Long = System.currentTimeMillis()
    )

    data class ImagePreviewData(
        val mimeType: String,
        val thumbnailBase64: String?,
        val compressedBase64: String?
    )

    data class QueryPageResult(
        val rows: List<Map<String, Any>>,
        val totalCount: Int,
        val page: Int,
        val limit: Int,
        val nextPage: Int?,
        val approxBytes: Int,
        val hasMore: Boolean,
        val queryPlan: List<String>
    )

    private data class PreparedImageWrite(
        val tableName: String,
        val level: String,
        val classTag: String,
        val method: String,
        val message: String,
        val encoded: EncodedImageLog,
        val sourceTask: ImageWriteTask? = null
    )

    private val dbHelper = LogDbHelper(ContextHolder.getAppContext())
    private val database = dbHelper.writableDatabase
    private val readDbLock = Any()
    @Volatile
    private var readOnlyDatabase: SQLiteDatabase? = null
    private val existedTables = HashSet<String>()
    private val scheduledExecutor = Executors.newSingleThreadScheduledExecutor()
    private const val IMAGE_TABLE = "log_image"
    private const val IMAGE_QUEUE_CAPACITY = 1500
    private const val IMAGE_BACKPRESSURE_THRESHOLD = 1000
    private const val IMAGE_BATCH_SIZE = 50
    private const val IMAGE_BATCH_FLUSH_MS = 200L
    private const val IMAGE_MAX_RETRY = 3
    private val imageWriteQueue = LinkedBlockingQueue<ImageWriteTask>(IMAGE_QUEUE_CAPACITY)
    private val imageBatchWriter = Executors.newSingleThreadExecutor()
    private val asyncAcceptedCount = AtomicLong(0)
    private val asyncSuccessCount = AtomicLong(0)
    private val asyncFailedCount = AtomicLong(0)
    private val asyncRetryCount = AtomicLong(0)
    private val asyncDroppedCount = AtomicLong(0)
    private val asyncOver50MsCount = AtomicLong(0)
    private val asyncOver100MsCount = AtomicLong(0)
    private val asyncMaxCostMs = AtomicLong(0)
    private val asyncBatchCount = AtomicLong(0)
    private val asyncBatchMaxSize = AtomicLong(0)

    private fun getTableName(scopeTag: String): String {
        val cleanTag = scopeTag.replace(Regex("[^a-zA-Z0-9_]"), "_")
        return "${cleanTag}_log"
    }

    init {
        startImageBatchWriter()
    }

    @Synchronized
    private fun ensureTable(tableName: String) {
        if (existedTables.contains(tableName)) return
        dbHelper.createLogTable(database, tableName)
        existedTables.add(tableName)
    }

    fun getDbFileSize(): Double = dbHelper.getDbFileSize()

    @Synchronized
    fun insertLog(scopeTag: String, level: String, classTag: String, method: String, message: String) {
        val tableName = getTableName(scopeTag)
        try {
            ensureTable(tableName)
            database.insertOrThrow(tableName, null, ContentValues().apply {
                put(LoggerX.COLUMN_LEVEL, level)
                put(LoggerX.COLUMN_TAG, classTag)
                put(LoggerX.COLUMN_METHOD, method)
                put(LoggerX.COLUMN_MESSAGE, message)
                put(LoggerX.COLUMN_THUMBNAIL, "")
                put(LoggerX.COLUMN_IMAGE_ID, -1)
            })
        } catch (e: Exception) {
            Log.e(LoggerX.TAG, "insertLog failed: ${e.message}")
        }
    }

    @Synchronized
    fun insertImageLog(
        scopeTag: String,
        level: String,
        classTag: String,
        method: String,
        message: String,
        imageBytes: ByteArray,
        compressor: IImageCompressor,
        options: ImageCompressionOptions
    ): Boolean {
        return insertImageLogInternal(
            scopeTag = scopeTag,
            level = level,
            classTag = classTag,
            method = method,
            message = message,
            imageBytes = imageBytes,
            compressor = compressor,
            options = options
        )
    }

    fun enqueueImageLog(
        scopeTag: String,
        level: String,
        classTag: String,
        method: String,
        message: String,
        imageBytes: ByteArray,
        compressor: IImageCompressor,
        options: ImageCompressionOptions,
        important: Boolean = false
    ): Boolean {
        val degradedOptions = if (imageWriteQueue.size >= IMAGE_BACKPRESSURE_THRESHOLD && !important) {
            options.copy(targetSizeKb = (options.targetSizeKb * 0.7f).toInt().coerceAtLeast(80))
        } else {
            options
        }
        if (imageWriteQueue.size >= IMAGE_QUEUE_CAPACITY - 1 && !important) {
            asyncDroppedCount.incrementAndGet()
            Log.w(LoggerX.TAG, "image queue full, dropping non-critical image log")
            return false
        }
        val accepted = imageWriteQueue.offer(
            ImageWriteTask(
                scopeTag = scopeTag,
                level = level,
                classTag = classTag,
                method = method,
                message = message,
                imageBytes = imageBytes,
                compressor = compressor,
                options = degradedOptions,
                important = important
            )
        )
        if (accepted) {
            asyncAcceptedCount.incrementAndGet()
        } else {
            asyncDroppedCount.incrementAndGet()
        }
        return accepted
    }

    fun getImageAsyncMetrics(): Map<String, Any> {
        return mapOf(
            "queueSize" to imageWriteQueue.size,
            "queueRemainingCapacity" to imageWriteQueue.remainingCapacity(),
            "acceptedCount" to asyncAcceptedCount.get(),
            "successCount" to asyncSuccessCount.get(),
            "failedCount" to asyncFailedCount.get(),
            "retryCount" to asyncRetryCount.get(),
            "droppedCount" to asyncDroppedCount.get(),
            "over50msCount" to asyncOver50MsCount.get(),
            "over100msCount" to asyncOver100MsCount.get(),
            "maxCostMs" to asyncMaxCostMs.get(),
            "batchCount" to asyncBatchCount.get(),
            "batchMaxSize" to asyncBatchMaxSize.get()
        )
    }

    @Synchronized
    private fun insertImageLogInternal(
        scopeTag: String,
        level: String,
        classTag: String,
        method: String,
        message: String,
        imageBytes: ByteArray,
        compressor: IImageCompressor,
        options: ImageCompressionOptions
    ): Boolean {
        val prepared = prepareImageWrite(
            scopeTag = scopeTag,
            level = level,
            classTag = classTag,
            method = method,
            message = message,
            imageBytes = imageBytes,
            compressor = compressor,
            options = options
        )
        return insertPreparedImageBatch(listOf(prepared))
    }

    private fun startImageBatchWriter() {
        imageBatchWriter.execute {
            while (!Thread.currentThread().isInterrupted) {
                runCatching {
                    val first = imageWriteQueue.take()
                    val batch = mutableListOf(first)
                    val deadline = System.currentTimeMillis() + IMAGE_BATCH_FLUSH_MS
                    while (batch.size < IMAGE_BATCH_SIZE) {
                        val remaining = deadline - System.currentTimeMillis()
                        if (remaining <= 0L) break
                        val next = imageWriteQueue.poll(remaining, TimeUnit.MILLISECONDS) ?: break
                        batch += next
                    }
                    processImageWriteBatch(batch)
                }.onFailure {
                    Log.e(LoggerX.TAG, "image batch writer failed: ${it.message}")
                }
            }
        }
    }

    private fun processImageWriteBatch(tasks: List<ImageWriteTask>) {
        if (tasks.isEmpty()) return
        val startNs = System.nanoTime()
        asyncBatchCount.incrementAndGet()
        asyncBatchMaxSize.updateAndGet { old -> maxOf(old, tasks.size.toLong()) }
        val prepared = mutableListOf<PreparedImageWrite>()
        val retryTasks = mutableListOf<ImageWriteTask>()
        tasks.forEach { task ->
            runCatching {
                prepareImageWrite(
                    scopeTag = task.scopeTag,
                    level = task.level,
                    classTag = task.classTag,
                    method = task.method,
                    message = task.message,
                    imageBytes = task.imageBytes,
                    compressor = task.compressor,
                    options = task.options,
                    sourceTask = task
                )
            }.onSuccess {
                prepared += it
            }.onFailure { throwable ->
                Log.e(LoggerX.TAG, "async image prepare failed(retry=${task.retry}): ${throwable.message}")
                if (task.retry + 1 < IMAGE_MAX_RETRY) {
                    asyncRetryCount.incrementAndGet()
                    retryTasks += task.copy(retry = task.retry + 1)
                } else {
                    asyncFailedCount.incrementAndGet()
                }
            }
        }
        val success = runCatching {
            if (prepared.isNotEmpty()) {
                val mmapFile = stagePreparedBatchWithMmap(prepared)
                try {
                    insertPreparedImageBatch(prepared)
                } finally {
                    runCatching { mmapFile?.delete() }
                }
            } else {
                true
            }
        }.getOrElse { throwable ->
            Log.e(LoggerX.TAG, "async image batch insert failed: ${throwable.message}")
            prepared.forEach { preparedItem ->
                val originalTask = preparedItem.sourceTask
                if (originalTask != null && originalTask.retry + 1 < IMAGE_MAX_RETRY) {
                    asyncRetryCount.incrementAndGet()
                    retryTasks += originalTask.copy(retry = originalTask.retry + 1)
                } else {
                    asyncFailedCount.incrementAndGet()
                }
            }
            false
        }
        val costMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs)
        asyncMaxCostMs.updateAndGet { old -> maxOf(old, costMs) }
        if (costMs > 50L) {
            asyncOver50MsCount.incrementAndGet()
        }
        if (costMs > 100L) {
            asyncOver100MsCount.incrementAndGet()
        }
        if (success) {
            asyncSuccessCount.addAndGet(prepared.size.toLong())
        }
        retryTasks.forEach { retryTask ->
            if (!imageWriteQueue.offer(retryTask)) {
                asyncFailedCount.incrementAndGet()
            }
        }
    }

    fun loadImageBase64(scopeTag: String, logId: Int): String? {
        val tableName = getTableName(scopeTag)
        if (!tableExists(tableName)) return null
        return queryImageBlob(tableName, logId)?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
    }

    fun loadImagePreviewData(scopeTag: String, logId: Int): ImagePreviewData? {
        val tableName = getTableName(scopeTag)
        if (!tableExists(tableName)) return null
        val sql = """
            SELECT s.${LoggerX.COLUMN_THUMBNAIL}, i.${LoggerX.COLUMN_MEDIA_TYPE}, i.${LoggerX.COLUMN_COMPRESSED_IMAGE}
            FROM $tableName s
            LEFT JOIN $IMAGE_TABLE i ON s.${LoggerX.COLUMN_IMAGE_ID} = i.id
            WHERE s.${LoggerX.COLUMN_ID}=?
            LIMIT 1
        """.trimIndent()
        return runCatching {
            getReadOnlyDatabase().rawQuery(sql, arrayOf(logId.toString())).use { c ->
                if (!c.moveToFirst()) return null
                val thumb = c.getString(0)
                val mime = c.getString(1).orEmpty().ifBlank { "image/webp" }
                val blob = c.getBlob(2)
                ImagePreviewData(
                    mimeType = mime,
                    thumbnailBase64 = thumb,
                    compressedBase64 = blob?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
                )
            }
        }.getOrNull()
    }

    private fun queryImageBlobByImageId(imageId: Int): ByteArray? {
        if (imageId <= 0) return null
        val sql = """
            SELECT ${LoggerX.COLUMN_COMPRESSED_IMAGE}
            FROM $IMAGE_TABLE
            WHERE id=?
            LIMIT 1
        """.trimIndent()
        return runCatching {
            getReadOnlyDatabase().rawQuery(sql, arrayOf(imageId.toString())).use { c ->
                if (c.moveToFirst()) c.getBlob(0) else null
            }
        }.getOrNull()
    }

    fun loadImageMimeType(scopeTag: String, logId: Int): String? {
        val tableName = getTableName(scopeTag)
        if (!tableExists(tableName)) return null
        val sql = """
            SELECT i.${LoggerX.COLUMN_MEDIA_TYPE}
            FROM $tableName s
            LEFT JOIN $IMAGE_TABLE i ON s.${LoggerX.COLUMN_IMAGE_ID} = i.id
            WHERE s.${LoggerX.COLUMN_ID}=?
            LIMIT 1
        """.trimIndent()
        return runCatching {
            getReadOnlyDatabase().rawQuery(sql, arrayOf(logId.toString())).use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
    }

    fun queryLogsAdvanced(
        scopeTag: String,
        time: String? = null,
        tag: String? = null,
        level: String? = null,
        method: String? = null,
        isImage: Boolean? = null,
        keyword: String? = null,
        isAsc: Boolean = false,
        page: Int = 1,
        limit: Int? = 100,
        includeImagePayload: Boolean = false
    ): List<Map<String, Any>> {
        if (limit == null) {
            return queryLogsAllAdvanced(
                scopeTag = scopeTag,
                time = time,
                tag = tag,
                level = level,
                method = method,
                isImage = isImage,
                keyword = keyword,
                isAsc = isAsc,
                includeImagePayload = includeImagePayload
            )
        }
        return queryLogsPageAdvanced(
            scopeTag = scopeTag,
            time = time,
            tag = tag,
            level = level,
            method = method,
            isImage = isImage,
            keyword = keyword,
            isAsc = isAsc,
            page = page,
            limit = limit ?: 100,
            includeImagePayload = includeImagePayload
        ).rows
    }

    fun queryLogsPageAdvanced(
        scopeTag: String,
        time: String? = null,
        tag: String? = null,
        level: String? = null,
        method: String? = null,
        isImage: Boolean? = null,
        keyword: String? = null,
        isAsc: Boolean = false,
        page: Int = 1,
        limit: Int = 100,
        includeImagePayload: Boolean = false,
        maxPageBytes: Int = 1024 * 1024
    ): QueryPageResult {
        val tableName = getTableName(scopeTag)
        if (!tableExists(tableName)) {
            return QueryPageResult(emptyList(), 0, page, limit, null, 0, false, emptyList())
        }
        val whereParts = mutableListOf<String>()
        val args = mutableListOf<String>()
        time?.let {
            whereParts += "s.${LoggerX.COLUMN_TIME} LIKE ?"
            args += "$it%"
        }
        tag?.let {
            whereParts += "s.${LoggerX.COLUMN_TAG} = ?"
            args += it
        }
        level?.let {
            whereParts += "s.${LoggerX.COLUMN_LEVEL} = ?"
            args += it
        }
        method?.let {
            whereParts += "s.${LoggerX.COLUMN_METHOD} LIKE ?"
            args += "%$it%"
        }
        keyword?.let {
            whereParts += "s.${LoggerX.COLUMN_MESSAGE} LIKE ?"
            args += "%$it%"
        }
        isImage?.let {
            whereParts += if (it) "s.${LoggerX.COLUMN_IMAGE_ID} > 0" else "s.${LoggerX.COLUMN_IMAGE_ID} <= 0"
        }
        val where = if (whereParts.isEmpty()) "" else "WHERE ${whereParts.joinToString(" AND ")}"
        val order = if (isAsc) "ASC" else "DESC"
        val offset = maxOf(0, (page - 1) * limit)
        val limitSql = "LIMIT $limit OFFSET $offset"
        val sql = """
            SELECT
                s.${LoggerX.COLUMN_ID},
                s.${LoggerX.COLUMN_TIME},
                s.${LoggerX.COLUMN_LEVEL},
                s.${LoggerX.COLUMN_TAG},
                s.${LoggerX.COLUMN_METHOD},
                s.${LoggerX.COLUMN_MESSAGE},
                s.${LoggerX.COLUMN_THUMBNAIL},
                s.${LoggerX.COLUMN_IMAGE_ID},
                i.${LoggerX.COLUMN_MEDIA_TYPE},
                i.${LoggerX.COLUMN_ORIGINAL_SIZE},
                i.${LoggerX.COLUMN_COMPRESSED_SIZE},
                i.${LoggerX.COLUMN_COMPRESSION_RATIO},
                i.${LoggerX.COLUMN_CHECKSUM_SHA256}
            FROM $tableName s
            LEFT JOIN $IMAGE_TABLE i ON s.${LoggerX.COLUMN_IMAGE_ID} = i.id
            $where
            ORDER BY s.${LoggerX.COLUMN_TIME} $order
            $limitSql
        """.trimIndent()
        val countSql = """
            SELECT COUNT(*)
            FROM $tableName s
            $where
        """.trimIndent()
        val explainSql = "EXPLAIN QUERY PLAN $sql"
        return runCatching {
            val result = mutableListOf<Map<String, Any>>()
            var bytes = 0
            val db = getReadOnlyDatabase()
            val totalCount = db.rawQuery(countSql, args.toTypedArray()).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }
            val queryPlan = db.rawQuery(explainSql, args.toTypedArray()).use { cursor ->
                val plan = mutableListOf<String>()
                while (cursor.moveToNext()) {
                    plan += cursor.getString(3).orEmpty()
                }
                plan
            }
            db.rawQuery(sql, args.toTypedArray()).use { cursor ->
                while (cursor.moveToNext()) {
                    val row = cursorToMap(cursor, includeImagePayload)
                    val rowBytes = estimateRowBytes(row)
                    if (result.isNotEmpty() && bytes + rowBytes > maxPageBytes) {
                        break
                    }
                    result += row
                    bytes += rowBytes
                }
            }
            val loadedCount = offset + result.size
            QueryPageResult(
                rows = result,
                totalCount = totalCount,
                page = page,
                limit = limit,
                nextPage = if (loadedCount < totalCount && result.isNotEmpty()) page + 1 else null,
                approxBytes = bytes,
                hasMore = loadedCount < totalCount,
                queryPlan = queryPlan
            )
        }.getOrElse {
            Log.e(LoggerX.TAG, "queryLogsAdvanced failed: ${it.message}")
            QueryPageResult(emptyList(), 0, page, limit, null, 0, false, emptyList())
        }
    }

    private fun queryLogsAllAdvanced(
        scopeTag: String,
        time: String? = null,
        tag: String? = null,
        level: String? = null,
        method: String? = null,
        isImage: Boolean? = null,
        keyword: String? = null,
        isAsc: Boolean = false,
        includeImagePayload: Boolean = false
    ): List<Map<String, Any>> {
        val tableName = getTableName(scopeTag)
        if (!tableExists(tableName)) return emptyList()
        val whereParts = mutableListOf<String>()
        val args = mutableListOf<String>()
        time?.let {
            whereParts += "s.${LoggerX.COLUMN_TIME} LIKE ?"
            args += "$it%"
        }
        tag?.let {
            whereParts += "s.${LoggerX.COLUMN_TAG} = ?"
            args += it
        }
        level?.let {
            whereParts += "s.${LoggerX.COLUMN_LEVEL} = ?"
            args += it
        }
        method?.let {
            whereParts += "s.${LoggerX.COLUMN_METHOD} LIKE ?"
            args += "%$it%"
        }
        keyword?.let {
            whereParts += "s.${LoggerX.COLUMN_MESSAGE} LIKE ?"
            args += "%$it%"
        }
        isImage?.let {
            whereParts += if (it) "s.${LoggerX.COLUMN_IMAGE_ID} > 0" else "s.${LoggerX.COLUMN_IMAGE_ID} <= 0"
        }
        val where = if (whereParts.isEmpty()) "" else "WHERE ${whereParts.joinToString(" AND ")}"
        val order = if (isAsc) "ASC" else "DESC"
        val sql = """
            SELECT
                s.${LoggerX.COLUMN_ID},
                s.${LoggerX.COLUMN_TIME},
                s.${LoggerX.COLUMN_LEVEL},
                s.${LoggerX.COLUMN_TAG},
                s.${LoggerX.COLUMN_METHOD},
                s.${LoggerX.COLUMN_MESSAGE},
                s.${LoggerX.COLUMN_THUMBNAIL},
                s.${LoggerX.COLUMN_IMAGE_ID},
                i.${LoggerX.COLUMN_MEDIA_TYPE},
                i.${LoggerX.COLUMN_ORIGINAL_SIZE},
                i.${LoggerX.COLUMN_COMPRESSED_SIZE},
                i.${LoggerX.COLUMN_COMPRESSION_RATIO},
                i.${LoggerX.COLUMN_CHECKSUM_SHA256}
            FROM $tableName s
            LEFT JOIN $IMAGE_TABLE i ON s.${LoggerX.COLUMN_IMAGE_ID} = i.id
            $where
            ORDER BY s.${LoggerX.COLUMN_TIME} $order
        """.trimIndent()
        return runCatching {
            val result = mutableListOf<Map<String, Any>>()
            getReadOnlyDatabase().rawQuery(sql, args.toTypedArray()).use { cursor ->
                while (cursor.moveToNext()) {
                    result += cursorToMap(cursor, includeImagePayload)
                }
            }
            result
        }.getOrElse {
            Log.e(LoggerX.TAG, "queryLogsAllAdvanced failed: ${it.message}")
            emptyList()
        }
    }

    fun getDistinctValues(scopeTag: String, columnName: String): List<String> {
        val tableName = getTableName(scopeTag)
        if (!tableExists(tableName)) return emptyList()
        val sql = when (columnName) {
            LoggerX.COLUMN_TIME -> "SELECT DISTINCT substr(${LoggerX.COLUMN_TIME},1,10) FROM $tableName ORDER BY ${LoggerX.COLUMN_TIME} DESC"
            LoggerX.COLUMN_METHOD -> """
                SELECT DISTINCT CASE
                    WHEN instr(${LoggerX.COLUMN_METHOD}, '$') > 0 THEN substr(${LoggerX.COLUMN_METHOD},1,instr(${LoggerX.COLUMN_METHOD}, '$') - 1)
                    WHEN instr(${LoggerX.COLUMN_METHOD}, '(') > 0 THEN substr(${LoggerX.COLUMN_METHOD},1,instr(${LoggerX.COLUMN_METHOD}, '(') - 1)
                    ELSE ${LoggerX.COLUMN_METHOD}
                END FROM $tableName
            """.trimIndent()
            LoggerX.COLUMN_IS_IMAGE -> """
                SELECT DISTINCT CASE WHEN ${LoggerX.COLUMN_IMAGE_ID} > 0 THEN '1' ELSE '0' END FROM $tableName
            """.trimIndent()
            else -> "SELECT DISTINCT $columnName FROM $tableName"
        }
        return runCatching {
            val values = mutableListOf<String>()
            getReadOnlyDatabase().rawQuery(sql, null).use { c ->
                while (c.moveToNext()) {
                    c.getString(0)?.takeIf { it.isNotBlank() }?.let(values::add)
                }
            }
            values
        }.getOrElse {
            Log.e(LoggerX.TAG, "getDistinctValues failed: ${it.message}")
            emptyList()
        }
    }

    fun deleteLogs(scopeTag: String, timeFormat: String?): Int {
        val tableName = getTableName(scopeTag)
        if (!tableExists(tableName)) return 0
        val ids = mutableListOf<Int>()
        val where = if (timeFormat.isNullOrBlank()) null else "${LoggerX.COLUMN_TIME} < ?"
        val whereArgs = if (timeFormat.isNullOrBlank()) null else arrayOf(timeFormat)
        database.query(tableName, arrayOf(LoggerX.COLUMN_ID), where, whereArgs, null, null, null).use { c ->
            while (c.moveToNext()) {
                ids += c.getInt(0)
            }
        }
        if (ids.isEmpty()) return 0
        database.beginTransaction()
        return try {
            ids.chunked(200).forEach { group ->
                val placeholders = group.joinToString(",") { "?" }
                database.delete(IMAGE_TABLE, "scope_id IN ($placeholders)", group.map { it.toString() }.toTypedArray())
            }
            val rows = database.delete(tableName, where, whereArgs)
            database.setTransactionSuccessful()
            rows
        } finally {
            runCatching { database.endTransaction() }
        }
    }

    fun clearAllLogs(): Boolean {
        return runCatching {
            getAllLogTables().forEach { table ->
                val ids = mutableListOf<Int>()
                database.query(table, arrayOf(LoggerX.COLUMN_ID), null, null, null, null, null).use { c ->
                    while (c.moveToNext()) {
                        ids += c.getInt(0)
                    }
                }
                ids.chunked(200).forEach { group ->
                    val placeholders = group.joinToString(",") { "?" }
                    database.delete(IMAGE_TABLE, "scope_id IN ($placeholders)", group.map { it.toString() }.toTypedArray())
                }
                database.delete(table, null, null)
            }
            true
        }.getOrElse {
            Log.e(LoggerX.TAG, "clearAllLogs failed: ${it.message}")
            false
        }
    }

    private var autoCleanFuture: java.util.concurrent.ScheduledFuture<*>? = null
    private var autoCleanBySizeFuture: java.util.concurrent.ScheduledFuture<*>? = null

    fun startAutoCleanByDate(retentionDays: Int) {
        if (retentionDays <= 0) return
        autoCleanFuture?.cancel(false)
        autoCleanFuture = scheduledExecutor.scheduleWithFixedDelay({
            runCatching { performCleanupByDateRange(retentionDays) }
                .onFailure { Log.e(LoggerX.TAG, "Auto clean by date failed: ${it.message}") }
        }, 0, 24, TimeUnit.HOURS)
    }

    fun startAutoCleanBySize(maxSizeMb: Double, cleanSizeMb: Double) {
        if (maxSizeMb <= 0 || cleanSizeMb <= 0) return
        autoCleanBySizeFuture?.cancel(false)
        autoCleanBySizeFuture = scheduledExecutor.scheduleWithFixedDelay({
            runCatching { performCleanupBySize(maxSizeMb, cleanSizeMb) }
                .onFailure { Log.e(LoggerX.TAG, "Auto clean by size failed: ${it.message}") }
        }, 0, 24, TimeUnit.HOURS)
    }

    private fun performCleanupByDateRange(days: Int) {
        getAllLogTables().forEach { tableName ->
            val distinctDates = mutableListOf<String>()
            val sql = "SELECT DISTINCT substr(${LoggerX.COLUMN_TIME}, 1, 10) as log_date FROM $tableName ORDER BY log_date ASC"
            database.rawQuery(sql, null).use { cursor ->
                while (cursor.moveToNext()) {
                    cursor.getString(0)?.let(distinctDates::add)
                }
            }
            if (distinctDates.size > days) {
                val cutoffDate = distinctDates[distinctDates.size - days - 1]
                deleteLogsByDate(tableName, cutoffDate)
            }
        }
    }

    private fun deleteLogsByDate(tableName: String, cutoffDate: String) {
        val ids = mutableListOf<Int>()
        database.query(
            tableName,
            arrayOf(LoggerX.COLUMN_ID),
            "substr(${LoggerX.COLUMN_TIME},1,10) <= ?",
            arrayOf(cutoffDate),
            null,
            null,
            null
        ).use { c ->
            while (c.moveToNext()) ids += c.getInt(0)
        }
        ids.chunked(200).forEach { group ->
            val placeholders = group.joinToString(",") { "?" }
            database.delete(IMAGE_TABLE, "scope_id IN ($placeholders)", group.map { it.toString() }.toTypedArray())
        }
        database.delete(tableName, "substr(${LoggerX.COLUMN_TIME},1,10) <= ?", arrayOf(cutoffDate))
    }

    private fun performCleanupBySize(maxSizeMb: Double, cleanSizeMb: Double) {
        val dbFile = ContextHolder.getAppContext().getDatabasePath(LogDbHelper.DB_NAME)
        if (!dbFile.exists()) return
        var currentSizeMb = dbFile.length().toDouble() / (1024.0 * 1024.0)
        if (currentSizeMb <= maxSizeMb) return
        val target = maxSizeMb - cleanSizeMb
        var loop = 0
        while (currentSizeMb > target && loop < 10) {
            loop++
            getAllLogTables().forEach { table ->
                val sql = "DELETE FROM $table WHERE ${LoggerX.COLUMN_ID} IN (SELECT ${LoggerX.COLUMN_ID} FROM $table ORDER BY ${LoggerX.COLUMN_TIME} ASC LIMIT 500)"
                database.execSQL(sql)
            }
            currentSizeMb = dbFile.length().toDouble() / (1024.0 * 1024.0)
        }
    }

    private fun prepareImageWrite(
        scopeTag: String,
        level: String,
        classTag: String,
        method: String,
        message: String,
        imageBytes: ByteArray,
        compressor: IImageCompressor,
        options: ImageCompressionOptions,
        sourceTask: ImageWriteTask? = null
    ): PreparedImageWrite {
        val tableName = getTableName(scopeTag)
        ensureTable(tableName)
        var compressionLog = "origin=${imageBytes.size}B"
        return try {
            val encoded = ImageLogCodec.encode(
                source = imageBytes,
                compressor = compressor,
                options = options,
                maxFieldBytes = ImageLogCodec.MAX_INPUT_BYTES
            ) ?: throw ImageLogWriteException(
                message = "Image encode failed or exceeds max payload",
                compressionLog = "$compressionLog|encode_failed"
            )
            compressionLog = encoded.compressionLog
            PreparedImageWrite(
                tableName = tableName,
                level = level,
                classTag = classTag,
                method = method,
                message = message,
                encoded = encoded,
                sourceTask = sourceTask
            )
        } catch (e: Exception) {
            Log.e(LoggerX.TAG, "prepareImageWrite failed: ${e.message}")
            throw if (e is ImageLogWriteException) e else ImageLogWriteException(
                "Image encode transaction failed",
                compressionLog,
                e
            )
        }
    }

    @Synchronized
    private fun insertPreparedImageBatch(preparedBatch: List<PreparedImageWrite>): Boolean {
        if (preparedBatch.isEmpty()) return true
        database.beginTransaction()
        return try {
            preparedBatch.forEach { prepared ->
                ensureTable(prepared.tableName)
                val scopeRowId = database.insertOrThrow(prepared.tableName, null, ContentValues().apply {
                    put(LoggerX.COLUMN_LEVEL, prepared.level)
                    put(LoggerX.COLUMN_TAG, prepared.classTag)
                    put(LoggerX.COLUMN_METHOD, prepared.method)
                    put(LoggerX.COLUMN_MESSAGE, prepared.message)
                    put(LoggerX.COLUMN_THUMBNAIL, prepared.encoded.thumbnailBase64)
                    put(LoggerX.COLUMN_IMAGE_ID, -1)
                })
                val imageId = database.insertOrThrow(IMAGE_TABLE, null, ContentValues().apply {
                    put("scope_id", scopeRowId.toInt())
                    put(LoggerX.COLUMN_MEDIA_TYPE, prepared.encoded.mediaType)
                    put(LoggerX.COLUMN_COMPRESSED_IMAGE, prepared.encoded.compressedImage)
                    put("width", prepared.encoded.width)
                    put("height", prepared.encoded.height)
                    put(LoggerX.COLUMN_ORIGINAL_SIZE, prepared.encoded.originalBytes)
                    put(LoggerX.COLUMN_COMPRESSED_SIZE, prepared.encoded.compressedBytes)
                    put(LoggerX.COLUMN_COMPRESSION_RATIO, prepared.encoded.compressionRatio)
                    put(LoggerX.COLUMN_CHECKSUM_SHA256, sha256(prepared.encoded.compressedImage))
                })
                database.update(
                    prepared.tableName,
                    ContentValues().apply { put(LoggerX.COLUMN_IMAGE_ID, imageId.toInt()) },
                    "${LoggerX.COLUMN_ID}=?",
                    arrayOf(scopeRowId.toString())
                )
            }
            database.setTransactionSuccessful()
            true
        } finally {
            runCatching { database.endTransaction() }
        }
    }

    private fun stagePreparedBatchWithMmap(preparedBatch: List<PreparedImageWrite>): File? {
        if (preparedBatch.isEmpty()) return null
        val payload = ByteArrayOutputStream()
        DataOutputStream(payload).use { out ->
            out.writeInt(preparedBatch.size)
            preparedBatch.forEach { item ->
                out.writeUTF(item.tableName)
                out.writeUTF(item.level)
                out.writeUTF(item.classTag)
                out.writeUTF(item.method)
                out.writeUTF(item.message)
                out.writeUTF(item.encoded.mediaType)
                out.writeInt(item.encoded.width)
                out.writeInt(item.encoded.height)
                out.writeInt(item.encoded.originalBytes)
                out.writeInt(item.encoded.compressedBytes)
                out.writeDouble(item.encoded.compressionRatio)
                out.writeUTF(item.encoded.thumbnailBase64)
                out.writeInt(item.encoded.compressedImage.size)
                out.write(item.encoded.compressedImage)
            }
        }
        val bytes = payload.toByteArray()
        val bufferDir = File(ContextHolder.getAppContext().cacheDir, "loggerx-mmap").apply { mkdirs() }
        val bufferFile = File(bufferDir, "image-batch-${System.nanoTime()}.bin")
        RandomAccessFile(bufferFile, "rw").use { file ->
            file.setLength(bytes.size.toLong())
            file.channel.use { channel ->
                val mapped = channel.map(FileChannel.MapMode.READ_WRITE, 0, bytes.size.toLong())
                mapped.put(bytes)
                mapped.force()
            }
        }
        return bufferFile
    }

    private fun queryImageBlob(tableName: String, logId: Int): ByteArray? {
        val sql = """
            SELECT i.${LoggerX.COLUMN_COMPRESSED_IMAGE}
            FROM $tableName s
            LEFT JOIN $IMAGE_TABLE i ON s.${LoggerX.COLUMN_IMAGE_ID} = i.id
            WHERE s.${LoggerX.COLUMN_ID}=?
            LIMIT 1
        """.trimIndent()
        return runCatching {
            getReadOnlyDatabase().rawQuery(sql, arrayOf(logId.toString())).use { c ->
                if (c.moveToFirst()) c.getBlob(0) else null
            }
        }.getOrNull()
    }

    private fun cursorToMap(c: Cursor, includeImagePayload: Boolean): Map<String, Any> {
        val imageId = c.getInt(c.getColumnIndexOrThrow(LoggerX.COLUMN_IMAGE_ID))
        val isImage = imageId > 0
        val map = mutableMapOf<String, Any>(
            LoggerX.COLUMN_ID to c.getInt(c.getColumnIndexOrThrow(LoggerX.COLUMN_ID)),
            LoggerX.COLUMN_TIME to c.getString(c.getColumnIndexOrThrow(LoggerX.COLUMN_TIME)),
            LoggerX.COLUMN_LEVEL to c.getString(c.getColumnIndexOrThrow(LoggerX.COLUMN_LEVEL)).orEmpty(),
            LoggerX.COLUMN_TAG to c.getString(c.getColumnIndexOrThrow(LoggerX.COLUMN_TAG)).orEmpty(),
            LoggerX.COLUMN_METHOD to c.getString(c.getColumnIndexOrThrow(LoggerX.COLUMN_METHOD)).orEmpty(),
            LoggerX.COLUMN_MESSAGE to c.getString(c.getColumnIndexOrThrow(LoggerX.COLUMN_MESSAGE)).orEmpty(),
            LoggerX.COLUMN_THUMBNAIL to c.getString(c.getColumnIndexOrThrow(LoggerX.COLUMN_THUMBNAIL)).orEmpty(),
            LoggerX.COLUMN_IMAGE_ID to imageId,
            LoggerX.COLUMN_IS_IMAGE to if (isImage) 1 else 0
        )
        if (isImage) {
            map[LoggerX.COLUMN_MEDIA_TYPE] = c.getString(c.getColumnIndexOrThrow(LoggerX.COLUMN_MEDIA_TYPE)).orEmpty()
            var originalSize = c.getInt(c.getColumnIndexOrThrow(LoggerX.COLUMN_ORIGINAL_SIZE))
            var compressedSize = c.getInt(c.getColumnIndexOrThrow(LoggerX.COLUMN_COMPRESSED_SIZE))
            var compressionRatio = c.getDouble(c.getColumnIndexOrThrow(LoggerX.COLUMN_COMPRESSION_RATIO))
            map[LoggerX.COLUMN_CHECKSUM_SHA256] = c.getString(c.getColumnIndexOrThrow(LoggerX.COLUMN_CHECKSUM_SHA256)).orEmpty()
            if (includeImagePayload) {
                val blob = queryImageBlobByImageId(imageId)
                map[LoggerX.COLUMN_COMPRESSED_IMAGE] = blob?.let { Base64.encodeToString(it, Base64.NO_WRAP) }.orEmpty()
                if (blob != null && compressedSize <= 0) {
                    compressedSize = blob.size
                }
                if (originalSize > 0 && compressedSize > 0 && compressionRatio <= 0.0) {
                    compressionRatio = compressedSize.toDouble() / originalSize.toDouble()
                }
            }
            map[LoggerX.COLUMN_ORIGINAL_SIZE] = originalSize
            map[LoggerX.COLUMN_COMPRESSED_SIZE] = compressedSize
            map[LoggerX.COLUMN_COMPRESSION_RATIO] = compressionRatio
        }
        return map
    }

    private fun estimateRowBytes(row: Map<String, Any>): Int {
        return row.entries.sumOf { (key, value) ->
            key.length * 2 + value.toString().length * 2
        }.coerceAtLeast(64)
    }

    private fun tableExists(tableName: String): Boolean {
        if (existedTables.contains(tableName)) return true
        val exists = runCatching {
            database.rawQuery(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?",
                arrayOf(tableName)
            ).use { c -> c.moveToFirst() && c.getInt(0) > 0 }
        }.getOrDefault(false)
        if (exists) {
            ensureTable(tableName)
        }
        return exists
    }

    private fun getReadOnlyDatabase(): SQLiteDatabase {
        readOnlyDatabase?.let { db ->
            if (db.isOpen) return db
        }
        synchronized(readDbLock) {
            readOnlyDatabase?.let { db ->
                if (db.isOpen) return db
            }
            val dbPath = ContextHolder.getAppContext().getDatabasePath(LogDbHelper.DB_NAME).absolutePath
            val db = SQLiteDatabase.openDatabase(
                dbPath,
                null,
                SQLiteDatabase.OPEN_READONLY
            )
            applyPragma(db, "PRAGMA query_only=ON")
            applyPragma(db, "PRAGMA cache_size=-64000")
            readOnlyDatabase = db
            return db
        }
    }

    private fun applyPragma(db: SQLiteDatabase, sql: String) {
        runCatching {
            db.rawQuery(sql, null).use { }
        }.recoverCatching {
            db.execSQL(sql)
        }
    }

    private fun getAllLogTables(): List<String> {
        val tables = mutableListOf<String>()
        database.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name LIKE '%_log'", null).use { c ->
            while (c.moveToNext()) tables += c.getString(0)
        }
        return tables
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}

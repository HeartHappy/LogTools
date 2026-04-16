package com.hearthappy.log.db

import android.content.ContentValues
import android.database.sqlite.SQLiteStatement
import android.util.Log
import com.hearthappy.log.LoggerX
import com.hearthappy.log.LoggerX.Companion.COLUMN_IMAGE_CHUNKED
import com.hearthappy.log.LoggerX.Companion.COLUMN_IMAGE_PAYLOAD
import com.hearthappy.log.LoggerX.Companion.COLUMN_IS_IMAGE
import com.hearthappy.log.LoggerX.Companion.COLUMN_ID
import com.hearthappy.log.LoggerX.Companion.COLUMN_LEVEL
import com.hearthappy.log.LoggerX.Companion.COLUMN_MESSAGE
import com.hearthappy.log.LoggerX.Companion.COLUMN_METHOD
import com.hearthappy.log.LoggerX.Companion.COLUMN_MIME_TYPE
import com.hearthappy.log.LoggerX.Companion.COLUMN_TAG
import com.hearthappy.log.LoggerX.Companion.COLUMN_THUMBNAIL
import com.hearthappy.log.LoggerX.Companion.COLUMN_TIME
import com.hearthappy.log.core.ImageLogCodec
import com.hearthappy.log.core.ContextHolder
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object LogDbManager {
    data class ImagePreviewData(
        val mimeType: String,
        val thumbnailBase64: String?,
        val compressedBase64: String?
    )

    private val dbHelper = LogDbHelper(ContextHolder.getAppContext())
    private val database = dbHelper.writableDatabase

    // 缓存不同表的 SQLiteStatement，Key 为表名
    private val statementCache = HashMap<String, SQLiteStatement>()

    // 记录已存在的表，减少查询次数
    private val existedTables = HashSet<String>()
    private val tableColumnsCache = HashMap<String, Set<String>>()
    private val scheduledExecutor = Executors.newSingleThreadScheduledExecutor()

    private fun getTableName(scopeTag: String): String { // 过滤非法字符，确保表名合法
        val cleanTag = scopeTag.replace(Regex("[^a-zA-Z0-9_]"), "_")
        return "${cleanTag}_log"
    }

    @Synchronized private fun ensureTable(tableName: String): SQLiteStatement {
        if (!existedTables.contains(tableName)) {
            dbHelper.createLogTable(database, tableName)
            existedTables.add(tableName)
        }
        ensureTableSchema(tableName)

        return statementCache.getOrPut(tableName) {
            database.compileStatement("INSERT INTO $tableName (level, tag, method, message) VALUES (?, ?, ?, ?)")
        }
    }

    fun getDbFileSize(): Double {
        return dbHelper.getDbFileSize()
    }

    /**
     * 写入日志：根据 scope 自动分表
     */
    @Synchronized
    fun insertLog(scopeTag: String, level: String, classTag: String, method: String, message: String) {
        val tableName = getTableName(scopeTag)
        repeat(2) { attempt ->
            try {
                val stmt = ensureTable(tableName)
                stmt.clearBindings()
                stmt.bindString(1, level)
                stmt.bindString(2, classTag)
                stmt.bindString(3, method)
                stmt.bindString(4, message)
                stmt.executeInsert()
                return
            } catch (e: Exception) {
                statementCache.remove(tableName)?.close()
                existedTables.remove(tableName)
                try {
                    dbHelper.createLogTable(database, tableName)
                } catch (_: Exception) {
                }
                if (attempt == 1) {
                    Log.e(LoggerX.TAG, "Insert failed after retry: ${e.message}")
                }
            }
        }
    }

    @Synchronized
    fun insertImageLog(
        scopeTag: String,
        level: String,
        classTag: String,
        method: String,
        message: String,
        mimeType: String,
        thumbnailBase64: String,
        payloadBase64: String?,
        chunked: Boolean,
        chunks: List<String>
    ): Boolean {
        val tableName = getTableName(scopeTag)
        if (thumbnailBase64.toByteArray(Charsets.UTF_8).size > ImageLogCodec.MAX_TEXT_COLUMN_BYTES) {
            Log.w(LoggerX.TAG, "Thumbnail exceeds TEXT limit, reject image log")
            return false
        }
        if (!chunked && !payloadBase64.isNullOrEmpty() &&
            payloadBase64.toByteArray(Charsets.UTF_8).size > ImageLogCodec.MAX_TEXT_COLUMN_BYTES
        ) {
            Log.w(LoggerX.TAG, "Image payload exceeds TEXT limit, reject image log")
            return false
        }
        if (chunked && chunks.any { it.toByteArray(Charsets.UTF_8).size > ImageLogCodec.MAX_TEXT_COLUMN_BYTES }) {
            Log.w(LoggerX.TAG, "Image chunk exceeds TEXT limit, reject image log")
            return false
        }
        try {
            ensureTable(tableName)
            database.beginTransaction()
            val rowId = database.insertOrThrow(tableName, null, ContentValues().apply {
                put(COLUMN_LEVEL, level)
                put(COLUMN_TAG, classTag)
                put(COLUMN_METHOD, method)
                put(COLUMN_MESSAGE, message)
                put(COLUMN_IS_IMAGE, 1)
                put(COLUMN_MIME_TYPE, mimeType)
                put(COLUMN_THUMBNAIL, thumbnailBase64)
                put(COLUMN_IMAGE_PAYLOAD, payloadBase64)
                put(COLUMN_IMAGE_CHUNKED, if (chunked) 1 else 0)
            })
            if (chunked && rowId > 0) {
                val chunkTable = "${tableName}_img_chunk"
                chunks.forEachIndexed { index, chunk ->
                    database.insertOrThrow(chunkTable, null, ContentValues().apply {
                        put("log_id", rowId.toInt())
                        put("chunk_index", index)
                        put("chunk_data", chunk)
                    })
                }
            }
            database.setTransactionSuccessful()
            return rowId > 0
        } catch (e: Exception) {
            Log.e(LoggerX.TAG, "insertImageLog failed: ${e.message}")
            return false
        } finally {
            runCatching { database.endTransaction() }
        }
    }

    fun loadImageBase64(scopeTag: String, logId: Int): String? {
        val tableName = getTableName(scopeTag)
        if (!tableExists(tableName)) return null
        ensureTableSchema(tableName)
        return try {
            val cursor = database.query(
                tableName,
                arrayOf(COLUMN_IMAGE_CHUNKED, COLUMN_IMAGE_PAYLOAD, COLUMN_IS_IMAGE),
                "$COLUMN_ID = ?",
                arrayOf(logId.toString()),
                null,
                null,
                null,
                "1"
            )
            cursor.use {
                if (!it.moveToFirst()) return null
                if (it.getInt(it.getColumnIndexOrThrow(COLUMN_IS_IMAGE)) != 1) return null
                val isChunked = it.getInt(it.getColumnIndexOrThrow(COLUMN_IMAGE_CHUNKED)) == 1
                if (!isChunked) {
                    return it.getString(it.getColumnIndexOrThrow(COLUMN_IMAGE_PAYLOAD))
                }
            }
            val chunkTable = "${tableName}_img_chunk"
            val chunkCursor = database.query(
                chunkTable,
                arrayOf("chunk_data"),
                "log_id = ?",
                arrayOf(logId.toString()),
                null,
                null,
                "chunk_index ASC"
            )
            val builder = StringBuilder()
            chunkCursor.use { c ->
                while (c.moveToNext()) {
                    builder.append(c.getString(0).orEmpty())
                }
            }
            builder.toString().ifEmpty { null }
        } catch (e: Exception) {
            Log.e(LoggerX.TAG, "loadImageBase64 failed: ${e.message}")
            null
        }
    }

    fun loadImagePreviewData(scopeTag: String, logId: Int): ImagePreviewData? {
        val tableName = getTableName(scopeTag)
        if (!tableExists(tableName)) return null
        ensureTableSchema(tableName)
        val mimeType: String
        val thumbnail: String?
        val isChunked: Boolean
        val inlinePayload: String?
        try {
            database.query(
                tableName,
                arrayOf(COLUMN_IS_IMAGE, COLUMN_MIME_TYPE, COLUMN_THUMBNAIL, COLUMN_IMAGE_CHUNKED, COLUMN_IMAGE_PAYLOAD),
                "$COLUMN_ID = ?",
                arrayOf(logId.toString()),
                null,
                null,
                null,
                "1"
            ).use { c ->
                if (!c.moveToFirst()) return null
                if (c.getInt(c.getColumnIndexOrThrow(COLUMN_IS_IMAGE)) != 1) return null
                mimeType = c.getString(c.getColumnIndexOrThrow(COLUMN_MIME_TYPE)).orEmpty().ifBlank { "image/webp" }
                thumbnail = c.getString(c.getColumnIndexOrThrow(COLUMN_THUMBNAIL))
                isChunked = c.getInt(c.getColumnIndexOrThrow(COLUMN_IMAGE_CHUNKED)) == 1
                inlinePayload = c.getString(c.getColumnIndexOrThrow(COLUMN_IMAGE_PAYLOAD))
            }
        } catch (e: Exception) {
            Log.e(LoggerX.TAG, "loadImagePreviewData failed: ${e.message}")
            return null
        }
        if (!isChunked) {
            return ImagePreviewData(mimeType, thumbnail, inlinePayload)
        }
        val chunkTable = "${tableName}_img_chunk"
        val builder = StringBuilder()
        return try {
            database.query(
                chunkTable,
                arrayOf("chunk_data"),
                "log_id = ?",
                arrayOf(logId.toString()),
                null,
                null,
                "chunk_index ASC"
            ).use { c ->
                while (c.moveToNext()) {
                    builder.append(c.getString(0).orEmpty())
                }
            }
            ImagePreviewData(mimeType, thumbnail, builder.toString().ifEmpty { null })
        } catch (e: Exception) {
            Log.e(LoggerX.TAG, "loadImagePreviewData chunk load failed: ${e.message}")
            ImagePreviewData(mimeType, thumbnail, null)
        }
    }

    fun loadImageMimeType(scopeTag: String, logId: Int): String? {
        val tableName = getTableName(scopeTag)
        if (!tableExists(tableName)) return null
        ensureTableSchema(tableName)
        return try {
            database.query(
                tableName,
                arrayOf(COLUMN_MIME_TYPE),
                "$COLUMN_ID = ? AND $COLUMN_IS_IMAGE = 1",
                arrayOf(logId.toString()),
                null,
                null,
                null,
                "1"
            ).use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 1. 高级查询：支持 time, tag, level, method, message 的过滤与模糊查询
     * @param time 模糊匹配，可传 "2026-03-20" 或 "2026-03-20 15"
     * @param method 模糊匹配方法名
     */
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
        val tableName = getTableName(scopeTag)
        if (!tableExists(tableName)) return emptyList()
        ensureTableSchema(tableName)
        val list = mutableListOf<Map<String, Any>>()
        val selection = StringBuilder()
        val selectionArgs = mutableListOf<String>()

        // 时间模糊查询 (LIKE '2026-03-20%')
        time?.let {
            addFilter(selection, "$COLUMN_TIME LIKE ?", "$it%", selectionArgs)
        } // Tag 精确查询
        tag?.let {
            addFilter(selection, "$COLUMN_TAG = ?", it, selectionArgs)
        } // Level 精确查询
        level?.let {
            addFilter(selection, "$COLUMN_LEVEL = ?", it, selectionArgs)
        } // Method 模糊查询
        method?.let {
            addFilter(selection, "$COLUMN_METHOD LIKE ?", "%$it%", selectionArgs)
        } // Message 关键字查询
        isImage?.let {
            addFilter(selection, "$COLUMN_IS_IMAGE = ?", if (it) "1" else "0", selectionArgs)
        }
        keyword?.let {
            addFilter(selection, "$COLUMN_MESSAGE LIKE ?", "%$it%", selectionArgs)
        }

        val sortOrder = if (isAsc) "ASC" else "DESC"
        val offset = if (limit != null) maxOf(0, (page - 1) * limit) else 0
        val limitClause = when {
            limit == null -> null
            else -> "$offset, $limit"
        }
        try {
            val cursor = database.query(
                tableName,
                null,
                if (selection.isEmpty()) null else selection.toString(),
                if (selectionArgs.isEmpty()) null else selectionArgs.toTypedArray(),
                null,
                null,
                "$COLUMN_TIME $sortOrder",
                limitClause
            )

            cursor?.use {
                while (it.moveToNext()) {
                    list.add(cursorToMap(it, includeImagePayload))
                }
            }
        } catch (e: Exception) {
            Log.e(LoggerX.TAG, "queryLogsAdvanced failed: ${e.message}")
            return emptyList()
        }
        return list
    }

    /**
     * 2. 智能去重查询
     * - time: 截取天 (YYYY-MM-DD) 后去重
     * - method: 截取 $ 或 ( 之前的原始方法名后去重
     */
    fun getDistinctValues(scopeTag: String, columnName: String): List<String> {
        val tableName = getTableName(scopeTag)
        if (!tableExists(tableName)) return emptyList()
        val result = mutableListOf<String>()

        // 动态构建针对不同字段的清洗 SQL
        val sql = when (columnName) {
            COLUMN_TIME -> { // 截取前10位：2026-03-20 15:15:10 -> 2026-03-20
                "SELECT DISTINCT substr($COLUMN_TIME, 1, 10) FROM $tableName ORDER BY $COLUMN_TIME DESC"
            }
            COLUMN_METHOD -> {
                /**
                 * 逻辑：
                 * 1. 查找 $ 或 ( 的位置
                 * 2. 如果存在，截取其前面的部分
                 * 3. 如果不存在，返回原字符串
                 */
                """
                SELECT DISTINCT 
                    CASE 
                        WHEN instr($COLUMN_METHOD, '$') > 0 THEN substr($COLUMN_METHOD, 1, instr($COLUMN_METHOD, '$') - 1)
                        WHEN instr($COLUMN_METHOD, '(') > 0 THEN substr($COLUMN_METHOD, 1, instr($COLUMN_METHOD, '(') - 1)
                        ELSE $COLUMN_METHOD 
                    END as clean_method 
                FROM $tableName 
                ORDER BY clean_method ASC
                """.trimIndent()
            }
            else -> "SELECT DISTINCT $columnName FROM $tableName"
        }

        try {
            val cursor = database.rawQuery(sql, null)
            cursor?.use {
                while (it.moveToNext()) {
                    val value = it.getString(0)
                    if (!value.isNullOrEmpty()) result.add(value)
                }
            }
        } catch (e: Exception) {
            Log.e(LoggerX.TAG, "getDistinctValues failed: ${e.message}")
            return emptyList()
        }
        return result
    }

    /**
     * 1、删除指定 Scope 的所有数据
     * 2、删除旧数据：删除指定时间点之前的日志
     * @param timeFormat 格式为 "yyyy-MM-dd HH:mm:ss"
     */
    fun deleteLogs(scopeTag: String, timeFormat: String?): Int {
        val tableName = getTableName(scopeTag)
        val chunkTable = "${tableName}_img_chunk"
        return timeFormat?.run {
            val ids = mutableListOf<Int>()
            database.query(tableName, arrayOf(COLUMN_ID), "$COLUMN_TIME < ?", arrayOf(this), null, null, null)
                .use { c -> while (c.moveToNext()) ids.add(c.getInt(0)) }
            if (ids.isNotEmpty()) {
                ids.chunked(200).forEach { group ->
                    val placeholders = group.joinToString(",") { "?" }
                    database.delete(chunkTable, "log_id IN ($placeholders)", group.map { it.toString() }.toTypedArray())
                }
            }
            database.delete(tableName, "$COLUMN_TIME < ?", arrayOf(this))
        } ?: run {
            database.delete(chunkTable, null, null)
            database.delete(tableName, null, null)
        }

    }

    /**
     * 5. 删除所有表的数据 (遍历数据库中所有以 _log 结尾的表)
     */
    fun clearAllLogs(): Boolean {
        try {
            val cursor = database.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name LIKE '%_log'", null)
            cursor?.use {
                while (it.moveToNext()) {
                    val tableName = it.getString(0)
                    database.delete("${tableName}_img_chunk", null, null)
                    database.delete(tableName, null, null)
                }
            }
            return true
        } catch (e: Exception) {
            Log.e(LoggerX.TAG, "clearAllLogs: ${e.message}")
            e.printStackTrace()
            return false
        }
    }


    private var autoCleanFuture: java.util.concurrent.ScheduledFuture<*>? = null
    private var autoCleanBySizeFuture: java.util.concurrent.ScheduledFuture<*>? = null

    /**
     * 自动执行清理任务：基于数据库中实际存在的日期范围进行清理
     * @param retentionDays 保留天数 (例如: 7)
     */
    fun startAutoCleanByDate(retentionDays: Int) {
        if (retentionDays <= 0) return

        // 如果已经开启，先停止之前的任务
        autoCleanFuture?.cancel(false)

        // 立即执行一次，然后每隔 24 小时执行一次
        autoCleanFuture = scheduledExecutor.scheduleWithFixedDelay({
            try {
                performCleanupByDateRange(retentionDays)
            } catch (e: Exception) {
                Log.e(LoggerX.TAG, "Auto clean by date failed: ${e.message}")
            }
        }, 0, 24, TimeUnit.HOURS)
    }

    private fun performCleanupByDateRange(days: Int) {
        getAllLogTables().forEach { tableName ->
            // 1. 查询所有不重复的日期（YYYY-MM-DD），并升序排列
            val distinctDates = mutableListOf<String>()
            val sql = "SELECT DISTINCT substr($COLUMN_TIME, 1, 10) as log_date FROM $tableName ORDER BY log_date ASC"
            database.rawQuery(sql, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val date = cursor.getString(0)
                    if (date != null) {
                        distinctDates.add(date)
                    }
                }
            }

            // 2. 如果日期数量超过保留天数，则进行清理
            if (distinctDates.size > days) {
                val deleteCount = distinctDates.size - days
                // 获取需要删除的最后一个日期（第 deleteCount 个日期）
                val cutoffDate = distinctDates[deleteCount - 1]

                // 3. 删除该日期及其之前的所有数据
                // 使用 substr(time, 1, 10) <= ? 来确保删除包含 cutoffDate 在内的所有数据
                val rows = database.delete(tableName, "substr($COLUMN_TIME, 1, 10) <= ?", arrayOf(cutoffDate))
                Log.d(LoggerX.TAG, "Auto clean by date for $tableName: deleted $rows rows on or before $cutoffDate. Total dates: ${distinctDates.size}, retention: $days")
            }
        }
    }

    /**
     * 自动执行清理任务：基于数据库文件大小进行清理
     * @param maxSizeMb 数据库文件最大允许大小 (MB)
     * @param cleanSizeMb 每次清理尝试减少的大小 (MB)，实际清理量可能因数据分布和 VACUUM 行为而异
     */
    fun startAutoCleanBySize(maxSizeMb: Double, cleanSizeMb: Double) {
        if (maxSizeMb <= 0 || cleanSizeMb <= 0) return

        // 如果已经开启，先停止之前的任务
        autoCleanBySizeFuture?.cancel(false)

        // 立即执行一次，然后每隔 24 小时执行一次
        autoCleanBySizeFuture = scheduledExecutor.scheduleWithFixedDelay({
            try {
                performCleanupBySize(maxSizeMb, cleanSizeMb)
            } catch (e: Exception) {
                Log.e(LoggerX.TAG, "Auto clean by size failed: ${e.message}")
            }
        }, 0, 24, TimeUnit.HOURS)
    }

    private fun performCleanupBySize(maxSizeMb: Double, cleanSizeMb: Double) {
        val dbFile = ContextHolder.getAppContext().getDatabasePath(LogDbHelper.DB_NAME)
        if (!dbFile.exists()) return

        var currentSizeMb = dbFile.length().toDouble() / (1024.0 * 1024.0)
        Log.d(LoggerX.TAG, "Current DB size: $currentSizeMb MB, Max size: $maxSizeMb MB")

        if (currentSizeMb > maxSizeMb) {
            Log.d(LoggerX.TAG, "DB size exceeds limit, starting cleanup...")
            val targetSizeMb = maxSizeMb - cleanSizeMb // 目标大小，尝试清理到这个大小以下

            var cleanedTotalRows = 0
            var iteration = 0
            val maxIterations = 10 // 避免无限循环

            while (currentSizeMb > targetSizeMb && iteration < maxIterations) {
                iteration++
                var rowsDeletedInIteration = 0
                getAllLogTables().forEach { tableName ->
                    // 每次删除 1000 条最旧的数据
                    val deleteSql = "DELETE FROM $tableName WHERE $COLUMN_ID IN (SELECT $COLUMN_ID FROM $tableName ORDER BY $COLUMN_TIME ASC LIMIT 1000)"
                    database.execSQL(deleteSql)
                    rowsDeletedInIteration += 1000 // 假设删除了 1000 条，实际可能少于
                }
                cleanedTotalRows += rowsDeletedInIteration

                // 重新获取文件大小
                currentSizeMb = dbFile.length().toDouble() / (1024.0 * 1024.0)
                Log.d(LoggerX.TAG, "Iteration $iteration: Deleted $rowsDeletedInIteration rows. New DB size: $currentSizeMb MB")

                // 如果文件大小没有明显变化，可能需要 VACUUM，但这里避免使用
                // 实际文件大小可能不会立即减少，但逻辑上旧数据已被删除
            }
            Log.d(LoggerX.TAG, "Auto clean by size finished. Total rows deleted: $cleanedTotalRows. Final DB size: $currentSizeMb MB")
        }
    }


    private fun getAllLogTables(): List<String> {
        val tables = mutableListOf<String>()
        val cursor = database.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name LIKE '%_log'", null)
        cursor?.use {
            while (it.moveToNext()) tables.add(it.getString(0))
        }
        return tables
    }

    // 辅助方法：拼接 SQL 条件
    private fun addFilter(sb: StringBuilder, condition: String, arg: String, args: MutableList<String>) {
        if (sb.isNotEmpty()) sb.append(" AND ")
        sb.append(condition)
        args.add(arg)
    }

    private fun cursorToMap(it: android.database.Cursor, includeImagePayload: Boolean): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            COLUMN_ID to it.getInt(it.getColumnIndexOrThrow(COLUMN_ID)),
            COLUMN_TIME to it.getString(it.getColumnIndexOrThrow(COLUMN_TIME)),
            COLUMN_LEVEL to it.getString(it.getColumnIndexOrThrow(COLUMN_LEVEL)),
            COLUMN_TAG to it.getString(it.getColumnIndexOrThrow(COLUMN_TAG)),
            COLUMN_METHOD to it.getString(it.getColumnIndexOrThrow(COLUMN_METHOD))
        )
        val message = it.getString(it.getColumnIndexOrThrow(COLUMN_MESSAGE)).orEmpty()
        val imageIndex = it.getColumnIndex(COLUMN_IS_IMAGE)
        val isImage = imageIndex >= 0 && it.getInt(imageIndex) == 1
        map[COLUMN_IS_IMAGE] = if (isImage) 1 else 0
        if (isImage) {
            val mimeIndex = it.getColumnIndex(COLUMN_MIME_TYPE)
            val thumbIndex = it.getColumnIndex(COLUMN_THUMBNAIL)
            val chunkIndex = it.getColumnIndex(COLUMN_IMAGE_CHUNKED)
            map[COLUMN_MIME_TYPE] = if (mimeIndex >= 0) it.getString(mimeIndex).orEmpty() else ""
            map[COLUMN_THUMBNAIL] = if (thumbIndex >= 0) it.getString(thumbIndex).orEmpty() else ""
            map[COLUMN_IMAGE_CHUNKED] = if (chunkIndex >= 0) it.getInt(chunkIndex) else 0
            if (includeImagePayload) {
                val payloadIndex = it.getColumnIndex(COLUMN_IMAGE_PAYLOAD)
                map[COLUMN_IMAGE_PAYLOAD] = if (payloadIndex >= 0) it.getString(payloadIndex).orEmpty() else ""
                map[COLUMN_MESSAGE] = message
            } else {
                map[COLUMN_MESSAGE] = message.ifEmpty { "[IMAGE]" }
            }
        } else {
            map[COLUMN_MESSAGE] = message
        }
        return map
    }

    private fun tableExists(tableName: String): Boolean {
        if (existedTables.contains(tableName)) return true
        return try {
            val cursor = database.rawQuery(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?",
                arrayOf(tableName)
            )
            val exists = cursor.use {
                it.moveToFirst() && it.getInt(0) > 0
            }
            if (exists) {
                existedTables.add(tableName)
                ensureTableSchema(tableName)
            }
            exists
        } catch (_: Exception) {
            false
        }
    }

    private fun ensureTableSchema(tableName: String) {
        val cached = tableColumnsCache[tableName]
        if (cached != null &&
            cached.containsAll(
                listOf(
                    COLUMN_IS_IMAGE,
                    COLUMN_MIME_TYPE,
                    COLUMN_THUMBNAIL,
                    COLUMN_IMAGE_PAYLOAD,
                    COLUMN_IMAGE_CHUNKED
                )
            )
        ) {
            return
        }
        val columns = mutableSetOf<String>()
        database.rawQuery("PRAGMA table_info($tableName)", null).use { cursor ->
            while (cursor.moveToNext()) {
                columns.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
        }
        addColumnIfMissing(tableName, columns, COLUMN_IS_IMAGE, "INTEGER DEFAULT 0")
        addColumnIfMissing(tableName, columns, COLUMN_MIME_TYPE, "TEXT")
        addColumnIfMissing(tableName, columns, COLUMN_THUMBNAIL, "TEXT")
        addColumnIfMissing(tableName, columns, COLUMN_IMAGE_PAYLOAD, "TEXT")
        addColumnIfMissing(tableName, columns, COLUMN_IMAGE_CHUNKED, "INTEGER DEFAULT 0")
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_${tableName}_time_image ON $tableName($COLUMN_TIME, $COLUMN_IS_IMAGE)")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ${tableName}_img_chunk (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                log_id INTEGER NOT NULL,
                chunk_index INTEGER NOT NULL,
                chunk_data TEXT NOT NULL
            )
            """.trimIndent()
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_${tableName}_img_chunk ON ${tableName}_img_chunk(log_id, chunk_index)")
        tableColumnsCache[tableName] = columns
    }

    private fun addColumnIfMissing(tableName: String, existing: MutableSet<String>, column: String, definition: String) {
        if (existing.contains(column)) return
        database.execSQL("ALTER TABLE $tableName ADD COLUMN $column $definition")
        existing.add(column)
    }

}

package com.hearthappy.log.db

import android.database.sqlite.SQLiteStatement
import android.util.Log
import com.hearthappy.log.Logger
import com.hearthappy.log.Logger.Companion.COLUMN_ID
import com.hearthappy.log.Logger.Companion.COLUMN_LEVEL
import com.hearthappy.log.Logger.Companion.COLUMN_MESSAGE
import com.hearthappy.log.Logger.Companion.COLUMN_METHOD
import com.hearthappy.log.Logger.Companion.COLUMN_TAG
import com.hearthappy.log.Logger.Companion.COLUMN_TIME
import com.hearthappy.log.core.ContextHolder
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object LogDbManager {
    private val dbHelper = LogDbHelper(ContextHolder.getAppContext())
    private val database = dbHelper.writableDatabase

    // 缓存不同表的 SQLiteStatement，Key 为表名
    private val statementCache = HashMap<String, SQLiteStatement>()

    // 记录已存在的表，减少查询次数
    private val existedTables = HashSet<String>()
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
        try {
            val tableName = getTableName(scopeTag)
            val stmt = ensureTable(tableName)
            stmt.clearBindings()
            stmt.bindString(1, level)
            stmt.bindString(2, classTag)
            stmt.bindString(3, method)
            stmt.bindString(4, message)
            stmt.executeInsert()
        } catch (e: Exception) {
            Log.e(Logger.TAG, "Insert failed: ${e.message}")
        }
    }

    /**
     * 1. 高级查询：支持 time, tag, level, method, message 的过滤与模糊查询
     * @param time 模糊匹配，可传 "2026-03-20" 或 "2026-03-20 15"
     * @param method 模糊匹配方法名
     */
    fun queryLogsAdvanced(scopeTag: String, time: String? = null, tag: String? = null, level: String? = null, method: String? = null, keyword: String? = null, isAsc: Boolean = false, page: Int = 1, limit: Int? = 100): List<Map<String, Any>> {
        val tableName = getTableName(scopeTag)
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
        keyword?.let {
            addFilter(selection, "$COLUMN_MESSAGE LIKE ?", "%$it%", selectionArgs)
        }

        val sortOrder = if (isAsc) "ASC" else "DESC"
        val offset = if (limit != null) maxOf(0, (page - 1) * limit) else 0
        val limitClause = when {
            limit == null -> null
            else -> "$offset, $limit"
        }
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
                list.add(cursorToMap(it))
            }
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

        val cursor = database.rawQuery(sql, null)
        cursor?.use {
            while (it.moveToNext()) {
                val value = it.getString(0)
                if (!value.isNullOrEmpty()) result.add(value)
            }
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
        return timeFormat?.run {
            database.delete(tableName, "$COLUMN_TIME < ?", arrayOf(this))
        } ?: database.delete(tableName, null, null)

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
                    database.delete(tableName, null, null)
                }
            }
            return true
        } catch (e: Exception) {
            Log.e(Logger.TAG, "clearAllLogs: ${e.message}")
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
                Log.e(Logger.TAG, "Auto clean by date failed: ${e.message}")
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
                Log.d(Logger.TAG, "Auto clean by date for $tableName: deleted $rows rows on or before $cutoffDate. Total dates: ${distinctDates.size}, retention: $days")
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
                Log.e(Logger.TAG, "Auto clean by size failed: ${e.message}")
            }
        }, 0, 24, TimeUnit.HOURS)
    }

    private fun performCleanupBySize(maxSizeMb: Double, cleanSizeMb: Double) {
        val dbFile = ContextHolder.getAppContext().getDatabasePath(LogDbHelper.DB_NAME)
        if (!dbFile.exists()) return

        var currentSizeMb = dbFile.length().toDouble() / (1024.0 * 1024.0)
        Log.d(Logger.TAG, "Current DB size: $currentSizeMb MB, Max size: $maxSizeMb MB")

        if (currentSizeMb > maxSizeMb) {
            Log.d(Logger.TAG, "DB size exceeds limit, starting cleanup...")
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
                Log.d(Logger.TAG, "Iteration $iteration: Deleted $rowsDeletedInIteration rows. New DB size: $currentSizeMb MB")

                // 如果文件大小没有明显变化，可能需要 VACUUM，但这里避免使用
                // 实际文件大小可能不会立即减少，但逻辑上旧数据已被删除
            }
            Log.d(Logger.TAG, "Auto clean by size finished. Total rows deleted: $cleanedTotalRows. Final DB size: $currentSizeMb MB")
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

    private fun cursorToMap(it: android.database.Cursor): Map<String, Any> {
        return mapOf(COLUMN_ID to it.getInt(it.getColumnIndexOrThrow(COLUMN_ID)), COLUMN_TIME to it.getString(it.getColumnIndexOrThrow(COLUMN_TIME)), COLUMN_LEVEL to it.getString(it.getColumnIndexOrThrow(COLUMN_LEVEL)), COLUMN_TAG to it.getString(it.getColumnIndexOrThrow(COLUMN_TAG)), COLUMN_METHOD to it.getString(it.getColumnIndexOrThrow(COLUMN_METHOD)), COLUMN_MESSAGE to it.getString(it.getColumnIndexOrThrow(COLUMN_MESSAGE)))
    }


}

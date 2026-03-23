package com.hearthappy.log.db

import android.content.Context
import android.database.sqlite.SQLiteStatement
import android.util.Log
import com.hearthappy.log.Logger.Companion.COLUMN_ID
import com.hearthappy.log.Logger.Companion.COLUMN_LEVEL
import com.hearthappy.log.Logger.Companion.COLUMN_MESSAGE
import com.hearthappy.log.Logger.Companion.COLUMN_METHOD
import com.hearthappy.log.Logger.Companion.COLUMN_TAG
import com.hearthappy.log.Logger.Companion.COLUMN_TIME
import com.hearthappy.log.core.LogFileManager

class LogDbManager(context: Context) {
    private val dbHelper = LogDbHelper(context)
    private val database = dbHelper.writableDatabase

    // 缓存不同表的 SQLiteStatement，Key 为表名
    private val statementCache = HashMap<String, SQLiteStatement>()

    // 记录已存在的表，减少查询次数
    private val existedTables = HashSet<String>()

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
            Log.e("LogDbManager", "Insert failed: ${e.message}")
        }
    }

    /**
     * 1. 高级查询：支持 time, tag, level, method, message 的过滤与模糊查询
     * @param time 模糊匹配，可传 "2026-03-20" 或 "2026-03-20 15"
     * @param method 模糊匹配方法名
     */
    fun queryLogsAdvanced(scopeTag: String, time: String? = null, tag: String? = null, level: String? = null, method: String? = null, keyword: String? = null, isAsc: Boolean = false, limit: Int = 100): List<Map<String, Any>> {
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
        val cursor = database.query(tableName, null, if (selection.isEmpty()) null else selection.toString(), if (selectionArgs.isEmpty()) null else selectionArgs.toTypedArray(), null, null, "$COLUMN_TIME $sortOrder", limit.toString())

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
            Log.e(LogFileManager.TAG, "clearAllLogs: ${e.message}")
            e.printStackTrace()
            return false
        }
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

    companion object {
        @Volatile private var instance: LogDbManager? = null
        fun getInstance(context: Context) = instance ?: synchronized(this) {
            instance ?: LogDbManager(context.applicationContext).also { instance = it }
        }
    }
}
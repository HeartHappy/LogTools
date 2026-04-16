package com.hearthappy.log.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.hearthappy.log.LoggerX
import com.hearthappy.log.core.ContextHolder


/**
 * Created Date: 2026/3/20
 * @author ChenRui
 * ClassDescription：数据库处理
 */
internal class LogDbHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        // 初始可以为空，表将在运行时动态创建
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 升级逻辑
    }

    /**
     * 根据表名创建日志表
     */
    fun createLogTable(db: SQLiteDatabase, tableName: String) {
        val sql = """
            CREATE TABLE IF NOT EXISTS $tableName (
                ${LoggerX.COLUMN_ID } INTEGER PRIMARY KEY AUTOINCREMENT,
                ${LoggerX.COLUMN_TIME} TIMESTAMP DEFAULT (datetime('now', 'localtime')),
                ${LoggerX.COLUMN_LEVEL} TEXT,
                ${LoggerX.COLUMN_TAG} TEXT,
                ${LoggerX.COLUMN_METHOD} TEXT,
                ${LoggerX.COLUMN_MESSAGE} TEXT,
                ${LoggerX.COLUMN_IS_IMAGE} INTEGER DEFAULT 0,
                ${LoggerX.COLUMN_MIME_TYPE} TEXT,
                ${LoggerX.COLUMN_THUMBNAIL} TEXT,
                ${LoggerX.COLUMN_IMAGE_PAYLOAD} TEXT,
                ${LoggerX.COLUMN_IMAGE_CHUNKED} INTEGER DEFAULT 0
            )
        """.trimIndent()
        db.execSQL(sql)
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_${tableName}_time_image ON $tableName(${LoggerX.COLUMN_TIME}, ${LoggerX.COLUMN_IS_IMAGE})")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ${tableName}_img_chunk (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                log_id INTEGER NOT NULL,
                chunk_index INTEGER NOT NULL,
                chunk_data TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_${tableName}_img_chunk ON ${tableName}_img_chunk(log_id, chunk_index)")
    }
    /**
     * 获取日志数据库文件的大小（单位：MB）
     */
    fun getDbFileSize(): Double {
        val dbFile = ContextHolder.getAppContext().getDatabasePath(DB_NAME)
        return if (dbFile.exists()) {
            dbFile.length().toDouble() / (1024 * 1024)
        } else 0.0
    }
    companion object {
        const val DB_NAME = "hearthappy_logs.db"
        const val DB_VERSION = 2
    }
}

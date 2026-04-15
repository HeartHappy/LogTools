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
                ${LoggerX.COLUMN_MESSAGE} TEXT
            )
        """.trimIndent()
        db.execSQL(sql)
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
        const val DB_VERSION = 1
    }
}
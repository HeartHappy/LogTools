package com.hearthappy.log.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.hearthappy.log.Logger


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
                ${Logger.COLUMN_ID } INTEGER PRIMARY KEY AUTOINCREMENT,
                ${Logger.COLUMN_TIME} TIMESTAMP DEFAULT (datetime('now', 'localtime')),
                ${Logger.COLUMN_LEVEL} TEXT,
                ${Logger.COLUMN_TAG} TEXT,
                ${Logger.COLUMN_METHOD} TEXT,
                ${Logger.COLUMN_MESSAGE} TEXT
            )
        """.trimIndent()
        db.execSQL(sql)
    }

    companion object {
        const val DB_NAME = "hearthappy_logs.db"
        const val DB_VERSION = 1
    }
}
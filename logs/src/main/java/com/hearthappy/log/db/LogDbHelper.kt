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

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.enableWriteAheadLogging()
        applyPragma(db, "PRAGMA synchronous=NORMAL")
        applyPragma(db, "PRAGMA page_size=65536")
        applyPragma(db, "PRAGMA cache_size=-64000")
    }

    override fun onCreate(db: SQLiteDatabase) {
        applyOpenPragma(db)
        createImageTable(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        applyOpenPragma(db)
        createImageTable(db)
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        applyOpenPragma(db)
    }

    /**
     * 根据表名创建日志表
     */
    fun createLogTable(db: SQLiteDatabase, tableName: String) {
        createImageTable(db)
        val sql = """
            CREATE TABLE IF NOT EXISTS $tableName (
                ${LoggerX.COLUMN_ID } INTEGER PRIMARY KEY AUTOINCREMENT,
                ${LoggerX.COLUMN_TIME} TIMESTAMP DEFAULT (datetime('now', 'localtime')),
                ${LoggerX.COLUMN_LEVEL} TEXT,
                ${LoggerX.COLUMN_TAG} TEXT,
                ${LoggerX.COLUMN_METHOD} TEXT,
                ${LoggerX.COLUMN_MESSAGE} TEXT,
                ${LoggerX.COLUMN_THUMBNAIL} TEXT,
                ${LoggerX.COLUMN_IMAGE_ID} INTEGER DEFAULT -1
            )
        """.trimIndent()
        db.execSQL(sql)
        ensureScopeTableSchema(db, tableName)
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_${tableName}_time ON $tableName(${LoggerX.COLUMN_TIME} DESC)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_${tableName}_image_id ON $tableName(${LoggerX.COLUMN_IMAGE_ID})")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_${tableName}_level_time ON $tableName(${LoggerX.COLUMN_LEVEL}, ${LoggerX.COLUMN_TIME} DESC)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_${tableName}_tag_time ON $tableName(${LoggerX.COLUMN_TAG}, ${LoggerX.COLUMN_TIME} DESC)")
    }

    private fun ensureScopeTableSchema(db: SQLiteDatabase, tableName: String) {
        val columns = mutableSetOf<String>()
        db.rawQuery("PRAGMA table_info($tableName)", null).use { cursor ->
            while (cursor.moveToNext()) {
                columns.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
        }
        val requiredColumns = setOf(
            LoggerX.COLUMN_ID,
            LoggerX.COLUMN_TIME,
            LoggerX.COLUMN_LEVEL,
            LoggerX.COLUMN_TAG,
            LoggerX.COLUMN_METHOD,
            LoggerX.COLUMN_MESSAGE,
            LoggerX.COLUMN_THUMBNAIL,
            LoggerX.COLUMN_IMAGE_ID
        )
        val hasLegacyImageColumns = columns.contains("image_payload") ||
            columns.contains("image_chunked") ||
            columns.contains("mime_type") ||
            columns.contains("is_image")
        if (!columns.containsAll(requiredColumns) || hasLegacyImageColumns) {
            rebuildScopeTable(db, tableName, columns)
        }
    }

    private fun rebuildScopeTable(db: SQLiteDatabase, tableName: String, existingColumns: Set<String>) {
        val newTable = "${tableName}_new"
        db.execSQL("DROP TABLE IF EXISTS $newTable")
        db.execSQL(
            """
            CREATE TABLE $newTable (
                ${LoggerX.COLUMN_ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                ${LoggerX.COLUMN_TIME} TIMESTAMP DEFAULT (datetime('now', 'localtime')),
                ${LoggerX.COLUMN_LEVEL} TEXT,
                ${LoggerX.COLUMN_TAG} TEXT,
                ${LoggerX.COLUMN_METHOD} TEXT,
                ${LoggerX.COLUMN_MESSAGE} TEXT,
                ${LoggerX.COLUMN_THUMBNAIL} TEXT,
                ${LoggerX.COLUMN_IMAGE_ID} INTEGER DEFAULT -1
            )
            """.trimIndent()
        )
        val selectParts = listOf(
            if (existingColumns.contains(LoggerX.COLUMN_ID)) LoggerX.COLUMN_ID else "NULL AS ${LoggerX.COLUMN_ID}",
            if (existingColumns.contains(LoggerX.COLUMN_TIME)) LoggerX.COLUMN_TIME else "datetime('now','localtime') AS ${LoggerX.COLUMN_TIME}",
            if (existingColumns.contains(LoggerX.COLUMN_LEVEL)) LoggerX.COLUMN_LEVEL else "'' AS ${LoggerX.COLUMN_LEVEL}",
            if (existingColumns.contains(LoggerX.COLUMN_TAG)) LoggerX.COLUMN_TAG else "'' AS ${LoggerX.COLUMN_TAG}",
            if (existingColumns.contains(LoggerX.COLUMN_METHOD)) LoggerX.COLUMN_METHOD else "'' AS ${LoggerX.COLUMN_METHOD}",
            if (existingColumns.contains(LoggerX.COLUMN_MESSAGE)) LoggerX.COLUMN_MESSAGE else "'' AS ${LoggerX.COLUMN_MESSAGE}",
            if (existingColumns.contains(LoggerX.COLUMN_THUMBNAIL)) LoggerX.COLUMN_THUMBNAIL else "'' AS ${LoggerX.COLUMN_THUMBNAIL}",
            "-1 AS ${LoggerX.COLUMN_IMAGE_ID}"
        )
        val sql = "INSERT INTO $newTable (${columnsForInsert()}) SELECT ${selectParts.joinToString(",")} FROM $tableName"
        db.execSQL(sql)
        db.execSQL("DROP TABLE IF EXISTS $tableName")
        db.execSQL("ALTER TABLE $newTable RENAME TO $tableName")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_${tableName}_time ON $tableName(${LoggerX.COLUMN_TIME} DESC)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_${tableName}_image_id ON $tableName(${LoggerX.COLUMN_IMAGE_ID})")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_${tableName}_level_time ON $tableName(${LoggerX.COLUMN_LEVEL}, ${LoggerX.COLUMN_TIME} DESC)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_${tableName}_tag_time ON $tableName(${LoggerX.COLUMN_TAG}, ${LoggerX.COLUMN_TIME} DESC)")
    }

    private fun columnsForInsert(): String {
        return listOf(
            LoggerX.COLUMN_ID,
            LoggerX.COLUMN_TIME,
            LoggerX.COLUMN_LEVEL,
            LoggerX.COLUMN_TAG,
            LoggerX.COLUMN_METHOD,
            LoggerX.COLUMN_MESSAGE,
            LoggerX.COLUMN_THUMBNAIL,
            LoggerX.COLUMN_IMAGE_ID
        ).joinToString(",")
    }

    private fun createImageTable(db: SQLiteDatabase) {
        val requiredColumns = setOf(
            "id",
            "scope_id",
            LoggerX.COLUMN_MEDIA_TYPE,
            LoggerX.COLUMN_COMPRESSED_IMAGE,
            "width",
            "height",
            LoggerX.COLUMN_ORIGINAL_SIZE,
            LoggerX.COLUMN_COMPRESSED_SIZE,
            LoggerX.COLUMN_COMPRESSION_RATIO,
            LoggerX.COLUMN_CHECKSUM_SHA256
        )
        val hasImageTable = db.rawQuery(
            "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='log_image'",
            null
        ).use { c -> c.moveToFirst() && c.getInt(0) > 0 }

        if (!hasImageTable) {
            createImageTableFresh(db)
            return
        }

        val columns = mutableSetOf<String>()
        db.rawQuery("PRAGMA table_info(log_image)", null).use { cursor ->
            while (cursor.moveToNext()) {
                columns.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
        }
        val hasLegacyTimestamp = columns.contains("timestamp")
        if (!columns.containsAll(requiredColumns) || hasLegacyTimestamp) {
            rebuildImageTable(db, columns)
        } else {
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_image_scope_id ON log_image(scope_id)")
        }
    }

    private fun createImageTableFresh(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS log_image (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                scope_id INTEGER NOT NULL,
                media_type TEXT NOT NULL,
                compressed_image BLOB NOT NULL,
                width INTEGER,
                height INTEGER,
                original_size INTEGER,
                compressed_size INTEGER,
                compression_ratio REAL,
                checksum_sha256 TEXT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_image_scope_id ON log_image(scope_id)")
    }

    private fun rebuildImageTable(db: SQLiteDatabase, existingColumns: Set<String>) {
        val newTable = "log_image_new"
        db.execSQL("DROP TABLE IF EXISTS $newTable")
        db.execSQL(
            """
            CREATE TABLE $newTable (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                scope_id INTEGER NOT NULL,
                media_type TEXT NOT NULL,
                compressed_image BLOB NOT NULL,
                width INTEGER,
                height INTEGER,
                original_size INTEGER,
                compressed_size INTEGER,
                compression_ratio REAL,
                checksum_sha256 TEXT
            )
            """.trimIndent()
        )
        val selectParts = listOf(
            if (existingColumns.contains("id")) "id" else "NULL AS id",
            if (existingColumns.contains("scope_id")) "scope_id" else "-1 AS scope_id",
            if (existingColumns.contains(LoggerX.COLUMN_MEDIA_TYPE)) LoggerX.COLUMN_MEDIA_TYPE else "'image/webp' AS ${LoggerX.COLUMN_MEDIA_TYPE}",
            if (existingColumns.contains(LoggerX.COLUMN_COMPRESSED_IMAGE)) LoggerX.COLUMN_COMPRESSED_IMAGE else "X'' AS ${LoggerX.COLUMN_COMPRESSED_IMAGE}",
            if (existingColumns.contains("width")) "width" else "0 AS width",
            if (existingColumns.contains("height")) "height" else "0 AS height",
            if (existingColumns.contains(LoggerX.COLUMN_ORIGINAL_SIZE)) LoggerX.COLUMN_ORIGINAL_SIZE else "0 AS ${LoggerX.COLUMN_ORIGINAL_SIZE}",
            if (existingColumns.contains(LoggerX.COLUMN_COMPRESSED_SIZE)) LoggerX.COLUMN_COMPRESSED_SIZE else "0 AS ${LoggerX.COLUMN_COMPRESSED_SIZE}",
            if (existingColumns.contains(LoggerX.COLUMN_COMPRESSION_RATIO)) LoggerX.COLUMN_COMPRESSION_RATIO else "0.0 AS ${LoggerX.COLUMN_COMPRESSION_RATIO}",
            if (existingColumns.contains(LoggerX.COLUMN_CHECKSUM_SHA256)) LoggerX.COLUMN_CHECKSUM_SHA256 else "'' AS ${LoggerX.COLUMN_CHECKSUM_SHA256}"
        )
        db.execSQL(
            """
            INSERT INTO $newTable (
                id,scope_id,media_type,compressed_image,width,height,original_size,compressed_size,compression_ratio,checksum_sha256
            )
            SELECT ${selectParts.joinToString(",")} FROM log_image
            """.trimIndent()
        )
        db.execSQL("DROP TABLE IF EXISTS log_image")
        db.execSQL("ALTER TABLE $newTable RENAME TO log_image")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_image_scope_id ON log_image(scope_id)")
    }

    private fun applyOpenPragma(db: SQLiteDatabase) {
        applyPragma(db, "PRAGMA journal_mode=WAL")
        applyPragma(db, "PRAGMA synchronous=NORMAL")
        applyPragma(db, "PRAGMA cache_size=-64000")
    }

    private fun applyPragma(db: SQLiteDatabase, sql: String) {
        runCatching {
            db.rawQuery(sql, null).use { }
        }.recoverCatching {
            db.execSQL(sql)
        }
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
        const val DB_VERSION = 5
    }
}

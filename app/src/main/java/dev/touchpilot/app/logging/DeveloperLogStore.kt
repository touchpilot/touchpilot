package dev.touchpilot.app.logging

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DeveloperLogStore(context: Context) {
    private val database = DeveloperLogDatabase(context.applicationContext)

    @Synchronized
    fun insert(entry: DeveloperLogEntry): Long {
        val redacted = entry.redacted()
        val id = database.writableDatabase.insert(
            DeveloperLogDatabase.Table,
            null,
            ContentValues().apply {
                put("timestamp_millis", redacted.timestampMillis)
                put("type", redacted.type)
                put("actor", redacted.actor)
                put("name", redacted.name)
                put("status", redacted.status)
                put("source", redacted.source)
                put("result", redacted.result)
                put("error_details", redacted.errorDetails)
                put("payload_summary", redacted.payloadSummary)
                put("details", redacted.details)
                put("target", redacted.target)
                put("policy_decision", redacted.policyDecision)
            }
        )
        trimToMaxEntries()
        return id
    }

    @Synchronized
    fun recent(limit: Int = DefaultRecentLimit): List<DeveloperLogEntry> {
        return database.readableDatabase.query(
            DeveloperLogDatabase.Table,
            Columns,
            null,
            null,
            null,
            null,
            "timestamp_millis DESC, id DESC",
            limit.coerceAtLeast(1).toString()
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toDeveloperLogEntry())
                }
            }
        }
    }

    @Synchronized
    fun find(id: Long): DeveloperLogEntry? {
        return database.readableDatabase.query(
            DeveloperLogDatabase.Table,
            Columns,
            "id = ?",
            arrayOf(id.toString()),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toDeveloperLogEntry() else null
        }
    }

    @Synchronized
    fun clear() {
        database.writableDatabase.delete(DeveloperLogDatabase.Table, null, null)
    }

    private fun trimToMaxEntries() {
        database.writableDatabase.execSQL(
            """
            DELETE FROM ${DeveloperLogDatabase.Table}
            WHERE id NOT IN (
                SELECT id FROM ${DeveloperLogDatabase.Table}
                ORDER BY timestamp_millis DESC, id DESC
                LIMIT $MaxEntries
            )
            """.trimIndent()
        )
    }

    private fun Cursor.toDeveloperLogEntry(): DeveloperLogEntry {
        return DeveloperLogEntry(
            id = getLong(getColumnIndexOrThrow("id")),
            timestampMillis = getLong(getColumnIndexOrThrow("timestamp_millis")),
            type = getString(getColumnIndexOrThrow("type")).orEmpty(),
            actor = getString(getColumnIndexOrThrow("actor")).orEmpty(),
            name = getString(getColumnIndexOrThrow("name")).orEmpty(),
            status = getString(getColumnIndexOrThrow("status")).orEmpty(),
            source = getString(getColumnIndexOrThrow("source")).orEmpty(),
            result = getString(getColumnIndexOrThrow("result")).orEmpty(),
            errorDetails = getString(getColumnIndexOrThrow("error_details")).orEmpty(),
            payloadSummary = getString(getColumnIndexOrThrow("payload_summary")).orEmpty(),
            details = getString(getColumnIndexOrThrow("details")).orEmpty(),
            target = columnString("target"),
            policyDecision = columnString("policy_decision"),
        )
    }

    private fun Cursor.columnString(name: String): String {
        val index = getColumnIndex(name)
        return if (index >= 0) getString(index).orEmpty() else ""
    }

    private class DeveloperLogDatabase(context: Context) : SQLiteOpenHelper(
        context,
        DatabaseName,
        null,
        DatabaseVersion
    ) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $Table (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    timestamp_millis INTEGER NOT NULL,
                    type TEXT NOT NULL,
                    actor TEXT NOT NULL,
                    name TEXT NOT NULL,
                    status TEXT NOT NULL,
                    source TEXT NOT NULL,
                    result TEXT NOT NULL,
                    error_details TEXT NOT NULL,
                    payload_summary TEXT NOT NULL,
                    details TEXT NOT NULL,
                    target TEXT NOT NULL DEFAULT '',
                    policy_decision TEXT NOT NULL DEFAULT ''
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX index_developer_logs_timestamp ON $Table(timestamp_millis DESC)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 1) {
                onCreate(db)
            }
            if (oldVersion < 2) {
                db.execSQL("ALTER TABLE $Table ADD COLUMN target TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE $Table ADD COLUMN policy_decision TEXT NOT NULL DEFAULT ''")
            }
        }

        companion object {
            const val Table = "developer_logs"
        }
    }

    companion object {
        private const val DatabaseName = "touchpilot-developer-logs.db"
        private const val DatabaseVersion = 2
        private const val MaxEntries = 500
        private const val DefaultRecentLimit = 100
        private val Columns = arrayOf(
            "id",
            "timestamp_millis",
            "type",
            "actor",
            "name",
            "status",
            "source",
            "result",
            "error_details",
            "payload_summary",
            "details",
            "target",
            "policy_decision",
        )
    }
}

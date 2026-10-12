package com.kivan.carmelit

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

/** What the trip log records; the wording of each [TripLog.add] detail is for a person to read. */
enum class LogKind {
    ZONE_EXIT, ZONE_ENTER, ZONE_ERROR, ZONES_ADDED, ZONES_FAILED,
    GPS_START, GPS_REFUSED, GPS_STOP, TRIP_OPEN, TRIP_SAVED, TRIP_DROPPED, FAKE,
}

data class LogEntry(val t: Long, val kind: LogKind, val detail: String)

/**
 * Every zone report, GPS start or refusal, and trip verdict, kept 60 days in SQLite
 * (`adb exec-out run-as com.kivan.carmelit cat databases/triplog.db`). It is what explains a missed trip.
 */
class TripLog private constructor(context: Context) : SQLiteOpenHelper(context, "triplog.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE log(t INTEGER NOT NULL, kind TEXT NOT NULL, detail TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE INDEX log_t ON log(t)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    override fun onOpen(db: SQLiteDatabase) {
        if (!db.isReadOnly) db.delete("log", "t < ?", arrayOf((now() - KEEP_SEC).toString()))
    }

    fun add(kind: LogKind, detail: String = "") {
        Log.i("Carmelit", "$kind $detail")
        val row = ContentValues().apply { put("t", now()); put("kind", kind.name); put("detail", detail) }
        runCatching { writableDatabase.insert("log", null, row) }
            .onFailure { Log.w("Carmelit", "trip log write failed", it) }
    }

    fun last(kind: LogKind): LogEntry? = last(listOf(kind))

    fun last(kinds: List<LogKind>): LogEntry? = runCatching {
        val marks = kinds.joinToString(",") { "?" }
        readableDatabase.rawQuery(
            "SELECT t, kind, detail FROM log WHERE kind IN ($marks) ORDER BY t DESC, rowid DESC LIMIT 1",
            kinds.map { it.name }.toTypedArray(),
        ).use { c -> if (c.moveToFirst()) LogEntry(c.getLong(0), LogKind.valueOf(c.getString(1)), c.getString(2)) else null }
    }.getOrNull()

    companion object {
        private const val KEEP_SEC = 60 * 24 * 3600L
        private fun now() = System.currentTimeMillis() / 1000

        @Volatile private var instance: TripLog? = null

        fun get(context: Context): TripLog =
            instance ?: synchronized(this) { instance ?: TripLog(context.applicationContext).also { instance = it } }
    }
}

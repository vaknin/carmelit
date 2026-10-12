package com.kivan.carmelit

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.edit

/**
 * Applies zone reports, GPS fixes and the clock to the one open trip ([onZone], [Track.judge]),
 * saving what ends. Main thread only: the receivers and [TrackService] all run there.
 */
object TripRecorder {
    private const val SERVICE_SETTLE_MS = 3_000L
    private fun now() = System.currentTimeMillis() / 1000

    /** The open trip, from memory or the file; one that is over by now is ended first. */
    fun open(context: Context): Track? {
        val t = TripSession.active.value
            ?: TrackStore(context).active()?.also { TripSession.active.value = it }
            ?: return null
        val ended = t.endedAt(now()) ?: return t
        apply(context, TripChange(null, ended))
        return null
    }

    fun zoneReported(context: Context, e: ZoneEvent) {
        val prefs = context.getSharedPreferences("recorder", Context.MODE_PRIVATE)
        // A "left" that was reported after the arrival it precedes is old news, not a new trip.
        if (!e.entered && e.at.t < prefs.getLong("lastEnterAt", 0)) {
            TripLog.get(context).add(LogKind.ZONE_ERROR, "late exit ignored: ${e.door.name.lowercase()}")
            return
        }
        if (e.entered) prefs.edit { putLong("lastEnterAt", maxOf(e.at.t, prefs.getLong("lastEnterAt", 0))) }
        val open = open(context)
        apply(context, onZone(open, e, SettingsStore(context).load(), now()), open)
    }

    /** Adds GPS fixes to the open trip; true while GPS is still worth running. */
    fun onFixes(context: Context, fixes: List<Fix>): Boolean {
        val t = open(context) ?: return false
        val store = TrackStore(context)
        fixes.forEach(store::append)
        val grown = t.copy(fixes = t.fixes + fixes)
        TripSession.active.value = grown
        val ended = grown.endedAt(now())
        if (ended != null) {
            apply(context, TripChange(null, ended))
            return false
        }
        return grown.wantsGps(now())
    }

    /** The clock alone (underground there are no fixes); true while GPS is still worth running. */
    fun onTick(context: Context): Boolean = open(context)?.wantsGps(now()) ?: false

    private fun apply(context: Context, c: TripChange, before: Track? = TripSession.active.value) {
        if (c.ended == null && !c.opened) return
        val store = TrackStore(context)
        val log = TripLog.get(context)
        c.ended?.let { e ->
            store.end(e.track, e.verdict)
            if (e.run != null) {
                save(context, e.run)
                log.add(LogKind.TRIP_SAVED, describe(e.run))
            } else {
                log.add(LogKind.TRIP_DROPPED, e.why)
            }
        }
        TripSession.active.value = c.open
        if (c.opened) {
            val t = c.open!!
            store.begin(t)
            log.add(LogKind.TRIP_OPEN, if (t.goingHome) "left work" else "left home")
            startGps(context)
        } else if (before != null) {
            stopGps(context)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var gpsRequestedAt = 0L

    /** Android wants startForeground() within seconds of a start; a stop right behind a start waits for it. */
    private fun stopGps(context: Context) {
        val wait = gpsRequestedAt + SERVICE_SETTLE_MS - SystemClock.elapsedRealtime()
        val stop = { if (TripSession.active.value == null) context.stopService(Intent(context, TrackService::class.java)) }
        if (wait > 0) handler.postDelayed(stop, wait) else stop()
    }

    private fun save(context: Context, run: Run) {
        val runs = RunStore(context)
        val r = record(run, runs.active(), runs.all(), SettingsStore(context).load())
        runs.saveAll(r.runs)
        runs.saveActive(r.active)
        TripSession.lastSaved.value = r.saved
        TripSession.runsChanged.value++
    }

    private fun startGps(context: Context) {
        val log = TripLog.get(context)
        if (!context.canRecord()) {
            log.add(LogKind.GPS_REFUSED, "no location permission")
            return
        }
        try {
            gpsRequestedAt = SystemClock.elapsedRealtime()
            TrackService.start(context)
        } catch (e: IllegalStateException) {
            // Android 12+ refuses a location service started from the background when the start is not exempt.
            log.add(LogKind.GPS_REFUSED, e.javaClass.simpleName)
        }
    }

    private fun describe(r: Run): String {
        val minutes = r.doorToDoor?.let { "${(it + 30) / 60} min" } ?: "? min"
        return (if (r.goingHome) "to home " else "to work ") + minutes + if (r.rodeCarmelit) "" else ", no Carmelit"
    }
}

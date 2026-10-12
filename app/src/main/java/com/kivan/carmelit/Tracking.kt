package com.kivan.carmelit

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.core.content.edit
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/**
 * What the screen needs to know about recording, shared in-process with [TrackService]:
 * the trip in progress, and a counter bumped whenever a trip changed the run store.
 */
object TripSession {
    val active = MutableStateFlow<Track?>(null)
    val runsChanged = MutableStateFlow(0)
    /** The run the last trip saved, for the "Saved:" line. */
    val lastSaved = MutableStateFlow<Run?>(null)
}

/**
 * Tracks as text files: `active.txt` while a trip is open (header, then one fix per line, appended),
 * renamed to `<leftAt>-<endedAt>-<verdict>.txt` when it ends; the last [KEEP] are kept for debugging
 * (`adb shell run-as com.kivan.carmelit ls files/tracks`).
 */
class TrackStore(context: Context) {
    private val dir = File(context.filesDir, "tracks").apply { mkdirs() }
    private val activeFile = File(dir, "active.txt")

    fun active(): Track? = activeFile.takeIf { it.exists() }?.readLines()?.let(Track::decode)

    /** Starts the file with the header and the fixes the trip already has (the trigger). */
    fun begin(t: Track) = activeFile.writeText((listOf(t.header()) + t.fixes.map(Track::encodeFix)).joinToString("\n", postfix = "\n"))

    fun append(f: Fix) = activeFile.appendText(Track.encodeFix(f) + "\n")

    fun end(t: Track, verdict: Verdict) {
        val endedAt = System.currentTimeMillis() / 1000
        if (activeFile.exists()) activeFile.renameTo(File(dir, "${t.leftAt}-$endedAt-${verdict.name.lowercase()}.txt"))
        dir.listFiles { f -> f.name != activeFile.name }?.sortedBy { it.name }?.dropLast(KEEP)?.forEach { it.delete() }
    }

    private companion object { const val KEEP = 30 }
}

fun Context.hasPermission(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

/** Fine location both in use and in the background: what geofences and the service need. */
fun Context.canRecord() =
    hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) && hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

/** What the zones are doing, for the Settings line. */
data class ZoneState(val ok: Boolean, val addedAt: Long?, val error: String?)

/**
 * The home and work zones, for leaving and arriving. Added only when a door or the permission
 * changed ([zoneSignature]), and again by an hourly retry while Android has dropped them.
 */
object Geofences {
    private const val HOME = "home"
    private const val WORK = "work"
    private const val ALERT_CHANNEL = "alerts"
    private const val ALERT_ID = 2
    private const val ZONE_REFRESH_SEC = 24 * 3600L

    private fun intent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, GeofenceReceiver::class.java),
        // Mutable: Play Services fills in the event.
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )

    private fun retryIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 1, Intent(context, ZoneRetryReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun prefs(context: Context) = context.getSharedPreferences("zones", Context.MODE_PRIVATE)

    fun state(context: Context, s: Settings): ZoneState {
        val p = prefs(context)
        val ok = p.getString("sig", null) == zoneSignature(s, context.canRecord()) && !p.getBoolean("dropped", false)
        return ZoneState(ok, p.getLong("addedAt", 0).takeIf { it > 0 }, p.getString("error", null))
    }

    /** Makes the zones match [s]; does nothing when they already do, unless [force] or Android dropped them. */
    @SuppressLint("MissingPermission") // checked by canRecord()
    fun ensure(context: Context, s: Settings, force: Boolean = false) {
        val p = prefs(context)
        val sig = zoneSignature(s, context.canRecord())
        // Play Services can lose zones without a word (force-stop, data clear), so a day-old add is repeated.
        val fresh = System.currentTimeMillis() / 1000 - p.getLong("addedAt", 0) < ZONE_REFRESH_SEC
        if (!force && p.getString("sig", null) == sig && !p.getBoolean("dropped", false) && fresh) return
        val client = LocationServices.getGeofencingClient(context)
        val pi = intent(context)
        if (s.homeLL == null || s.workLL == null || !context.canRecord()) {
            client.removeGeofences(pi)
            p.edit { putString("sig", sig); remove("addedAt"); remove("error"); remove("errorAt"); putBoolean("dropped", false) }
            settle(context)
            return
        }
        val fences = listOf(HOME to s.homeLL, WORK to s.workLL).map { (id, at) ->
            Geofence.Builder()
                .setRequestId(id)
                .setCircularRegion(at.lat, at.lng, FENCE_RADIUS_M)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                .build()
        }
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(0) // no event for already being inside or outside when registering
            .addGeofences(fences)
            .build()
        // Adding replaces zones with the same id.
        client.addGeofences(request, pi)
            .addOnSuccessListener {
                val now = System.currentTimeMillis() / 1000
                p.edit { putString("sig", sig); putLong("addedAt", now); remove("error"); remove("errorAt"); putBoolean("dropped", false) }
                TripLog.get(context).add(LogKind.ZONES_ADDED)
                settle(context)
            }
            .addOnFailureListener {
                val code = (it as? ApiException)?.statusCode
                markDropped(context, code?.let(GeofenceStatusCodes::getStatusCodeString) ?: it.javaClass.simpleName, LogKind.ZONES_FAILED)
            }
    }

    /** Android dropped the zones or refused to add them: remember it, retry hourly, tell the user. */
    fun markDropped(context: Context, error: String, kind: LogKind = LogKind.ZONES_FAILED) {
        val p = prefs(context)
        val first = !p.getBoolean("dropped", false)
        p.edit { putString("error", error); putLong("errorAt", System.currentTimeMillis() / 1000); putBoolean("dropped", true) }
        TripLog.get(context).add(kind, error)
        context.getSystemService(AlarmManager::class.java).setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_HOUR,
            AlarmManager.INTERVAL_HOUR, retryIntent(context),
        )
        if (first) notifyAlert(context) // the hourly retry failing again must not buzz again
    }

    /** Zones are as wanted: no retry and no alert needed. */
    private fun settle(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(retryIntent(context))
        context.getSystemService(NotificationManager::class.java).cancel(ALERT_ID)
    }

    private fun notifyAlert(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(ALERT_CHANNEL, "Problems", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        nm.notify(
            ALERT_ID,
            android.app.Notification.Builder(context, ALERT_CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Trips aren't being recorded")
                .setContentText("Location is off, or Android stopped watching home and work. Tap to fix.")
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
    }

    fun door(requestId: String): Door? = when (requestId) {
        HOME -> Door.HOME
        WORK -> Door.WORK
        else -> null
    }
}

/** A zone report becomes a trip event; the trip itself is [TripRecorder]'s. */
class GeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        val log = TripLog.get(context)
        if (event.hasError()) {
            val name = GeofenceStatusCodes.getStatusCodeString(event.errorCode)
            log.add(LogKind.ZONE_ERROR, name)
            if (event.errorCode == GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE) Geofences.markDropped(context, name, LogKind.ZONES_FAILED)
            return
        }
        val entered = when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> true
            Geofence.GEOFENCE_TRANSITION_EXIT -> false
            else -> return
        }
        val door = event.triggeringGeofences?.firstNotNullOfOrNull { Geofences.door(it.requestId) } ?: return
        val s = SettingsStore(context).load()
        val doorLL = (if (door == Door.HOME) s.homeLL else s.workLL) ?: return
        val now = System.currentTimeMillis() / 1000
        val loc = event.triggeringLocation
        // No location: stand in the door, a walk's width from it on the way out; bad accuracy so it never counts as a GPS fix.
        val fix = if (loc != null) Fix(loc.time / 1000, LatLng(loc.latitude, loc.longitude), loc.accuracy)
        else Fix(if (entered) now else now - (FENCE_RADIUS_M / WALK_MPS).toLong(), doorLL, 9999f)
        log.add(
            if (entered) LogKind.ZONE_ENTER else LogKind.ZONE_EXIT,
            "${door.name.lowercase()} acc=${fix.acc.toInt()} late=${now - fix.t}s" + if (loc == null) " no location" else "",
        )
        TripRecorder.zoneReported(context, ZoneEvent(door, entered, fix))
    }
}

/** Zones are forgotten on reboot and on app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Geofences.ensure(context, SettingsStore(context).load(), force = true)
    }
}

/** The hourly retry while Android has dropped the zones. */
class ZoneRetryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Geofences.ensure(context, SettingsStore(context).load(), force = true)
    }
}

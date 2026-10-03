package com.kivan.carmelit

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

private const val TAG = "Carmelit"

/**
 * What the screen needs to know about recording, shared in-process with [TrackService]:
 * the session in progress, and a counter bumped whenever the service changed the run store.
 */
object TripSession {
    val active = MutableStateFlow<Track?>(null)
    val runsChanged = MutableStateFlow(0)
    /** The run the last session saved, for the "Saved:" line. */
    val lastSaved = MutableStateFlow<Run?>(null)
}

/**
 * Tracks as text files: `active.txt` while recording (header, then one fix per line, appended),
 * renamed to `<leftAt>-<verdict>.txt` when the session ends; the last [KEEP] are kept for debugging
 * (`adb shell run-as com.kivan.carmelit ls files/tracks`).
 */
class TrackStore(context: Context) {
    private val dir = File(context.filesDir, "tracks").apply { mkdirs() }
    private val activeFile = File(dir, "active.txt")

    fun active(): Track? = activeFile.takeIf { it.exists() }?.readLines()?.let(Track::decode)
    fun begin(t: Track) = activeFile.writeText(t.header() + "\n")
    fun append(f: Fix) = activeFile.appendText(Track.encodeFix(f) + "\n")

    fun end(t: Track, verdict: Verdict) {
        if (activeFile.exists()) activeFile.renameTo(File(dir, "${t.leftAt}-${verdict.name.lowercase()}.txt"))
        dir.listFiles { f -> f.name != activeFile.name }?.sortedBy { it.name }?.dropLast(KEEP)?.forEach { it.delete() }
    }

    private companion object { const val KEEP = 30 }
}

fun Context.hasPermission(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

/** Fine location both in use and in the background: what geofences and the service need. */
fun Context.canRecord() =
    hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) && hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

/** The home and work geofences, exit-only. Re-registered on every app start, settings save and boot. */
object Geofences {
    private const val HOME = "home"
    private const val WORK = "work"

    private fun intent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, GeofenceReceiver::class.java),
        // Mutable: Play Services fills in the event.
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )

    @SuppressLint("MissingPermission") // checked by canRecord()
    fun sync(context: Context, s: Settings) {
        val client = LocationServices.getGeofencingClient(context)
        val pi = intent(context)
        // Adding replaces fences with the same id, so removing is only for a door being cleared or access lost.
        if (s.homeLL == null || s.workLL == null || !context.canRecord()) {
            client.removeGeofences(pi)
            return
        }
        val fences = listOf(HOME to s.homeLL, WORK to s.workLL).map { (id, p) ->
            Geofence.Builder()
                .setRequestId(id)
                .setCircularRegion(p.lat, p.lng, FENCE_RADIUS_M)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_EXIT)
                .build()
        }
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(0) // no event for already being outside when registering
            .addGeofences(fences)
            .build()
        client.addGeofences(request, pi)
            .addOnFailureListener { Log.w(TAG, "geofences not added", it) }
    }

    /** Leaving work means heading home; leaving home, heading to work. */
    fun goingHome(requestId: String): Boolean? = when (requestId) {
        WORK -> true
        HOME -> false
        else -> null
    }
}

/** Leaving a fence starts a [TrackService] session (geofence broadcasts may start location services). */
class GeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError() || event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_EXIT) return
        val goingHome = event.triggeringGeofences?.firstNotNullOfOrNull { Geofences.goingHome(it.requestId) } ?: return
        val loc = event.triggeringLocation ?: return
        Log.i(TAG, "left ${if (goingHome) "work" else "home"} at ${loc.time / 1000}")
        TrackService.start(context, goingHome, Fix(loc.time / 1000, LatLng(loc.latitude, loc.longitude), loc.accuracy))
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Geofences.sync(context, SettingsStore(context).load())
    }
}

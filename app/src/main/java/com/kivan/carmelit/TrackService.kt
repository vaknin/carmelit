package com.kivan.carmelit

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * One recording session: GPS every [INTERVAL_MS] from leaving home or work until [Track.judge]
 * says finish or drop. The track is on disk after every fix, so a killed process resumes it.
 */
class TrackService : Service() {
    private lateinit var client: FusedLocationProviderClient
    private lateinit var store: TrackStore
    private var track: Track? = null
    private val handler = Handler(Looper.getMainLooper())

    // Underground there are no fixes at all, so deadlines are also checked on a timer.
    private val tick = object : Runnable {
        override fun run() {
            check()
            handler.postDelayed(this, TICK_MS)
        }
    }

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val t = track ?: return
            val fixes = result.locations.map { Fix(it.time / 1000, LatLng(it.latitude, it.longitude), it.accuracy) }
            fixes.forEach(store::append)
            track = t.copy(fixes = t.fixes + fixes)
            check()
        }
    }

    override fun onCreate() {
        super.onCreate()
        client = LocationServices.getFusedLocationProviderClient(this)
        store = TrackStore(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (track != null) return START_STICKY // a second exit while recording: keep the first
        val now = System.currentTimeMillis() / 1000
        val resumed = store.active()?.takeIf { now - it.leftAt < SESSION_MAX_SEC }
        val t = resumed ?: intent?.let(::fromIntent)
        // startForegroundService() obliges startForeground() even when there is nothing to record.
        try {
            startForeground(NOTIFICATION_ID, notification(t), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } catch (e: Exception) {
            // Android refuses a background start it does not consider exempt; nothing to record then.
            Log.w(TAG, "foreground start refused", e)
            stopSelf()
            return START_NOT_STICKY
        }
        if (t == null || !canRecord()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (resumed == null) store.begin(t)
        track = t
        TripSession.active.value = t
        requestUpdates()
        handler.post(tick)
        return START_STICKY
    }

    private fun fromIntent(intent: Intent): Track? {
        if (!intent.hasExtra(EXTRA_GOING_HOME)) return null
        val trigger = Fix(
            intent.getLongExtra(EXTRA_T, 0),
            LatLng(intent.getDoubleExtra(EXTRA_LAT, 0.0), intent.getDoubleExtra(EXTRA_LNG, 0.0)),
            intent.getFloatExtra(EXTRA_ACC, 0f),
        )
        return Track.start(SettingsStore(this).load(), intent.getBooleanExtra(EXTRA_GOING_HOME, false), trigger)
    }

    @SuppressLint("MissingPermission") // checked by canRecord() before starting
    private fun requestUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, INTERVAL_MS).build()
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
    }

    private fun check() {
        val t = track ?: return
        when (val v = t.judge(System.currentTimeMillis() / 1000)) {
            Verdict.CONTINUE -> {}
            else -> end(t, v)
        }
    }

    private fun end(t: Track, verdict: Verdict) {
        track = null
        client.removeLocationUpdates(callback)
        handler.removeCallbacks(tick)
        store.end(t, verdict)
        val gps = if (verdict == Verdict.FINISH) t.reconstruct() else null
        Log.i(TAG, "session ${t.leftAt} $verdict, ${t.fixes.size} fixes, run ${gps != null}")
        if (gps != null) {
            val runs = RunStore(this)
            val r = record(gps, runs.active(), runs.all(), SettingsStore(this).load())
            runs.saveAll(r.runs)
            runs.saveActive(r.active)
            TripSession.lastSaved.value = r.saved
            TripSession.runsChanged.value++
        }
        TripSession.active.value = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        // Killed mid-session: the file stays as active.txt and the sticky restart resumes it.
        client.removeLocationUpdates(callback)
        handler.removeCallbacks(tick)
        if (track != null) TripSession.active.value = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(t: Track?): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Trip recording", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Recording trip" + (t?.let { if (it.goingHome) " · To home" else " · To work" } ?: ""))
            .apply { if (t != null) setContentText("${t.from.english} → ${t.to.english}") }
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "Carmelit"
        private const val CHANNEL = "trip"
        private const val NOTIFICATION_ID = 1
        private const val INTERVAL_MS = 10_000L
        private const val TICK_MS = 30_000L
        private const val EXTRA_GOING_HOME = "goingHome"
        private const val EXTRA_T = "t"
        private const val EXTRA_LAT = "lat"
        private const val EXTRA_LNG = "lng"
        private const val EXTRA_ACC = "acc"

        fun start(context: Context, goingHome: Boolean, trigger: Fix) {
            val i = Intent(context, TrackService::class.java)
                .putExtra(EXTRA_GOING_HOME, goingHome)
                .putExtra(EXTRA_T, trigger.t)
                .putExtra(EXTRA_LAT, trigger.at.lat)
                .putExtra(EXTRA_LNG, trigger.at.lng)
                .putExtra(EXTRA_ACC, trigger.acc)
            context.startForegroundService(i)
        }
    }
}

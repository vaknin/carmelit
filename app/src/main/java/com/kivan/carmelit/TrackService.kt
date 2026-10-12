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
 * GPS for the open trip, every [INTERVAL_MS] while [Track.wantsGps]. All trip decisions are
 * [TripRecorder]'s; this only feeds it fixes and the clock, and stops itself when told. Never
 * restarted by Android after a kill (a background restart is refused anyway): the open trip
 * survives in its file and the arrival zone closes it.
 */
class TrackService : Service() {
    private lateinit var client: FusedLocationProviderClient
    private var recording = false
    private val handler = Handler(Looper.getMainLooper())

    // Underground there are no fixes at all, so the clock is also checked on a timer.
    private val tick = object : Runnable {
        override fun run() {
            if (!TripRecorder.onTick(this@TrackService)) stopGps()
            else handler.postDelayed(this, TICK_MS)
        }
    }

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val fixes = result.locations.map { Fix(it.time / 1000, LatLng(it.latitude, it.longitude), it.accuracy) }
            if (!TripRecorder.onFixes(this@TrackService, fixes)) stopGps()
        }
    }

    override fun onCreate() {
        super.onCreate()
        client = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForegroundService() obliges startForeground() even when there is nothing to record.
        try {
            startForeground(NOTIFICATION_ID, notification(null), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } catch (e: Exception) {
            // Android refuses a background start it does not consider exempt; nothing to record then.
            TripLog.get(this).add(LogKind.GPS_REFUSED, e.javaClass.simpleName)
            stopSelf()
            return START_NOT_STICKY
        }
        val t = TripRecorder.open(this)
        if (recording) {
            // A second start while recording: a new trip may have begun, so say so and keep going.
            if (t == null || !t.wantsGps(System.currentTimeMillis() / 1000)) { stopGps(); return START_NOT_STICKY }
            startForeground(NOTIFICATION_ID, notification(t), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            TripLog.get(this).add(LogKind.GPS_START, (if (t.goingHome) "to home" else "to work") + ", already running")
            return START_NOT_STICKY
        }
        if (t == null || !t.wantsGps(System.currentTimeMillis() / 1000) || !canRecord()) {
            stopGps()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, notification(t), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        recording = true
        requestUpdates()
        handler.postDelayed(tick, TICK_MS)
        TripLog.get(this).add(LogKind.GPS_START, if (t.goingHome) "to home" else "to work")
        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission") // checked by canRecord() before starting
    private fun requestUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, INTERVAL_MS).build()
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
    }

    private fun stopGps() {
        if (recording) {
            val why = if (TripSession.active.value == null) "trip over" else "no more GPS needed"
            TripLog.get(this).add(LogKind.GPS_STOP, why)
        }
        recording = false
        client.removeLocationUpdates(callback)
        handler.removeCallbacks(tick)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        // The trip is still open when the service dies: only the GPS goes.
        if (recording) TripLog.get(this).add(LogKind.GPS_STOP, if (TripSession.active.value == null) "trip over" else "service stopped")
        client.removeLocationUpdates(callback)
        handler.removeCallbacks(tick)
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
        private const val CHANNEL = "trip"
        private const val NOTIFICATION_ID = 1
        private const val INTERVAL_MS = 10_000L
        private const val TICK_MS = 30_000L

        /** Throws [IllegalStateException] when Android refuses a background start. */
        fun start(context: Context) {
            context.startForegroundService(Intent(context, TrackService::class.java))
        }
    }
}

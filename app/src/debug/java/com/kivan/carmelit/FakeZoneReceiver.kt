package com.kivan.carmelit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * A made-up zone report from adb: `--es door home|work --ez enter true|false`, optional
 * `--ed lat/lng`, `--ef acc`, `--el ago` (seconds since it "happened"). Without a position it
 * stands in the door, a fence's width out on the way out.
 */
class FakeZoneReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val door = if (intent.getStringExtra("door") == "work") Door.WORK else Door.HOME
        val entered = intent.getBooleanExtra("enter", false)
        val s = SettingsStore(context).load()
        val doorLL = (if (door == Door.HOME) s.homeLL else s.workLL) ?: return
        val now = System.currentTimeMillis() / 1000
        val ago = intent.getLongExtra("ago", 0L)
        val at = if (intent.hasExtra("lat")) LatLng(intent.getDoubleExtra("lat", 0.0), intent.getDoubleExtra("lng", 0.0))
        else LatLng(doorLL.lat + FENCE_RADIUS_M / 111_000.0, doorLL.lng)
        val fix = Fix(now - ago, at, intent.getFloatExtra("acc", 20f))
        TripLog.get(context).add(LogKind.FAKE, "${door.name.lowercase()} enter=$entered ago=${ago}s")
        TripLog.get(context).add(
            if (entered) LogKind.ZONE_ENTER else LogKind.ZONE_EXIT,
            "${door.name.lowercase()} acc=${fix.acc.toInt()} late=${ago}s fake",
        )
        TripRecorder.zoneReported(context, ZoneEvent(door, entered, fix))
    }
}

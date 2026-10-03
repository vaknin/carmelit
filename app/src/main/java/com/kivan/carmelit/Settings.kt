package com.kivan.carmelit

import android.content.Context
import androidx.core.content.edit

/**
 * User settings. Offsets are in minutes after the origin terminus departure and override the
 * default table for the *work* station only (home is a terminus for the default commute):
 *  - [toWorkOffsetMin]: morning trip, when the train reaches the work station (the arrival estimate).
 *  - [fromWorkOffsetMin]: evening trip, when the train reaches the work station (the boarding time).
 * One number cannot serve both, because the stop counts differ per direction.
 * [homeLL] / [workLL] are the front doors; with both set (and location access) trips record by GPS.
 */
data class Settings(
    val home: Station = Station.MERKAZ_HACARMEL,
    val work: Station = Station.HANEVIIM,
    val walkHomeMin: Int = 22,
    val walkWorkMin: Int = 5,
    val marginMin: Int = 0,
    val toWorkOffsetMin: Double? = null,
    val fromWorkOffsetMin: Double? = null,
    val homeLL: LatLng? = null,
    val workLL: LatLng? = null,
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(): Settings {
        val d = Settings()
        fun station(key: String, default: Station) =
            prefs.getString(key, null)?.let { runCatching { Station.valueOf(it) }.getOrNull() } ?: default
        fun optional(key: String): Double? =
            if (prefs.contains(key)) prefs.getFloat(key, 0f).toDouble() else null
        return Settings(
            home = station("home", d.home),
            work = station("work", d.work),
            walkHomeMin = prefs.getInt("walkHome", d.walkHomeMin),
            walkWorkMin = prefs.getInt("walkWork", d.walkWorkMin),
            marginMin = prefs.getInt("margin", d.marginMin),
            toWorkOffsetMin = optional("toWorkOffset"),
            fromWorkOffsetMin = optional("fromWorkOffset"),
            homeLL = LatLng.decode(prefs.getString("homeLL", null)),
            workLL = LatLng.decode(prefs.getString("workLL", null)),
        )
    }

    fun save(s: Settings) = prefs.edit {
        putString("home", s.home.name)
        putString("work", s.work.name)
        putInt("walkHome", s.walkHomeMin)
        putInt("walkWork", s.walkWorkMin)
        putInt("margin", s.marginMin)
        if (s.toWorkOffsetMin != null) putFloat("toWorkOffset", s.toWorkOffsetMin.toFloat()) else remove("toWorkOffset")
        if (s.fromWorkOffsetMin != null) putFloat("fromWorkOffset", s.fromWorkOffsetMin.toFloat()) else remove("fromWorkOffset")
        putString("homeLL", s.homeLL?.encode())
        putString("workLL", s.workLL?.encode())
        remove("autoRecord") // the old on/off switch
    }
}

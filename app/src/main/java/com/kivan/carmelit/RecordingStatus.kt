package com.kivan.carmelit

/** What stops (or may stop) trips being recorded, most blocking first. */
enum class Problem { NO_DOORS, NO_LOCATION, NO_BACKGROUND, LOCATION_OFF, ZONES_FAILED, NOTIFICATIONS_OFF, MAY_BE_PAUSED }

/**
 * Everything the Settings line is told from. The texts are made by the caller: [zonesSince] is
 * "12 Oct" and [lastTrip] "to work 31 min, today 08:40".
 */
data class StatusInputs(
    val doorsSet: Boolean,
    val fine: Boolean,
    val background: Boolean,
    val notifications: Boolean,
    val locationOn: Boolean,
    val zonesOk: Boolean,
    val zonesSince: String?,
    val zoneError: String?,
    val autoRevokeExempt: Boolean,
    val lastTrip: String?,
)

data class RecordingStatus(val line: String, val problem: Problem?)

/** The one plain line for Settings, and the first [Problem] to fix. Never says "watching" while a blocking problem stands. */
fun recordingStatus(i: StatusInputs): RecordingStatus {
    val watching = "Watching home and work" + (i.zonesSince?.let { " since $it" } ?: "") +
        (i.lastTrip?.let { " · last trip: $it" } ?: "")
    return when {
        !i.doorsSet -> RecordingStatus("Set both doors, standing at each", Problem.NO_DOORS)
        !i.fine -> RecordingStatus("Needs precise location", Problem.NO_LOCATION)
        !i.background -> RecordingStatus("Needs location access “Allow all the time”", Problem.NO_BACKGROUND)
        !i.locationOn -> RecordingStatus("Location is switched off, so trips can't be seen", Problem.LOCATION_OFF)
        !i.zonesOk -> RecordingStatus(
            "Android isn't watching home and work" + (i.zoneError?.let { " ($it)" } ?: ""), Problem.ZONES_FAILED,
        )
        !i.notifications -> RecordingStatus("$watching · notifications are off", Problem.NOTIFICATIONS_OFF)
        !i.autoRevokeExempt -> RecordingStatus("$watching · Android may pause the app if it isn't opened for months", Problem.MAY_BE_PAUSED)
        else -> RecordingStatus(watching, null)
    }
}

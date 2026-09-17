package com.kivan.carmelit

/** Which published timetable a service day runs. */
enum class DayType { WEEKDAY, FRIDAY, MOTZEI, EREV_PESACH, CLOSED }

/** A service window: departures from [start] every [headwayMin] up to and including [end], all in minutes of the day. */
private data class Window(val start: Int, val end: Int, val headwayMin: Int)

private const val MIDNIGHT = 24 * 60

private val EARLY = Window(6 * 60, 7 * 60, 15)
private fun core(untilHour: Int) = Window(7 * 60, untilHour * 60, 12)
private val LATE = Window(22 * 60, MIDNIGHT, 15)

private fun windows(type: DayType, isDst: Boolean): List<Window> = when (type) {
    DayType.WEEKDAY -> listOf(EARLY, core(22), LATE)
    DayType.FRIDAY -> listOf(EARLY, core(15))
    DayType.EREV_PESACH -> listOf(EARLY, core(14))
    DayType.MOTZEI -> listOf(Window((if (isDst) 20 else 19) * 60, MIDNIGHT, 15))
    DayType.CLOSED -> emptyList()
}

/**
 * Terminus departures for a day of the given type, as minutes after the service day's 00:00.
 * Both termini depart at the same minute. The closing-time train (15:00, 24:00, ...) is included:
 * the site does not mention it, but the Ministry of Transport GTFS feed (2026-09-16) lists it.
 * The 24:00 train is 1440, i.e. 00:00 of the next calendar day.
 */
fun departures(type: DayType, isDst: Boolean): List<Int> =
    windows(type, isDst).flatMap { w -> w.start..w.end step w.headwayMin }.distinct()

/** Short human description of the day's timetable for the footer line. */
fun timetableLabel(type: DayType, isDst: Boolean): String = when (type) {
    DayType.WEEKDAY -> "weekday timetable · every 12 min 07:00–22:00"
    DayType.FRIDAY -> "Friday hours · every 12 min until 15:00"
    DayType.EREV_PESACH -> "erev Pesach · every 12 min until 14:00"
    DayType.MOTZEI -> "motzei Shabbat/chag · every 15 min ${if (isDst) "20:00" else "19:00"}–24:00"
    DayType.CLOSED -> "no service"
}

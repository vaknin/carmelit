package com.kivan.carmelit

import java.time.LocalTime

/** Which published timetable a service day runs. */
enum class DayType { WEEKDAY, FRIDAY, MOTZEI, YOM_KIPPUR, EREV_YK, EREV_PESACH, CLOSED }

/** A service window: departures from [start] every [headwayMin] up to but excluding [endExclusive] (null = midnight). */
private data class Window(val start: LocalTime, val endExclusive: LocalTime?, val headwayMin: Int)

private val EARLY = Window(LocalTime.of(6, 0), LocalTime.of(7, 0), 15)
private fun core(until: LocalTime) = Window(LocalTime.of(7, 0), until, 12)
private val LATE = Window(LocalTime.of(22, 0), null, 15)

private fun windows(type: DayType, isDst: Boolean): List<Window> = when (type) {
    DayType.WEEKDAY -> listOf(EARLY, core(LocalTime.of(22, 0)), LATE)
    DayType.FRIDAY -> listOf(EARLY, core(LocalTime.of(15, 0)))
    DayType.EREV_YK -> listOf(EARLY, core(LocalTime.of(13, 0)))
    DayType.EREV_PESACH -> listOf(EARLY, core(LocalTime.of(14, 0)))
    DayType.MOTZEI -> listOf(Window(LocalTime.of(if (isDst) 20 else 19, 0), null, 15))
    DayType.YOM_KIPPUR -> listOf(Window(LocalTime.of(21, 0), null, 15))
    DayType.CLOSED -> emptyList()
}

/**
 * Terminus departure times for a day of the given type. Both termini depart at the same minute.
 * Each window is half-open, so the closing-time train (15:00, 24:00, ...) is not included: the
 * site does not say whether it runs, so the one before it is the last guaranteed departure.
 */
fun departures(type: DayType, isDst: Boolean): List<LocalTime> = windows(type, isDst).flatMap { w ->
    generateSequence(w.start) { it.plusMinutes(w.headwayMin.toLong()) }
        // `it >= w.start` stops the sequence when plusMinutes wraps past midnight.
        .takeWhile { it >= w.start && (w.endExclusive == null || it < w.endExclusive) }
        .toList()
}

/** Short human description of the day's timetable for the footer line. */
fun timetableLabel(type: DayType, isDst: Boolean): String = when (type) {
    DayType.WEEKDAY -> "weekday timetable · every 12 min 07:00–22:00"
    DayType.FRIDAY -> "Friday hours · every 12 min until 15:00"
    DayType.EREV_YK -> "erev Yom Kippur · every 12 min until 13:00"
    DayType.EREV_PESACH -> "erev Pesach · every 12 min until 14:00"
    DayType.MOTZEI -> "motzei Shabbat/chag · every 15 min ${if (isDst) "20:00" else "19:00"}–24:00"
    DayType.YOM_KIPPUR -> "Yom Kippur · every 15 min 21:00–24:00"
    DayType.CLOSED -> "no service"
}

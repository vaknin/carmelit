package com.kivan.carmelit

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

/** All times in the app are Israel wall-clock time, never the device zone. */
val ZONE: ZoneId = ZoneId.of("Asia/Jerusalem")

/** Israeli DST in effect on [date], evaluated at noon (transitions happen at 02:00). */
fun isDst(date: LocalDate): Boolean =
    ZONE.rules.isDaylightSavings(date.atTime(12, 0).atZone(ZONE).toInstant())

/** Shabbat or a yom tov on which the Carmelit has no daytime service. */
private fun isRestDay(date: LocalDate): Boolean {
    val kind = HebrewCalendar.holidayOn(date)?.kind
    return date.dayOfWeek == DayOfWeek.SATURDAY || kind == HolidayKind.CHAG || kind == HolidayKind.YK
}

/**
 * Which timetable runs on [date] (IDEA.md "Day classification rules"):
 *  - Yom Kippur: 21:00–24:00 (also when it is a Shabbat).
 *  - A rest day followed by another rest day: closed; otherwise evening (motzei) service only.
 *  - Erev Yom Kippur / erev Pesach: their own shorter windows, even on a Friday.
 *  - The day before a rest day (a plain Friday, or erev chag on any weekday): Friday hours.
 *  - Everything else (including chol hamoed, Purim, Independence Day): weekday.
 */
fun classifyDay(date: LocalDate): DayType {
    val kind = HebrewCalendar.holidayOn(date)?.kind
    if (kind == HolidayKind.YK) return DayType.YOM_KIPPUR
    val nextIsRest = isRestDay(date.plusDays(1))
    if (isRestDay(date)) return if (nextIsRest) DayType.CLOSED else DayType.MOTZEI
    if (kind == HolidayKind.EREV_YK) return DayType.EREV_YK
    if (kind == HolidayKind.EREV_PESACH) return DayType.EREV_PESACH
    if (nextIsRest) return DayType.FRIDAY
    return DayType.WEEKDAY
}

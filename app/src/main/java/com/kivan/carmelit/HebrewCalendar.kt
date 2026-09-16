package com.kivan.carmelit

import java.time.LocalDate

/** Which timetable a holiday date selects (or, for the `EREV` entries, a label only). */
enum class HolidayKind { CHAG, EREV, EREV_YK, YK, EREV_PESACH }

data class Holiday(val kind: HolidayKind, val name: String)

/**
 * Hebrew calendar (Reingold & Dershowitz, "Calendrical Calculations"), just enough to place the
 * Jewish holidays on which the Carmelit changes its timetable. Port of hebcal.js; the golden test
 * asserts it produces the same table.
 */
object HebrewCalendar {
    private const val HEBREW_EPOCH = -1373427L   // RD of 1 Tishri AM 1
    private const val RD_UNIX_EPOCH = 719163L    // RD of 1970-01-01

    private fun elapsedDays(y: Long): Long {
        val monthsElapsed = Math.floorDiv(235 * y - 234, 19L)
        val partsElapsed = 12084 + 13753 * monthsElapsed
        var day = 29 * monthsElapsed + Math.floorDiv(partsElapsed, 25920L)
        if (Math.floorMod(3 * (day + 1), 7L) < 3) day += 1
        return day
    }

    private fun yearCorrection(y: Long): Long {
        val ny0 = elapsedDays(y - 1)
        val ny1 = elapsedDays(y)
        val ny2 = elapsedDays(y + 1)
        return when {
            ny2 - ny1 == 356L -> 2
            ny1 - ny0 == 382L -> 1
            else -> 0
        }
    }

    /** Rata Die (fixed day number) of 1 Tishri of Hebrew year [y]. */
    fun newYearRD(y: Int): Long = HEBREW_EPOCH + elapsedDays(y.toLong()) + yearCorrection(y.toLong())

    fun rdToDate(rd: Long): LocalDate = LocalDate.ofEpochDay(rd - RD_UNIX_EPOCH)

    /** Holidays of Hebrew year [y] that affect the timetable, keyed by Gregorian date. */
    fun holidaysForHebrewYear(y: Int): Map<LocalDate, Holiday> {
        val rh = newYearRD(y)
        val pesach = newYearRD(y + 1) - 163   // 15 Nisan
        val out = LinkedHashMap<LocalDate, Holiday>()
        fun put(rd: Long, kind: HolidayKind, name: String) { out[rdToDate(rd)] = Holiday(kind, name) }
        put(rh - 1, HolidayKind.EREV, "Erev Rosh Hashana")
        put(rh, HolidayKind.CHAG, "Rosh Hashana I")
        put(rh + 1, HolidayKind.CHAG, "Rosh Hashana II")
        put(rh + 8, HolidayKind.EREV_YK, "Erev Yom Kippur")
        put(rh + 9, HolidayKind.YK, "Yom Kippur")
        put(rh + 13, HolidayKind.EREV, "Erev Sukkot")
        put(rh + 14, HolidayKind.CHAG, "Sukkot")
        put(rh + 20, HolidayKind.EREV, "Hoshana Raba")
        put(rh + 21, HolidayKind.CHAG, "Shmini Atzeret / Simchat Torah")
        put(pesach - 1, HolidayKind.EREV_PESACH, "Erev Pesach")
        put(pesach, HolidayKind.CHAG, "Pesach I")
        put(pesach + 5, HolidayKind.EREV, "Erev Shvi'i shel Pesach")
        put(pesach + 6, HolidayKind.CHAG, "Shvi'i shel Pesach")
        put(pesach + 49, HolidayKind.EREV, "Erev Shavuot")
        put(pesach + 50, HolidayKind.CHAG, "Shavuot")
        return out
    }

    fun holidayTable(fromHY: Int, toHY: Int): Map<LocalDate, Holiday> {
        val t = LinkedHashMap<LocalDate, Holiday>()
        for (y in fromHY..toHY) t.putAll(holidaysForHebrewYear(y))
        return t
    }

    private val cache = HashMap<Int, Map<LocalDate, Holiday>>()

    private fun year(y: Int): Map<LocalDate, Holiday> =
        synchronized(cache) { cache.getOrPut(y) { holidaysForHebrewYear(y) } }

    /**
     * The holiday on [date], if any. Gregorian year G holds the tail of Hebrew year G+3760
     * (Jan–Sep) and the head of G+3761 (Sep–Dec), so both are consulted.
     */
    fun holidayOn(date: LocalDate): Holiday? =
        year(date.year + 3760)[date] ?: year(date.year + 3761)[date]
}

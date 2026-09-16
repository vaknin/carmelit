package com.kivan.carmelit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class HebrewCalendarTest {
    private fun d(s: String) = LocalDate.parse(s)

    @Test fun newYear5787() = assertEquals(d("2026-09-12"), HebrewCalendar.rdToDate(HebrewCalendar.newYearRD(5787)))

    @Test fun knownDates() {
        assertEquals(Holiday(HolidayKind.CHAG, "Rosh Hashana I"), HebrewCalendar.holidayOn(d("2026-09-12")))
        assertEquals(Holiday(HolidayKind.YK, "Yom Kippur"), HebrewCalendar.holidayOn(d("2026-09-21")))
        assertEquals(Holiday(HolidayKind.CHAG, "Pesach I"), HebrewCalendar.holidayOn(d("2027-04-22")))
        assertEquals(Holiday(HolidayKind.CHAG, "Shavuot"), HebrewCalendar.holidayOn(d("2027-06-11")))
        assertEquals(Holiday(HolidayKind.CHAG, "Rosh Hashana I"), HebrewCalendar.holidayOn(d("2027-10-02")))
        assertNull(HebrewCalendar.holidayOn(d("2026-09-16")))
    }

    @Test fun matchesGoldenTableFromHebcalJs() {
        val golden = javaClass.getResourceAsStream("/hebcal_golden.tsv")!!.bufferedReader().readLines()
            .filter { it.isNotBlank() }
            .associate { line ->
                val (date, kind, name) = line.split('\t')
                val k = when (kind) {
                    "chag" -> HolidayKind.CHAG; "erev" -> HolidayKind.EREV; "erevYK" -> HolidayKind.EREV_YK
                    "YK" -> HolidayKind.YK; "erevPesach" -> HolidayKind.EREV_PESACH
                    else -> error("unknown kind $kind")
                }
                d(date) to Holiday(k, name)
            }
        assertEquals(615, golden.size)
        assertEquals(golden, HebrewCalendar.holidayTable(5760, 5800))
        for ((date, h) in golden) assertEquals(date.toString(), h, HebrewCalendar.holidayOn(date))
    }
}

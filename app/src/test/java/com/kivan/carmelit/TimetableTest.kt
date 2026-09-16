package com.kivan.carmelit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class TimetableTest {
    private fun t(s: String) = LocalTime.parse(s)

    @Test fun weekday() {
        val d = departures(DayType.WEEKDAY, true)
        assertEquals(87, d.size)
        assertEquals(t("06:00"), d.first())
        assertEquals(t("23:45"), d.last())
        for (x in listOf("06:45", "07:00", "07:12", "21:48", "22:00", "22:15")) assertTrue(x, t(x) in d)
        for (x in listOf("07:15", "00:00", "21:45")) assertFalse(x, t(x) in d)
        assertEquals(d, d.sorted())
        assertEquals(d.size, d.toSet().size)
        assertEquals(d, departures(DayType.WEEKDAY, false))
    }

    @Test fun friday() {
        val d = departures(DayType.FRIDAY, false)
        assertEquals(44, d.size)
        assertEquals(t("14:48"), d.last())
        assertFalse(t("15:00") in d)
    }

    @Test fun erevYomKippur() {
        val d = departures(DayType.EREV_YK, false)
        assertEquals(34, d.size)
        assertEquals(t("12:48"), d.last())
    }

    @Test fun erevPesach() {
        val d = departures(DayType.EREV_PESACH, true)
        assertEquals(39, d.size)
        assertEquals(t("13:48"), d.last())
    }

    @Test fun motzei() {
        val summer = departures(DayType.MOTZEI, true)
        assertEquals(16, summer.size)
        assertEquals(t("20:00"), summer.first())
        assertEquals(t("23:45"), summer.last())
        val winter = departures(DayType.MOTZEI, false)
        assertEquals(20, winter.size)
        assertEquals(t("19:00"), winter.first())
    }

    @Test fun yomKippur() {
        val d = departures(DayType.YOM_KIPPUR, false)
        assertEquals(12, d.size)
        assertEquals(t("21:00"), d.first())
    }

    @Test fun closed() = assertTrue(departures(DayType.CLOSED, true).isEmpty())
}

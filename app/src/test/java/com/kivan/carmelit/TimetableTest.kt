package com.kivan.carmelit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class TimetableTest {
    private fun t(s: String) = if (s == "24:00") 1440 else LocalTime.parse(s).toSecondOfDay() / 60

    @Test fun weekday() {
        val d = departures(DayType.WEEKDAY, true)
        assertEquals(88, d.size)
        assertEquals(t("06:00"), d.first())
        assertEquals(t("24:00"), d.last())
        for (x in listOf("06:45", "07:00", "07:12", "21:48", "22:00", "22:15")) assertTrue(x, t(x) in d)
        for (x in listOf("07:15", "00:00", "21:45", "22:12")) assertFalse(x, t(x) in d)
        assertEquals(d, d.sorted())
        assertEquals(d.size, d.toSet().size)
        assertEquals(d, departures(DayType.WEEKDAY, false))
    }

    @Test fun friday() {
        val d = departures(DayType.FRIDAY, false)
        assertEquals(45, d.size)
        assertEquals(t("15:00"), d.last())
        assertFalse(t("15:12") in d)
    }

    @Test fun erevPesach() {
        val d = departures(DayType.EREV_PESACH, true)
        assertEquals(40, d.size)
        assertEquals(t("14:00"), d.last())
    }

    @Test fun motzei() {
        val summer = departures(DayType.MOTZEI, true)
        assertEquals(17, summer.size)
        assertEquals(t("20:00"), summer.first())
        assertEquals(t("24:00"), summer.last())
        val winter = departures(DayType.MOTZEI, false)
        assertEquals(21, winter.size)
        assertEquals(t("19:00"), winter.first())
    }

    @Test fun closed() = assertTrue(departures(DayType.CLOSED, true).isEmpty())
}

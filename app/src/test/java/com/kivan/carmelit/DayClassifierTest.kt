package com.kivan.carmelit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class DayClassifierTest {
    private fun check(vararg cases: Pair<String, DayType>) {
        for ((date, expected) in cases) assertEquals(date, expected, classifyDay(LocalDate.parse(date)))
    }

    @Test fun plainWeek() = check(
        "2026-09-16" to DayType.WEEKDAY, "2026-09-18" to DayType.FRIDAY, "2026-09-19" to DayType.MOTZEI,
        "2026-09-27" to DayType.WEEKDAY, // Sunday of chol hamoed Sukkot
    )

    @Test fun roshHashana2026() = check(
        "2026-09-10" to DayType.WEEKDAY, "2026-09-11" to DayType.FRIDAY, "2026-09-12" to DayType.CLOSED,
        "2026-09-13" to DayType.MOTZEI, "2026-09-14" to DayType.WEEKDAY,
    )

    @Test fun yomKippur() = check(
        "2026-09-19" to DayType.MOTZEI, "2026-09-20" to DayType.EREV_YK, "2026-09-21" to DayType.YOM_KIPPUR,
        "2026-09-22" to DayType.WEEKDAY,
        "2025-10-02" to DayType.YOM_KIPPUR, "2025-10-03" to DayType.FRIDAY,          // YK on Thursday
        "2028-09-29" to DayType.EREV_YK, "2028-09-30" to DayType.YOM_KIPPUR, "2028-10-01" to DayType.WEEKDAY, // YK on Shabbat
    )

    @Test fun sukkot() = check(
        "2026-09-25" to DayType.FRIDAY, "2026-09-26" to DayType.MOTZEI,
        "2026-10-02" to DayType.FRIDAY, "2026-10-03" to DayType.MOTZEI,
        "2028-10-04" to DayType.FRIDAY, "2028-10-05" to DayType.MOTZEI, "2028-10-06" to DayType.FRIDAY, // chag on Thursday
    )

    @Test fun pesach() = check(
        "2027-04-21" to DayType.EREV_PESACH, "2027-04-22" to DayType.MOTZEI, "2027-04-23" to DayType.FRIDAY,
        "2027-04-27" to DayType.FRIDAY, "2027-04-28" to DayType.MOTZEI,
        "2029-03-30" to DayType.EREV_PESACH, "2029-03-31" to DayType.MOTZEI, // erev Pesach on Friday
        "2029-04-06" to DayType.CLOSED, // 7th day of Pesach on Friday
    )

    @Test fun shavuot() = check(
        "2027-06-10" to DayType.FRIDAY, "2027-06-11" to DayType.CLOSED, "2027-06-12" to DayType.MOTZEI,
        "2026-05-22" to DayType.CLOSED,
        "2029-05-19" to DayType.CLOSED, "2029-05-20" to DayType.MOTZEI, // Shabbat before a Sunday chag
    )

    @Test fun twoDayClosure2028() = check(
        "2028-09-20" to DayType.FRIDAY, "2028-09-21" to DayType.CLOSED, "2028-09-22" to DayType.CLOSED,
        "2028-09-23" to DayType.MOTZEI,
    )

    @Test fun dst() {
        assertTrue(isDst(LocalDate.parse("2026-10-24")))
        assertTrue(isDst(LocalDate.parse("2026-03-28")))
        assertFalse(isDst(LocalDate.parse("2026-10-31")))
        assertFalse(isDst(LocalDate.parse("2026-03-21")))
        assertEquals(LocalTime.of(20, 0), departures(DayType.MOTZEI, isDst(LocalDate.parse("2026-10-24"))).first())
        assertEquals(LocalTime.of(19, 0), departures(DayType.MOTZEI, isDst(LocalDate.parse("2026-10-31"))).first())
    }
}

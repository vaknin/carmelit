package com.kivan.carmelit

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZonedDateTime

class PlannerTest {
    private fun at(s: String): ZonedDateTime = LocalDateTime.parse(s).atZone(ZONE)

    @Test fun weekdayMorning() {
        val p = nextTrips(at("2026-09-16T07:48"), Settings(), goingHome = false)
        assertEquals(4, p.trips.size)
        val t = p.trips[0]
        assertEquals(at("2026-09-16T08:12"), t.departure)
        assertEquals(at("2026-09-16T08:12"), t.boardAt)
        assertEquals(at("2026-09-16T07:50"), t.leaveAt)
        assertEquals(at("2026-09-16T08:17"), t.arriveAt)
        assertEquals(DayType.WEEKDAY, t.dayType)
        assertEquals(at("2026-09-16T08:24"), p.trips[1].departure)
        assertEquals(at("2026-09-16T08:02"), p.trips[1].leaveAt)
        assertEquals(Station.MERKAZ_HACARMEL, p.from)
        assertEquals(Station.HANEVIIM, p.to)
    }

    @Test fun leaveNowStillCounts() {
        val p = nextTrips(at("2026-09-16T07:50"), Settings(), goingHome = false)
        assertEquals(at("2026-09-16T08:12"), p.trips[0].departure)
    }

    @Test fun lateNightRollsToTomorrow() {
        val p = nextTrips(at("2026-09-16T23:30"), Settings(), goingHome = false)
        assertEquals(at("2026-09-17T06:00"), p.trips[0].departure)
        assertEquals(at("2026-09-17T05:38"), p.trips[0].leaveAt)
    }

    @Test fun fridayAfternoonRollsToMotzeiShabbat() {
        val p = nextTrips(at("2026-09-18T15:00"), Settings(), goingHome = false)
        assertEquals(at("2026-09-19T20:00"), p.trips[0].departure)
        assertEquals(at("2026-09-19T19:38"), p.trips[0].leaveAt)
        assertEquals(DayType.MOTZEI, p.dayType)
        assertEquals(true, p.isDst)
    }

    @Test fun winterMotzei() {
        val p = nextTrips(at("2026-11-06T15:00"), Settings(), goingHome = false)
        assertEquals(at("2026-11-07T19:00"), p.trips[0].departure)
        assertEquals(false, p.isDst)
    }

    @Test fun closedShabbatSkipsToSunday() {
        val p = nextTrips(at("2026-09-11T15:00"), Settings(), goingHome = false)
        assertEquals(at("2026-09-13T20:00"), p.trips[0].departure)
        assertEquals(java.time.LocalDate.parse("2026-09-13"), p.serviceDay)
    }

    @Test fun margin() {
        val p = nextTrips(at("2026-09-16T07:40"), Settings(marginMin = 5), goingHome = false)
        assertEquals(at("2026-09-16T08:12"), p.trips[0].departure)
        assertEquals(at("2026-09-16T07:45"), p.trips[0].leaveAt)
    }

    @Test fun goingHome() {
        val p = nextTrips(at("2026-09-16T16:50"), Settings(), goingHome = true)
        val t = p.trips[0]
        assertEquals(Station.HANEVIIM, p.from)
        assertEquals(at("2026-09-16T17:00"), t.departure)
        assertEquals(at("2026-09-16T17:03"), t.boardAt)
        assertEquals(at("2026-09-16T16:58"), t.leaveAt)
        assertEquals(at("2026-09-16T17:08"), t.arriveAt)
    }

    @Test fun offsetOverrides() {
        val morning = nextTrips(at("2026-09-16T07:48"), Settings(toWorkOffsetMin = 6.0), goingHome = false)
        assertEquals(at("2026-09-16T08:18"), morning.trips[0].arriveAt)
        val evening = nextTrips(at("2026-09-16T16:50"), Settings(fromWorkOffsetMin = 2.5), goingHome = true)
        assertEquals(at("2026-09-16T17:02:30"), evening.trips[0].boardAt)
        assertEquals(at("2026-09-16T16:57:30"), evening.trips[0].leaveAt)
    }
}

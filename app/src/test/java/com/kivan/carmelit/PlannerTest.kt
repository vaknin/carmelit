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
        assertEquals(at("2026-09-16T08:18:30"), t.arriveAt)
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
        assertEquals(DayType.WEEKDAY, p.todayType)
        assertEquals(at("2026-09-16T23:45"), p.missedLast?.departure)
        assertEquals(at("2026-09-16T23:23"), p.missedLast?.leaveAt)
    }

    @Test fun missedLastIsNullWhileTodayStillHasTrains() {
        val p = nextTrips(at("2026-09-16T07:48"), Settings(), goingHome = false)
        assertEquals(null, p.missedLast)
        assertEquals(DayType.WEEKDAY, p.todayType)
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
        // Friday 15:00: the 14:48 Friday train has gone; Shabbat + Rosh Hashana day II are closed.
        assertEquals(DayType.FRIDAY, p.todayType)
        assertEquals(at("2026-09-11T14:48"), p.missedLast?.departure)
    }

    @Test fun closedTodayHasNoMissedTrain() {
        val p = nextTrips(at("2026-09-12T10:00"), Settings(), goingHome = false)
        assertEquals(DayType.CLOSED, p.todayType)
        assertEquals(null, p.missedLast)
        assertEquals(at("2026-09-13T20:00"), p.trips[0].departure)
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
        // 2 stops up from Ir Tahtit = 5 min (measured), so boarding is not the published minute.
        assertEquals(at("2026-09-16T17:05"), t.boardAt)
        assertEquals(at("2026-09-16T17:00"), t.leaveAt)
        assertEquals(at("2026-09-16T17:09:30"), t.arriveAt)
    }

    /**
     * The 2026-09-17 field report: standing at HaNevi'im at 08:51 going home, the app offered a
     * leave time only 2 min before the "train" it named, because it named the Ir Tahtit departure
     * instead of the boarding time. The walk must sit between leaveAt and boardAt, not departure.
     */
    @Test fun goingHomeLeaveTimeIsOneWalkBeforeBoarding() {
        val s = Settings(walkWorkMin = 5)
        val t = nextTrips(at("2026-09-17T08:51"), s, goingHome = true).trips[0]
        assertEquals(at("2026-09-17T09:00"), t.departure)
        assertEquals(at("2026-09-17T09:05"), t.boardAt)
        assertEquals(at("2026-09-17T09:00"), t.leaveAt)
        assertEquals(5L, java.time.Duration.between(t.leaveAt, t.boardAt).toMinutes())
    }

    @Test fun offsetOverrides() {
        val morning = nextTrips(at("2026-09-16T07:48"), Settings(toWorkOffsetMin = 6.0), goingHome = false)
        assertEquals(at("2026-09-16T08:18"), morning.trips[0].arriveAt)
        val evening = nextTrips(at("2026-09-16T16:50"), Settings(fromWorkOffsetMin = 2.5), goingHome = true)
        assertEquals(at("2026-09-16T17:02:30"), evening.trips[0].boardAt)
        assertEquals(at("2026-09-16T16:57:30"), evening.trips[0].leaveAt)
    }
}

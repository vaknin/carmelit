package com.kivan.carmelit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import kotlin.math.roundToLong

class GeoTest {
    private fun at(s: String) = LocalDateTime.parse("2026-09-16T$s").atZone(ZONE).toEpochSecond()

    // ~1.3 km south-west of Merkaz HaCarmel, and ~150 m from HaNevi'im.
    private val home = LatLng(32.7950, 34.9800)
    private val work = LatLng(32.8135, 34.9965)
    private val settings = Settings(homeLL = home, workLL = work)
    private val mc = STATION_POINTS.getValue(Station.MERKAZ_HACARMEL)
    private val hn = STATION_POINTS.getValue(Station.HANEVIIM)

    private fun lerp(a: LatLng, b: LatLng, f: Double) = LatLng(a.lat + (b.lat - a.lat) * f, a.lng + (b.lng - a.lng) * f)

    /** Fixes every 30 s walking from [a] to [b] between the two clock times, [b] included. */
    private fun walk(a: LatLng, b: LatLng, from: String, to: String, acc: Float = 8f): List<Fix> {
        val t0 = at(from); val t1 = at(to)
        return (t0..t1 step 30).map { t -> Fix(t, lerp(a, b, (t - t0).toDouble() / (t1 - t0)), acc) }
    }

    /** The geofence fires 150 m out of the home fence's centre at 07:58. */
    private fun startToWork(): Track {
        val trigger = Fix(at("07:58:00"), lerp(home, mc, 150 / distanceM(home, mc)), 10f)
        return Track.start(settings, goingHome = false, trigger)!!
    }

    /** Home 07:56 → entrance 08:20, underground, surface at HaNevi'im 08:31:30, work fence 08:33:30. */
    private fun commute(): Track {
        val t = startToWork()
        val fixes = walk(lerp(home, mc, 150 / distanceM(home, mc)), mc, "07:58:00", "08:20:00") +
            // Underground: the phone keeps guessing from cell towers.
            listOf(Fix(at("08:25:00"), mc, 900f), Fix(at("08:29:00"), hn, 700f)) +
            walk(hn, work, "08:31:30", "08:33:30")
        return t.copy(fixes = fixes)
    }

    @Test fun startMovesTheLeaveBackToTheDoor() {
        val t = startToWork()
        assertEquals(Station.MERKAZ_HACARMEL, t.from)
        assertEquals(Station.HANEVIIM, t.to)
        assertEquals(at("07:58:00") - 115, t.leftAt) // 150 m at 1.3 m/s
        assertEquals(t.leftAt + 22 * 60 + STATION_GRACE_SEC, t.deadline)
        assertNull(Track.start(Settings(homeLL = home), goingHome = false, Fix(0, home, 5f)))
    }

    @Test fun fullCommuteFinishesAndReconstructs() {
        val t = commute()
        assertEquals(Verdict.FINISH, t.judge(at("08:33:30")))
        val r = t.close(null)!!
        assertTrue(r.rodeCarmelit)
        assertEquals(Source.GPS, r.source)
        assertEquals(t.leftAt, r[Step.LEAVE])
        assertEquals(at("08:20:00"), r.entranceAt)
        assertEquals(at("08:31:30"), r.surfaceAt)
        // First fix inside the work fence, plus its distance to the door at walking pace.
        val door = r[Step.DOOR]!!
        assertTrue(door in at("08:32:30")..at("08:34:00"))
        // Surfaced 08:31:30 − 1 min climb = 08:30:30 = the 08:24 from Merkaz HaCarmel + 6.5 min.
        val f = r.finished(settings)
        assertEquals(at("08:24:00"), f.departure)
        assertTrue(f.doorToDoor!! in 35 * 60L..38 * 60L)
    }

    @Test fun undergroundKeepsRecording() {
        val t = commute().let { c -> c.copy(fixes = c.fixes.filter { it.t <= at("08:29:00") }) }
        // Long past the station deadline, but the station was reached: keep recording a while.
        assertEquals(Verdict.CONTINUE, t.judge(at("08:30:00")))
        assertTrue(t.wantsGps(at("08:30:00")))
        // No good fix for 25 min underground: GPS stops, the trip waits for the arrival zone.
        assertTrue(t.wantsGps(at("08:20:00") + UNDERGROUND_MAX_SEC))
        assertFalse(t.wantsGps(at("08:20:00") + UNDERGROUND_MAX_SEC + 1))
        assertEquals(Verdict.CONTINUE, t.judge(at("08:50:00")))
        // The cell-tower guess at HaNevi'im is not a surfacing.
        val r = t.close(arrivalAtWork(at("08:33:30")))!!
        assertNull(r.surfaceAt)
        assertEquals(at("08:20:00"), r.entranceAt)
    }

    @Test fun walkThatNeverReachesTheStationStopsGpsAtTheDeadlineAndDropsAfterThreeHours() {
        val shop = LatLng(32.7990, 34.9750)
        // Starts where the fence fired, 150 m out.
        val t = startToWork().copy(fixes = walk(lerp(home, shop, 150 / distanceM(home, shop)), shop, "07:58:00", "08:10:00"))
        assertEquals(Verdict.CONTINUE, t.judge(at("08:10:00")))
        assertTrue(t.wantsGps(at("08:10:00")))
        assertFalse(t.wantsGps(t.deadline + 1))
        assertEquals(Verdict.CONTINUE, t.judge(t.deadline + 1)) // the trip stays open for the arrival zone
        assertEquals(Verdict.DROP, t.judge(t.leftAt + TRIP_MAX_SEC + 1))
        assertNull(t.close(null))
    }

    @Test fun comingBackHomeIsDropped() {
        val out = lerp(home, mc, 300 / distanceM(home, mc))
        val t = startToWork().copy(fixes = walk(out, home, "07:58:00", "08:02:00"))
        assertEquals(Verdict.DROP, t.judge(at("08:02:00")))
    }

    @Test fun gpsStopsAfterNinetyMinutes() {
        val t = commute().let { c -> c.copy(fixes = c.fixes.filter { it.t <= at("08:20:00") }) }
        assertFalse(t.wantsGps(t.leftAt + SESSION_MAX_SEC + 1))
        assertEquals(Verdict.CONTINUE, t.judge(t.leftAt + SESSION_MAX_SEC + 1))
        val r = t.close(arrivalAtWork(t.leftAt + SESSION_MAX_SEC + 600))!!
        assertNull(r.surfaceAt)
    }

    @Test fun theTriggerFixIsKept() {
        val t = startToWork()
        assertEquals(1, t.fixes.size)
        assertEquals(at("07:58:00"), t.fixes.single().t)
    }

    // ---- zone events ----

    private fun leftHome(time: String = "07:58:00") =
        ZoneEvent(Door.HOME, false, Fix(at(time), lerp(home, mc, 150 / distanceM(home, mc)), 10f))

    private fun leftWork(time: String) =
        ZoneEvent(Door.WORK, false, Fix(at(time), LatLng(work.lat + 0.0017, work.lng), 10f))

    /** The work zone reports arrival ~120 m from the door. */
    private fun arrivalAtWork(t: Long) = Fix(t, LatLng(work.lat + 0.0011, work.lng), 10f)

    @Test fun zonesAloneMakeADoorToDoorTripWithNoCarmelit() {
        val opened = onZone(null, leftHome(), settings, at("07:58:30"))
        assertTrue(opened.opened)
        val t = opened.open!!
        assertEquals(Door.HOME, t.originDoor)
        assertEquals(Door.WORK, t.destDoor)
        val arrived = onZone(t, ZoneEvent(Door.WORK, true, arrivalAtWork(at("08:33:30"))), settings, at("08:34:00"))
        assertNull(arrived.open)
        val end = arrived.ended!!
        assertEquals(Verdict.FINISH, end.verdict)
        val r = end.run!!
        assertEquals(t.leftAt, r[Step.LEAVE])
        assertTrue(r[Step.DOOR]!! in at("08:34:30")..at("08:35:15")) // 122 m at 1.3 m/s after the report
        assertNull(r.entranceAt)
        assertFalse(r.rodeCarmelit)
    }

    @Test fun stationFixThenZoneArrivalKeepsTheStationAndTheEarlierDoorTime() {
        val t = commute().let { c -> c.copy(fixes = c.fixes.filter { it.t <= at("08:20:00") }) }
        val late = onZone(t, ZoneEvent(Door.WORK, true, arrivalAtWork(at("08:33:30"))), settings, at("08:37:00")).ended!!.run!!
        assertEquals(at("08:20:00"), late.entranceAt)
        assertTrue(late.rodeCarmelit)
        // GPS saw the work fence only from 08:32, so the earlier zone report wins.
        val full = commute()
        val both = full.close(Fix(at("08:30:00"), arrivalAtWork(0).at, 10f))!!
        assertEquals(at("08:30:00") + (distanceM(arrivalAtWork(0).at, work) / WALK_MPS).roundToLong(), both[Step.DOOR])
    }

    @Test fun cameBackToTheStartDoorDropsTheTrip() {
        val t = onZone(null, leftHome(), settings, at("07:58:30")).open!!
        val back = onZone(t, ZoneEvent(Door.HOME, true, Fix(at("08:03:00"), home, 10f)), settings, at("08:03:10"))
        assertNull(back.open)
        assertEquals(Verdict.DROP, back.ended!!.verdict)
        assertEquals("came back", back.ended!!.why)
    }

    @Test fun anArrivalWithNoOpenTripChangesNothing() {
        val c = onZone(null, ZoneEvent(Door.WORK, true, arrivalAtWork(at("08:33:30"))), settings, at("08:34:00"))
        assertEquals(TripChange(null), c)
    }

    @Test fun aStaleTripIsEndedAndTheNewLeaveStartsFresh() {
        val old = onZone(null, leftHome("07:58:00"), settings, at("07:58:30")).open!!
        // Nothing for 3 h, then work is left in the afternoon.
        val ev = leftWork("17:30:00")
        val c = onZone(old, ev, settings, at("17:30:30"))
        assertEquals(Verdict.DROP, c.ended!!.verdict)
        assertEquals("no arrival in 3 h", c.ended!!.why)
        assertTrue(c.opened)
        assertTrue(c.open!!.goingHome)
        assertEquals(c.open!!.leftAt, onZone(null, ev, settings, at("17:30:30")).open!!.leftAt)
        assertTrue(c.open!!.leftAt > old.leftAt + TRIP_MAX_SEC)
    }

    @Test fun aSecondLeaveReportWithinTwoMinutesIsTheSameTrip() {
        val t = onZone(null, leftHome("07:58:00"), settings, at("07:58:30")).open!!
        val c = onZone(t, leftHome("07:59:00"), settings, at("07:59:10"))
        assertEquals(TripChange(t), c)
        // A real second leave much later is a new trip.
        val again = onZone(t, leftHome("08:10:00"), settings, at("08:10:10"))
        assertTrue(again.opened)
        assertEquals("left again before arriving", again.ended!!.why)
    }

    @Test fun leavingTheOtherDoorWhileOpenEndsTheOldTripAndStartsTheReturn() {
        val t = onZone(null, leftHome(), settings, at("07:58:30")).open!!
        val c = onZone(t, leftWork("08:30:00"), settings, at("08:30:10"))
        assertEquals(Verdict.DROP, c.ended!!.verdict)
        assertTrue(c.open!!.goingHome)
        assertEquals(Door.WORK, c.open!!.originDoor)
    }

    @Test fun waitingNearTheDoorAfterTheZoneFiredIsNotComingBack() {
        // 100 m from the door, 10 min after leaving: outside the inner half of the zone, so still on the way.
        val near = lerp(home, mc, 100 / distanceM(home, mc))
        val t = startToWork().copy(fixes = walk(near, near, "07:58:00", "08:08:00"))
        assertEquals(Verdict.CONTINUE, t.judge(at("08:08:00")))
    }

    @Test fun anArrivalReportedBeforeTheTripStartedIsIgnored() {
        val t = onZone(null, leftHome(), settings, at("07:58:30")).open!!
        val old = ZoneEvent(Door.WORK, true, arrivalAtWork(t.leftAt - 600))
        assertEquals(TripChange(t), onZone(t, old, settings, at("07:59:00")))
        val oldHome = ZoneEvent(Door.HOME, true, Fix(t.leftAt - 600, home, 10f))
        assertEquals(TripChange(t), onZone(t, oldHome, settings, at("07:59:00")))
    }

    @Test fun leavingWithNoDoorsSetOpensNothing() {
        val c = onZone(null, leftHome(), Settings(), at("07:58:30"))
        assertEquals(TripChange(null), c)
    }

    @Test fun anOpenTripWhoseGpsSawTheArrivalIsFinishedByTheNextEvent() {
        val c = onZone(commute(), leftWork("08:40:00"), settings, at("08:40:10"))
        assertEquals(Verdict.FINISH, c.ended!!.verdict)
        assertTrue(c.ended!!.run!!.rodeCarmelit)
        assertTrue(c.opened)
    }

    @Test fun trackRoundTrips() {
        val t = commute()
        val back = Track.decode((listOf(t.header()) + t.fixes.map(Track::encodeFix)))
        assertEquals(t, back)
        assertEquals(home, LatLng.decode(home.encode()))
    }

    @Test fun zonesAreReaddedOnlyWhenADoorOrThePermissionChanges() {
        val sig = zoneSignature(settings, true)
        assertEquals(sig, zoneSignature(settings.copy(walkHomeMin = 30, marginMin = 3, work = Station.MASADA), true))
        assertTrue(sig != zoneSignature(settings.copy(homeLL = work), true))
        assertTrue(sig != zoneSignature(settings.copy(workLL = null), true))
        assertTrue(sig != zoneSignature(settings, false))
    }

    @Test fun stationsAreWhereTheLineIs() {
        // Ir Tahtit to Merkaz HaCarmel is ~1.8 km as the crow flies.
        val d = distanceM(STATION_POINTS.getValue(Station.IR_TAHTIT), mc)
        assertTrue(d in 1_500.0..2_000.0)
    }
}

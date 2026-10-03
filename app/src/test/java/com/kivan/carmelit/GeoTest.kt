package com.kivan.carmelit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

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
        val r = t.reconstruct()!!
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
        // Long past the station deadline, but the station was reached: wait for the surface.
        assertEquals(Verdict.CONTINUE, t.judge(at("08:30:00")))
        // The cell-tower guess at HaNevi'im is not a surfacing.
        assertNull(t.reconstruct()!!.surfaceAt)
    }

    @Test fun walkThatNeverReachesTheStationIsDropped() {
        val shop = LatLng(32.7990, 34.9750)
        // Starts where the fence fired, 150 m out.
        val t = startToWork().copy(fixes = walk(lerp(home, shop, 150 / distanceM(home, shop)), shop, "07:58:00", "08:10:00"))
        assertEquals(Verdict.CONTINUE, t.judge(at("08:10:00")))
        assertEquals(Verdict.DROP, t.judge(t.deadline + 1))
        assertNull(t.reconstruct())
    }

    @Test fun comingBackHomeIsDropped() {
        val out = lerp(home, mc, 300 / distanceM(home, mc))
        val t = startToWork().copy(fixes = walk(out, home, "07:58:00", "08:02:00"))
        assertEquals(Verdict.DROP, t.judge(at("08:02:00")))
    }

    @Test fun sessionClosesAfterNinetyMinutesWithWhatItHas() {
        val t = commute().let { c -> c.copy(fixes = c.fixes.filter { it.t <= at("08:20:00") }) }
        assertEquals(Verdict.FINISH, t.judge(t.leftAt + SESSION_MAX_SEC + 1))
        val r = t.reconstruct()!!
        assertNull(r[Step.DOOR])
        assertNull(r.surfaceAt)
    }

    @Test fun trackRoundTrips() {
        val t = commute()
        val back = Track.decode((listOf(t.header()) + t.fixes.map(Track::encodeFix)))
        assertEquals(t, back)
        assertEquals(home, LatLng.decode(home.encode()))
    }

    @Test fun stationsAreWhereTheLineIs() {
        // Ir Tahtit to Merkaz HaCarmel is ~1.8 km as the crow flies.
        val d = distanceM(STATION_POINTS.getValue(Station.IR_TAHTIT), mc)
        assertTrue(d in 1_500.0..2_000.0)
    }
}

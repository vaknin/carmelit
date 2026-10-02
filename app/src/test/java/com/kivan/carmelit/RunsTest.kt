package com.kivan.carmelit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class RunsTest {
    private fun at(s: String) = LocalDateTime.parse(s).atZone(ZONE).toEpochSecond()
    private val toWork = Run(false, Station.MERKAZ_HACARMEL, Station.HANEVIIM)
    private val toHome = Run(true, Station.HANEVIIM, Station.MERKAZ_HACARMEL)

    /** Taps given as "HH:mm:ss" on 2026-09-16 (a Wednesday), null = skip. */
    private fun run(base: Run, vararg taps: String?): Run =
        taps.fold(base) { r, t -> if (t == null) r.skip() else r.tap(at("2026-09-16T$t")) }

    @Test fun sixTapsInOrderBothWays() {
        for (base in listOf(toWork, toHome)) {
            var r = base
            for (step in Step.entries) { assertEquals(step, r.next); r = r.tap(step.ordinal + 1L) }
            assertTrue(r.done)
            assertEquals(3L, r[Step.DOORS_OPEN])
        }
    }

    @Test fun undoStepsBackOverTapsAndSkips() {
        val r = toWork.tap(1).tap(2).tap(3)
        assertEquals(Step.DOORS_OPEN, r.undo().next)
        assertNull(r.undo()[Step.DOORS_OPEN])
        assertEquals(Step.PLATFORM, r.undo().undo().next)
        assertEquals(Step.PLATFORM, r.undo().undo().undo().next) // the first tap stays
        assertEquals(Step.DOORS_OPEN, toHome.tap(1).tap(2).skip().undo().next)
    }

    @Test fun terminusDoorsOpenIsRecordedButNeverAnchorsTheMatch() {
        // Train opens at 07:56 for the 08:00; alone that must not match anything.
        assertNull(run(toWork, "07:30:00", "07:52:00", "07:56:00").finished(Settings()).departure)
        val r = run(toWork, "07:30:00", "07:52:00", "07:56:00", "08:01:00").finished(Settings())
        assertEquals(at("2026-09-16T08:00:00"), r.departure)
        assertEquals(Summary(1, -240, -240, -240), RunStats(listOf(r), false, r.from, r.to).boardOffset)
    }

    @Test fun matchesTheTrainRiddenNotTheOneAdvised() {
        // Left 10 min earlier than needed for the 08:12, and caught the 08:00 instead.
        val r = run(toWork, "07:28:00", "07:49:30", null, "08:01:10", "08:07:20", "08:12:00").finished(Settings())
        assertEquals(at("2026-09-16T08:00:00"), r.departure)
        assertEquals(at("2026-09-16T07:38:00"), r.plannedLeave)
    }

    @Test fun midLineMatchUsesTheBoardingOffset() {
        // 17:00 from Ir Tahtit is at HaNevi'im ~17:05; doors close 17:05:40.
        val r = run(toHome, "16:58:00", "17:02:30", "17:05:10", "17:05:40", null, null).finished(Settings())
        assertEquals(at("2026-09-16T17:00:00"), r.departure)
    }

    @Test fun midnightTrainMatchesAcrossTheDateLine() {
        val r = toWork.tap(at("2026-09-16T23:35:00")).tap(at("2026-09-16T23:58:00")).skip()
            .tap(at("2026-09-17T00:01:30")).finished(Settings())
        assertEquals(at("2026-09-17T00:00:00"), r.departure)
    }

    @Test fun noTrainNearbyOrNoTrainTapsMeansNoMatch() {
        assertNull(run(toWork, "02:00:00", "02:20:00", null, "02:30:00").finished(Settings()).departure)
        assertNull(run(toWork, "07:28:00", "07:49:30").finished(Settings()).departure)
        // Doors "closed" 4 min before the 08:00: not that train, and the 07:48 is long gone.
        assertNull(run(toWork, "07:30:00", "07:55:00", null, "07:56:00").finished(Settings()).departure)
    }

    @Test fun summaryUsesMinMaxUntilFiveSamples() {
        assertNull(summarize(emptyList()))
        assertEquals(Summary(3, 20, 10, 30), summarize(listOf(30, 10, 20)))
        assertEquals(Summary(10, 50, 20, 80), summarize((1..10).map { it * 10L }))
    }

    @Test fun neededLeadDoesNotDependOnWhenTheUserLeft() {
        // Same 21:30 walk and the same 1:10 door lag, once leaving with 12 min to spare, once with 1.
        val early = run(toWork, "07:28:00", "07:49:30", null, "08:01:10").finished(Settings())
        val tight = run(toWork, "08:38:40", "09:00:10", null, "09:01:10").finished(Settings())
        val stats = RunStats(listOf(early, tight, run(toHome, "17:00:00", "17:04:00")), false, toWork.from, toWork.to)
        assertEquals(Summary(2, 1290, 1290, 1290), stats.walk)
        assertEquals(Summary(2, 1220, 1220, 1220), stats.neededLead)   // 21:30 walk − 1:10 lag
        assertEquals(Summary(2, 70, 70, 70), stats.closeOffset)
        assertEquals(Summary(2, 60, 60, 700), stats.spare)
        assertEquals(Summary(2, -40, -40, 600), stats.earlyBy)
        assertNull(stats.boardOffset)
    }

    @Test fun storeLinesRoundTrip() {
        val r = run(toHome, "16:58:00", "17:02:30", null, "17:05:40").finished(Settings())
        assertEquals(r, RunStore.decode(RunStore.encode(r)))
        assertNull(RunStore.decode("garbage"))
    }

    /** What GPS made of the 08:00 to-work trip: left 07:28, underground 07:50, up at HaNevi'im 08:07:30, door 08:12. */
    private val gps = Run(
        false, Station.MERKAZ_HACARMEL, Station.HANEVIIM,
        taps = listOf(at("2026-09-16T07:28:00"), null, null, null, null, at("2026-09-16T08:12:00")),
        pos = Step.entries.size, source = Source.GPS,
        entranceAt = at("2026-09-16T07:50:00"), surfaceAt = at("2026-09-16T08:07:30"),
    )

    @Test fun mergeKeepsTapsButReplacesALateLeave() {
        // "Leaving" pressed 4 min down the road; platform and doors tapped properly.
        val manual = run(toWork, "07:32:00", "07:52:00", null, "08:01:10")
        val m = merge(manual, gps)
        assertEquals(Source.MERGED, m.source)
        assertEquals(at("2026-09-16T07:28:00"), m[Step.LEAVE])
        assertEquals(at("2026-09-16T07:52:00"), m[Step.PLATFORM])
        assertEquals(at("2026-09-16T08:12:00"), m[Step.DOOR])
        assertEquals(manual.pos, m.pos) // still open for "Train arrived"
        assertEquals(at("2026-09-16T07:50:00"), m.entranceAt)
        // Pressed at the door (within a minute of GPS): the tap stands.
        assertEquals(at("2026-09-16T07:28:40"), merge(run(toWork, "07:28:40"), gps)[Step.LEAVE])
    }

    @Test fun gpsAloneMatchesOnSurfacing() {
        val r = gps.finished(Settings())
        assertEquals(at("2026-09-16T08:00:00"), r.departure) // 08:07:30 − 1 min climb = 08:00 + 6.5 min
        assertEquals(44 * 60L, r.doorToDoor)
        val st = RunStats(listOf(r), false, r.from, r.to)
        assertEquals(Summary(1, 22 * 60L, 22 * 60L, 22 * 60L), st.entranceWalk)
        assertNull(st.walk) // the entrance is not the platform
    }

    @Test fun recordMergesIntoTheOpenRunOrTheSavedOneOrSavesAlone() {
        val s = Settings()
        val open = run(toWork, "07:32:00", "07:52:00", null, "08:01:10")
        val intoOpen = record(gps, open, emptyList(), s)
        assertEquals(Source.MERGED, intoOpen.active!!.source)
        assertEquals(emptyList<Run>(), intoOpen.runs)
        assertNull(intoOpen.saved)

        val saved = open.finished(s)
        val other = run(toHome, "17:00:00", "17:04:00").finished(s)
        val intoSaved = record(gps, null, listOf(saved, other), s)
        assertEquals(2, intoSaved.runs.size)
        assertEquals(Source.MERGED, intoSaved.runs[0].source)
        assertEquals(at("2026-09-16T08:00:00"), intoSaved.runs[0].departure)
        assertEquals(intoSaved.runs[0], intoSaved.saved)

        // A manual run of yesterday is a different trip.
        val alone = record(gps, null, listOf(run(toWork, "06:00:00").copy(taps = listOf(at("2026-09-15T07:30:00"), null, null, null, null, null))), s)
        assertEquals(2, alone.runs.size)
        assertEquals(Source.GPS, alone.saved!!.source)
    }

    @Test fun runsSavedBeforeGpsStillLoad() {
        val old = "W|MERKAZ_HACARMEL|HANEVIIM|1,2,,,,|6||"
        val r = RunStore.decode(old)!!
        assertEquals(Source.MANUAL, r.source)
        assertNull(r.entranceAt)
        val g = gps.finished(Settings())
        assertEquals(g, RunStore.decode(RunStore.encode(g)))
    }
}

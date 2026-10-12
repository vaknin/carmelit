package com.kivan.carmelit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingStatusTest {
    private val good = StatusInputs(
        doorsSet = true, fine = true, background = true, notifications = true, locationOn = true,
        zonesOk = true, zonesSince = "12 Oct", zoneError = null, autoRevokeExempt = true,
        lastTrip = "to work 31 min, today 08:40",
    )

    @Test fun allGoodSaysWhatItWatchesAndTheLastTrip() {
        val s = recordingStatus(good)
        assertNull(s.problem)
        assertEquals("Watching home and work since 12 Oct · last trip: to work 31 min, today 08:40", s.line)
        assertEquals("Watching home and work", recordingStatus(good.copy(zonesSince = null, lastTrip = null)).line)
    }

    @Test fun eachProblemWinsOverTheOnesBelowIt() {
        val worst = good.copy(
            doorsSet = false, fine = false, background = false, locationOn = false, zonesOk = false,
            notifications = false, autoRevokeExempt = false,
        )
        val steps = listOf<(StatusInputs) -> StatusInputs>(
            { it.copy(doorsSet = true) }, { it.copy(fine = true) }, { it.copy(background = true) },
            { it.copy(locationOn = true) }, { it.copy(zonesOk = true) }, { it.copy(notifications = true) },
            { it.copy(autoRevokeExempt = true) },
        )
        var i = worst
        for (p in Problem.entries) {
            assertEquals(p, recordingStatus(i).problem)
            i = steps[p.ordinal](i)
        }
        assertNull(recordingStatus(i).problem)
    }

    @Test fun neverClaimsWatchingWhenTripsCannotBeSeen() {
        for (blocked in listOf(
            good.copy(zonesOk = false, zoneError = "GEOFENCE_NOT_AVAILABLE"),
            good.copy(locationOn = false),
            good.copy(background = false),
            good.copy(fine = false),
            good.copy(doorsSet = false),
        )) {
            assertFalse(recordingStatus(blocked).line.contains("Watching"))
        }
        assertTrue(recordingStatus(good.copy(zonesOk = false, zoneError = "TIMEOUT")).line.contains("TIMEOUT"))
    }
}

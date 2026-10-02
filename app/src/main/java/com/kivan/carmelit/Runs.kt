package com.kivan.carmelit

import android.content.Context
import androidx.core.content.edit
import java.time.Instant
import kotlin.math.abs
import kotlin.math.ceil

/** The six taps of a timed trip, in order. Labels name the event being tapped. */
enum class Step(private val toWork: String, private val toHome: String) {
    LEAVE("Leaving home", "Leaving work"),
    PLATFORM("On the platform", "On the platform"),
    DOORS_OPEN("Doors open", "Doors open"),
    DOORS_CLOSE("Doors closed", "Doors closed"),
    ARRIVE("Train arrived", "Train arrived"),
    DOOR("At work", "At home");

    fun label(goingHome: Boolean) = if (goingHome) toHome else toWork
}

/** Where a run's times came from: the buttons, GPS alone, or both ([merge]). */
enum class Source { MANUAL, GPS, MERGED }

/**
 * One timed trip. [taps] holds epoch seconds per [Step]; null below [pos] means skipped.
 * [departure] is the published terminus departure of the train actually ridden, found from the
 * taps themselves (never from what the app suggested), and [plannedLeave] is when the app would
 * have said to leave for that train. Both are filled in by [finished].
 * GPS adds two times no button has: [entranceAt], last seen at the boarding station before going
 * underground, and [surfaceAt], first seen at the destination station after the ride.
 */
data class Run(
    val goingHome: Boolean,
    val from: Station,
    val to: Station,
    val taps: List<Long?> = List(Step.entries.size) { null },
    val pos: Int = 0,
    val departure: Long? = null,
    val plannedLeave: Long? = null,
    val source: Source = Source.MANUAL,
    val entranceAt: Long? = null,
    val surfaceAt: Long? = null,
) {
    operator fun get(step: Step): Long? = taps[step.ordinal]

    /** True when boarding at the line's first station, where the train waits before its departure. */
    val boardsAtTerminus: Boolean get() = stopsFromOrigin(from, Direction.between(from, to)) == 0

    val next: Step? get() = Step.entries.getOrNull(pos)
    val done: Boolean get() = next == null

    fun tap(at: Long): Run = copy(taps = taps.toMutableList().also { it[pos] = at }, pos = pos + 1)
    fun skip(): Run = copy(pos = pos + 1)

    /** Steps back over the last tap or skip. The first tap cannot be undone; discard the run instead. */
    fun undo(): Run =
        if (pos <= 1) this else copy(taps = taps.toMutableList().also { it[pos - 1] = null }, pos = pos - 1)

    /** Door to door, when both ends are known. */
    val doorToDoor: Long? get() = this[Step.LEAVE]?.let { a -> this[Step.DOOR]?.let { b -> b - a } }
}

/** A manual LEAVE this much later than the GPS one was pressed on the way, not at the door. */
private const val LATE_LEAVE_SEC = 60L

/**
 * A GPS recording folded into a manual run of the same trip. Taps win, except a LEAVE pressed
 * more than [LATE_LEAVE_SEC] after GPS saw you leave. [manual]'s position is kept, so an
 * unfinished run can still be tapped on; a later tap overwrites a GPS-filled step, and Skip keeps it.
 */
fun merge(manual: Run, gps: Run): Run {
    val taps = manual.taps.indices.map { i -> manual.taps[i] ?: gps.taps[i] }.toMutableList()
    val m = manual[Step.LEAVE]
    val g = gps[Step.LEAVE]
    if (g != null && (m == null || m - g > LATE_LEAVE_SEC)) taps[Step.LEAVE.ordinal] = g
    return manual.copy(
        taps = taps, departure = null, plannedLeave = null, source = Source.MERGED,
        entranceAt = gps.entranceAt, surfaceAt = gps.surfaceAt,
    )
}

/**
 * How far a tap may be from a train's predicted time and still be that train: up to half the
 * shortest headway late, but only 2 min early — trains run late, they do not leave ahead of time.
 */
private val MATCH_WINDOW_SEC = -2 * 60L..6 * 60L

/**
 * Attaches the train that was really ridden: the published departure whose predicted time at the
 * tapped event is nearest. Doors closing is the best anchor, then doors opening (not at a terminus,
 * where they open minutes before the departure), then arrival, then GPS surfacing at the
 * destination less the [CLIMB_SEC] climb.
 * This is what makes the data independent of when the user chose to leave.
 */
fun Run.finished(s: Settings): Run {
    val leg = Leg.of(s, goingHome)
    val (ref, offset) = this[Step.DOORS_CLOSE]?.let { it to leg.boardOffset }
        ?: this[Step.DOORS_OPEN]?.takeIf { !boardsAtTerminus }?.let { it to leg.boardOffset }
        ?: this[Step.ARRIVE]?.let { it to leg.alightOffset }
        ?: surfaceAt?.let { (it - CLIMB_SEC) to leg.alightOffset }
        ?: return this
    val day = Instant.ofEpochSecond(ref).atZone(ZONE).toLocalDate()
    // Yesterday too: its 24:00 train is today's 00:00.
    val best = listOf(day.minusDays(1), day)
        .flatMap { d -> departures(classifyDay(d), isDst(d)).map { d.atStartOfDay().plusMinutes(it.toLong()).atZone(ZONE).toEpochSecond() } }
        .minByOrNull { abs(ref - (it + offset.seconds)) }
        ?.takeIf { ref - (it + offset.seconds) in MATCH_WINDOW_SEC }
        ?: return this
    return copy(departure = best, plannedLeave = best + leg.boardOffset.seconds - leg.walk.seconds - leg.margin.seconds)
}

/** A manual run started this long before GPS saw you leave can still be the same trip. */
private const val SAME_TRIP_SEC = 15 * 60L

/** The run store after [record]; [saved] is the finished run that was added or replaced, if any. */
data class Recorded(val active: Run?, val runs: List<Run>, val saved: Run?)

/**
 * Files a finished GPS recording. A manual run of the same trip still open gets the GPS times
 * and stays open for its remaining taps; one already saved is replaced by the merge; otherwise
 * the GPS run is saved on its own.
 */
fun record(gps: Run, active: Run?, runs: List<Run>, s: Settings): Recorded {
    val leave = gps[Step.LEAVE] ?: return Recorded(active, runs, null)
    fun sameTrip(r: Run): Boolean {
        val start = r.taps.firstNotNullOfOrNull { it } ?: return false
        return r.goingHome == gps.goingHome && r.from == gps.from && r.to == gps.to &&
            start in leave - SAME_TRIP_SEC..leave + SESSION_MAX_SEC
    }
    if (active != null && sameTrip(active)) {
        val merged = merge(active, gps)
        return if (merged.done) merged.finished(s).let { Recorded(null, runs + it, it) }
        else Recorded(merged, runs, null)
    }
    val i = runs.indexOfLast { it.source == Source.MANUAL && sameTrip(it) }
    if (i >= 0) {
        val merged = merge(runs[i], gps).finished(s)
        return Recorded(active, runs.toMutableList().also { it[i] = merged }, merged)
    }
    val saved = gps.finished(s)
    return Recorded(active, runs + saved, saved)
}

/** Typical value plus the two ends worth planning for; all in seconds. */
data class Summary(val n: Int, val median: Long, val low: Long, val high: Long)

/** Median with the 20th/80th percentiles (nearest rank); under 5 samples the ends are min/max. */
fun summarize(values: List<Long>): Summary? {
    if (values.isEmpty()) return null
    val v = values.sorted()
    fun rank(p: Double) = v[(ceil(p * v.size).toInt() - 1).coerceIn(0, v.lastIndex)]
    val few = v.size < 5
    return Summary(v.size, rank(0.5), if (few) v.first() else rank(0.2), if (few) v.last() else rank(0.8))
}

/**
 * What the runs of one direction between the current stations say. Everything is relative to the
 * train actually ridden, so leaving early or late changes only [spare] and [earlyBy], never the
 * quantities the planner needs.
 */
class RunStats(runs: List<Run>, goingHome: Boolean, from: Station, to: Station) {
    private val mine = runs.filter { it.goingHome == goingHome && it.from == from && it.to == to }

    private fun collect(f: (Run) -> Long?): Summary? =
        summarize(mine.mapNotNull(f).filter { it in -MAX_SEC..MAX_SEC })

    private fun Run.walkSec(): Long? = diff(this[Step.PLATFORM], this[Step.LEAVE])?.takeIf { it > 0 }

    /** Door to platform, ticket bought. */
    val walk = collect { it.walkSec() }
    /** Doors open at the boarding station, after the published departure (negative at a terminus). */
    val boardOffset = collect { diff(it[Step.DOORS_OPEN], it.departure) }
    /** Doors closed — the real deadline — after the published departure. */
    val closeOffset = collect { diff(it[Step.DOORS_CLOSE], it.departure) }
    /** Train at the destination, after the published departure. */
    val arriveOffset = collect { diff(it[Step.ARRIVE], it.departure) }
    /** Destination platform to the door. */
    val lastLeg = collect { diff(it[Step.DOOR], it[Step.ARRIVE])?.takeIf { d -> d > 0 } }
    /** Door to the station entrance (GPS: last seen before going underground). */
    val entranceWalk = collect { diff(it.entranceAt, it[Step.LEAVE])?.takeIf { d -> d > 0 } }
    /** The whole trip, door to door. */
    val doorToDoor = collect { it.doorToDoor?.takeIf { d -> d > 0 } }
    /** Outcome, not a parameter: time on the platform before the doors closed. */
    val spare = collect { diff(it[Step.DOORS_CLOSE], it[Step.PLATFORM]) }
    /**
     * How long before the published departure the user had to leave to just make the doors:
     * departure − (doors closed − walk). The same whatever time they really left, which is why it
     * is the number to compare the app's advice with. [Summary.high] is the cautious end.
     */
    val neededLead = collect { r -> r.walkSec()?.let { w -> diff(r.departure, r[Step.DOORS_CLOSE])?.plus(w) } }
    /** How long before the app's advised time the user really left (negative = later). */
    val earlyBy = collect { diff(it.plannedLeave, it[Step.LEAVE]) }

    private companion object {
        const val MAX_SEC = 3 * 3600L
        fun diff(a: Long?, b: Long?): Long? = if (a != null && b != null) a - b else null
    }
}

/** Runs live in their own SharedPreferences file, one line per run; the last [KEEP] are kept. */
class RunStore(context: Context) {
    private val prefs = context.getSharedPreferences("runs", Context.MODE_PRIVATE)

    fun active(): Run? = prefs.getString("active", null)?.let(::decode)
    fun saveActive(run: Run?) = prefs.edit { if (run != null) putString("active", encode(run)) else remove("active") }

    fun all(): List<Run> = prefs.getString("runs", "")!!.lines().mapNotNull(::decode)
    fun saveAll(runs: List<Run>) = prefs.edit { putString("runs", runs.takeLast(KEEP).joinToString("\n", transform = ::encode)) }

    companion object {
        const val KEEP = 200

        fun encode(r: Run): String = listOf(
            if (r.goingHome) "H" else "W", r.from.name, r.to.name,
            r.taps.joinToString(",") { it?.toString() ?: "" }, r.pos, r.departure ?: "", r.plannedLeave ?: "",
            r.source.name, r.entranceAt ?: "", r.surfaceAt ?: "",
        ).joinToString("|")

        fun decode(line: String): Run? = runCatching {
            val p = line.split("|")
            Run(
                goingHome = p[0] == "H", from = Station.valueOf(p[1]), to = Station.valueOf(p[2]),
                taps = p[3].split(",").map { it.toLongOrNull() }.also { require(it.size == Step.entries.size) },
                pos = p[4].toInt(), departure = p[5].toLongOrNull(), plannedLeave = p[6].toLongOrNull(),
                // Runs saved before GPS have seven fields.
                source = p.getOrNull(7)?.let { Source.valueOf(it) } ?: Source.MANUAL,
                entranceAt = p.getOrNull(8)?.toLongOrNull(), surfaceAt = p.getOrNull(9)?.toLongOrNull(),
            )
        }.getOrNull()
    }
}

package com.kivan.carmelit

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/** A point on the map, in degrees. Stored as "lat,lng". */
data class LatLng(val lat: Double, val lng: Double) {
    fun encode() = "$lat,$lng"

    companion object {
        fun decode(s: String?): LatLng? = s?.split(",")?.takeIf { it.size == 2 }?.let { (a, b) ->
            val lat = a.toDoubleOrNull() ?: return null
            val lng = b.toDoubleOrNull() ?: return null
            LatLng(lat, lng)
        }
    }
}

/** Great-circle distance in metres. */
fun distanceM(a: LatLng, b: LatLng): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLng = Math.toRadians(b.lng - a.lng)
    val h = sin(dLat / 2).let { it * it } +
        cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2).let { it * it }
    return 2 * r * asin(sqrt(h))
}

/**
 * Where each station is, from the Ministry of Transport GTFS feed of 2026-10-01 (stops.txt,
 * stop_ids 41077–41082, the stops of agency 20's two routes). One point per station; the street
 * entrances are within [ENTRANCE_RADIUS_M] of it.
 */
val STATION_POINTS: Map<Station, LatLng> = mapOf(
    Station.IR_TAHTIT to LatLng(32.817376, 34.999771),
    Station.HADAR to LatLng(32.814980, 34.997397),
    Station.HANEVIIM to LatLng(32.812858, 34.995149),
    Station.MASADA to LatLng(32.809530, 34.991994),
    Station.BNEI_ZION to LatLng(32.807299, 34.990084),
    Station.MERKAZ_HACARMEL to LatLng(32.804982, 34.987705),
)

/** Radius of the home and work geofences. GPS indoors is easily 50 m off. */
const val FENCE_RADIUS_M = 120f
const val ENTRANCE_RADIUS_M = 80.0
/** Fixes worse than this are ignored; underground the phone reports cell-tower guesses. */
const val GOOD_ACCURACY_M = 50f
/** Walking speed used to move an edge-of-fence fix back to the door. */
const val WALK_MPS = 1.3
/** From the train's doors at the destination up to the street, where GPS comes back. */
const val CLIMB_SEC = 60L
/** How long past the usual walk a session waits for the station before it is not a commute. */
const val STATION_GRACE_SEC = 20 * 60L
/** A session that has not reached the destination by then is closed with what it has. */
const val SESSION_MAX_SEC = 90 * 60L
/** Back inside the start fence after this long without reaching the station: not a commute. */
const val RETURN_SEC = 3 * 60L
/** A trip with no arrival this long after leaving is dropped. */
const val TRIP_MAX_SEC = 3 * 3600L
/** The boarding station was reached, but no good fix for this long: underground, GPS can stop. */
const val UNDERGROUND_MAX_SEC = 25 * 60L
/** Two reports of leaving the same door this close together are one report. */
const val DUPLICATE_SEC = 120L

/** The two ends of a trip. */
enum class Door { HOME, WORK }

/** One GPS fix: epoch seconds, position, accuracy radius in metres. */
data class Fix(val t: Long, val at: LatLng, val acc: Float) {
    val good: Boolean get() = acc <= GOOD_ACCURACY_M
}

/**
 * One recording session, from leaving the [origin] fence towards [dest]. [leftAt] is the leave
 * instant already moved back to the door; [deadline] is when the station must have been reached.
 */
data class Track(
    val goingHome: Boolean,
    val from: Station,
    val to: Station,
    val origin: LatLng,
    val dest: LatLng,
    val leftAt: Long,
    val deadline: Long,
    val fixes: List<Fix> = emptyList(),
) {
    val originDoor: Door get() = if (goingHome) Door.WORK else Door.HOME
    val destDoor: Door get() = if (goingHome) Door.HOME else Door.WORK

    fun header(): String = listOf(
        if (goingHome) "H" else "W", from.name, to.name, origin.encode(), dest.encode(), leftAt, deadline,
    ).joinToString("|")

    companion object {
        fun encodeFix(f: Fix) = "${f.t},${f.at.lat},${f.at.lng},${f.acc}"

        fun decodeFix(line: String): Fix? = runCatching {
            val p = line.split(",")
            Fix(p[0].toLong(), LatLng(p[1].toDouble(), p[2].toDouble()), p[3].toFloat())
        }.getOrNull()

        /** The header line followed by one fix per line; null when the header is unreadable. */
        fun decode(lines: List<String>): Track? = runCatching {
            val p = lines.first().split("|")
            Track(
                goingHome = p[0] == "H", from = Station.valueOf(p[1]), to = Station.valueOf(p[2]),
                origin = LatLng.decode(p[3])!!, dest = LatLng.decode(p[4])!!,
                leftAt = p[5].toLong(), deadline = p[6].toLong(),
                fixes = lines.drop(1).mapNotNull(::decodeFix),
            )
        }.getOrNull()

        /**
         * A new session after the geofence reported leaving. [trigger] is the fix that tripped it,
         * already outside the fence; the leave instant is moved back by the distance from the
         * centre at walking pace, which also absorbs a late trigger. The trigger is kept as the first
         * fix. Null when home or work is not set.
         */
        fun start(s: Settings, goingHome: Boolean, trigger: Fix): Track? {
            val origin = (if (goingHome) s.workLL else s.homeLL) ?: return null
            val dest = (if (goingHome) s.homeLL else s.workLL) ?: return null
            val leg = Leg.of(s, goingHome)
            val leftAt = trigger.t - (distanceM(origin, trigger.at) / WALK_MPS).roundToLong()
            return Track(goingHome, leg.from, leg.to, origin, dest, leftAt,
                deadline = leftAt + leg.walk.seconds + STATION_GRACE_SEC, fixes = listOf(trigger))
        }
    }
}

enum class Verdict { CONTINUE, DROP, FINISH }

private fun Track.goodFixes() = fixes.filter { it.good }.sortedBy { it.t }
private fun Fix.near(p: LatLng, radius: Double) = distanceM(at, p) <= radius
private val Track.entrance: LatLng get() = STATION_POINTS.getValue(from)
private val Track.exit: LatLng get() = STATION_POINTS.getValue(to)

/**
 * What an open trip is at [now], from its GPS fixes and the clock: FINISH once a good fix after
 * leaving is inside the destination fence (the station is not required); DROP when you are back
 * in the start fence, or [TRIP_MAX_SEC] passed without an arrival. Zone events decide the rest.
 */
fun Track.judge(now: Long): Verdict {
    val good = goodFixes()
    return when {
        good.any { it.t > leftAt && it.near(dest, FENCE_RADIUS_M.toDouble()) } -> Verdict.FINISH
        good.any { it.t - leftAt > RETURN_SEC && it.near(origin, FENCE_RADIUS_M / 2.0) } -> Verdict.DROP
        now - leftAt > TRIP_MAX_SEC -> Verdict.DROP
        else -> Verdict.CONTINUE
    }
}

/**
 * Whether GPS is still worth running at [now]. Not after [SESSION_MAX_SEC]; once the boarding
 * station was reached, not after [UNDERGROUND_MAX_SEC] without a good fix; before it, not past the
 * deadline (a walk to the shop). The trip itself stays open for the arrival zone.
 */
fun Track.wantsGps(now: Long): Boolean {
    if (now - leftAt > SESSION_MAX_SEC) return false
    val good = goodFixes()
    return if (good.any { it.near(entrance, ENTRANCE_RADIUS_M) }) now - good.last().t <= UNDERGROUND_MAX_SEC
    else now <= deadline
}

/**
 * The [Run] of a finished trip, or null when no door time can be told. [arrival] is the fix the
 * arrival zone reported, if any.
 *  - LEAVE: [Track.leftAt].
 *  - DOOR: the earlier of the first good fix inside the destination fence (moved forward by its
 *    distance to the door) and [arrival] (likewise).
 *  - [Run.entranceAt]: the last good fix at the boarding station before the destination station
 *    is seen, i.e. the moment you went underground. Null when the station was never seen.
 *  - [Run.surfaceAt]: the first good fix at the destination station after that.
 */
fun Track.close(arrival: Fix?): Run? {
    val good = goodFixes()
    val toDoor = { f: Fix -> f.t + (distanceM(f.at, dest) / WALK_MPS).roundToLong() }
    val door = listOfNotNull(
        good.firstOrNull { it.t > leftAt && it.near(dest, FENCE_RADIUS_M.toDouble()) }?.let(toDoor),
        arrival?.takeIf { it.t > leftAt }?.let(toDoor),
    ).minOrNull()?.takeIf { it > leftAt } ?: return null
    val firstAtExit = good.firstOrNull { it.near(exit, ENTRANCE_RADIUS_M) && good.any { e -> e.t < it.t && e.near(entrance, ENTRANCE_RADIUS_M) } }
    val entered = good.lastOrNull { it.near(entrance, ENTRANCE_RADIUS_M) && (firstAtExit == null || it.t < firstAtExit.t) }
    val surfaced = entered?.let { e -> good.firstOrNull { it.t > e.t + CLIMB_SEC && it.near(exit, ENTRANCE_RADIUS_M) } }
    val taps = MutableList<Long?>(Step.entries.size) { null }
    taps[Step.LEAVE.ordinal] = leftAt
    taps[Step.DOOR.ordinal] = door
    return Run(
        goingHome, from, to, taps, pos = Step.entries.size,
        source = Source.GPS, entranceAt = entered?.t, surfaceAt = surfaced?.t,
    )
}

/**
 * What the registered zones depend on: both doors, the radius, the transitions (v2: leaving and
 * arriving) and whether location may be used. Zones are re-added only when this changes.
 */
fun zoneSignature(s: Settings, canRecord: Boolean): String =
    listOf("v2", s.homeLL?.encode() ?: "-", s.workLL?.encode() ?: "-", FENCE_RADIUS_M, canRecord).joinToString("|")

/** A zone report: [at] is where the phone was when it fired (or a stand-in when Android sent none). */
data class ZoneEvent(val door: Door, val entered: Boolean, val at: Fix)

/** A trip that ended. [run] is set when it is worth keeping; [why] says what happened in plain words. */
data class Ended(val track: Track, val verdict: Verdict, val run: Run?, val why: String)

/** The open trip after an event, and the trip it closed or dropped, if any. */
data class TripChange(val open: Track?, val ended: Ended? = null, val opened: Boolean = false)

private fun finishOrDrop(t: Track, arrival: Fix?): Ended =
    t.close(arrival)?.let { Ended(t, Verdict.FINISH, it, "") } ?: Ended(t, Verdict.DROP, null, "no door time")

private fun staleWhy(t: Track, now: Long) = if (now - t.leftAt > TRIP_MAX_SEC) "no arrival in 3 h" else "came back"

/** How [Track.judge] says this trip is over at [now]; null while it goes on. */
fun Track.endedAt(now: Long): Ended? = when (judge(now)) {
    Verdict.DROP -> Ended(this, Verdict.DROP, null, staleWhy(this, now))
    Verdict.FINISH -> finishOrDrop(this, null)
    Verdict.CONTINUE -> null
}

/**
 * What a zone report does to the [open] trip at [now]. Leaving a door opens a trip (ending any
 * earlier one: a leftover never hijacks it); arriving at the other door ends it with a run;
 * arriving back at the same door drops it. Everything is a TripChange, nothing is applied here.
 */
fun onZone(open: Track?, e: ZoneEvent, s: Settings, now: Long): TripChange {
    var cur = open
    var ended = cur?.endedAt(now)
    if (ended != null) cur = null
    if (!e.entered) {
        val next = Track.start(s, goingHome = e.door == Door.WORK, e.at)
        if (cur != null && next != null && cur.originDoor == e.door && abs(next.leftAt - cur.leftAt) <= DUPLICATE_SEC)
            return TripChange(cur)
        if (cur != null) ended = Ended(cur, Verdict.DROP, null, "left again before arriving")
        return TripChange(next, ended, opened = next != null)
    }
    if (cur == null) return TripChange(null, ended)
    if (e.at.t < cur.leftAt) return TripChange(cur, ended) // reported out of order: not about this trip
    return if (e.door == cur.destDoor) TripChange(null, finishOrDrop(cur, e.at))
    else TripChange(null, Ended(cur, Verdict.DROP, null, "came back"))
}

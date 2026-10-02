package com.kivan.carmelit

import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime

/** One catchable train. [departure] is the published terminus time; the rest are derived. */
data class Trip(
    val departure: ZonedDateTime,
    val boardAt: ZonedDateTime,
    val leaveAt: ZonedDateTime,
    val arriveAt: ZonedDateTime,
    val dayType: DayType,
)

/**
 * The answer for one direction: the next [Trip]s and the timetable of the first one's day.
 * [todayType] is today's timetable and [missedLast] the last train of today whose leave time has
 * already passed, both so the UI can explain a headline that jumps to a later day.
 * [earlier] holds up to two trains that are no longer catchable on the walk but have not left
 * your station yet, oldest first: if you are already on the way, one of them may be yours.
 * [leftAt] is set when a recorded trip says you already left; catchability is then judged from
 * that instant instead of from now.
 */
data class Plan(
    val from: Station,
    val to: Station,
    val trips: List<Trip>,
    val serviceDay: LocalDate,
    val dayType: DayType,
    val isDst: Boolean,
    val todayType: DayType,
    val missedLast: Trip?,
    val earlier: List<Trip> = emptyList(),
    val leftAt: ZonedDateTime? = null,
)

/** How long after its boarding time a train still shows in [Plan.earlier]: doors close late. */
private val EARLIER_GRACE: Duration = Duration.ofMinutes(1)
private const val EARLIER_MAX = 2

private fun minutesToDuration(min: Double): Duration = Duration.ofSeconds(Math.round(min * 60))

/** The settings resolved for one direction: who walks how long, and the two station offsets. */
data class Leg(
    val from: Station,
    val to: Station,
    val walk: Duration,
    val margin: Duration,
    val boardOffset: Duration,
    val alightOffset: Duration,
) {
    companion object {
        fun of(s: Settings, goingHome: Boolean): Leg {
            val from = if (goingHome) s.work else s.home
            val to = if (goingHome) s.home else s.work
            val dir = Direction.between(from, to)
            return Leg(
                from, to,
                walk = Duration.ofMinutes((if (goingHome) s.walkWorkMin else s.walkHomeMin).toLong()),
                margin = Duration.ofMinutes(s.marginMin.toLong()),
                boardOffset = (if (goingHome) s.fromWorkOffsetMin?.let(::minutesToDuration) else null)
                    ?: Duration.ofSeconds(defaultOffsetSeconds(from, dir).toLong()),
                alightOffset = (if (!goingHome) s.toWorkOffsetMin?.let(::minutesToDuration) else null)
                    ?: Duration.ofSeconds(defaultOffsetSeconds(to, dir).toLong()),
            )
        }
    }
}

/**
 * The next [count] trains the user can still catch when leaving at or after [now], or, when
 * [leftAt] is given, the ones reachable from having left then (and not yet gone).
 * Scans up to 8 days ahead; the longest possible closure is two consecutive rest days.
 */
fun nextTrips(now: ZonedDateTime, s: Settings, goingHome: Boolean, count: Int = 4, leftAt: ZonedDateTime? = null): Plan {
    val leg = Leg.of(s, goingHome)
    val trips = ArrayList<Trip>(count)
    val today = now.withZoneSameInstant(ZONE).toLocalDate()
    var firstDay: LocalDate? = null
    var firstType = DayType.CLOSED
    var firstDst = false
    var todayType = DayType.CLOSED
    var missedLast: Trip? = null
    val earlier = ArrayList<Trip>()
    // From yesterday: its 24:00 train boards after midnight.
    days@ for (i in -1L until 8L) {
        val day = today.plusDays(i)
        val type = classifyDay(day)
        val dst = isDst(day)
        if (i == 0L) todayType = type
        for (t in departures(type, dst)) {
            // Local-time arithmetic, so DST days are right and 1440 lands on the next day's 00:00.
            val dep = day.atStartOfDay().plusMinutes(t.toLong()).atZone(ZONE)
            val board = dep.plus(leg.boardOffset)
            val leave = board.minus(leg.walk).minus(leg.margin)
            val trip = Trip(dep, board, leave, dep.plus(leg.alightOffset), type)
            val catchable = if (leftAt == null) leave >= now else leave >= leftAt && board >= now
            if (!catchable) {
                if (i == 0L) missedLast = trip
                if (board >= now.minus(EARLIER_GRACE)) earlier += trip
                continue
            }
            if (firstDay == null) { firstDay = day; firstType = type; firstDst = dst }
            trips += trip
            if (trips.size >= count) break@days
        }
    }
    // Only meaningful when today still had trains but none is catchable any more.
    if (firstDay == today) missedLast = null
    return Plan(
        leg.from, leg.to, trips, firstDay ?: today, firstType, firstDst, todayType, missedLast,
        earlier.takeLast(EARLIER_MAX), leftAt,
    )
}

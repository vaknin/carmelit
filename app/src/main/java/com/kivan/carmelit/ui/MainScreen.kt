package com.kivan.carmelit.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kivan.carmelit.DayType
import com.kivan.carmelit.Plan
import com.kivan.carmelit.R
import com.kivan.carmelit.Station
import com.kivan.carmelit.Trip
import com.kivan.carmelit.timetableLabel
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DateTextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

private val BAR_WINDOW: Duration = Duration.ofMinutes(15)
private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private fun ZonedDateTime.hhmm(): String = format(HHMM)
private fun ZonedDateTime.hhmmRounded(): String = plusSeconds(30).format(HHMM)

/**
 * When the train is at *your* station, which is what the walk is measured against. Exact at a
 * terminus (the published departure); a "~" estimate at any other station.
 */
private fun Trip.boardLabel(): String =
    if (boardAt == departure) boardAt.hhmm() else "~" + boardAt.hhmmRounded()

/** "tomorrow" / "Sunday" relative to [today]; empty for today. */
private fun dayLabel(date: LocalDate, today: LocalDate, capitalized: Boolean = false): String {
    val s = when (ChronoUnit.DAYS.between(today, date)) {
        0L -> ""
        1L -> "tomorrow"
        else -> date.dayOfWeek.getDisplayName(DateTextStyle.FULL, Locale.ENGLISH)
    }
    return if (capitalized) s.replaceFirstChar { it.uppercase() } else s
}

@Composable
private fun dim(): Color = MaterialTheme.colorScheme.onSurfaceVariant

@Composable
fun MainScreen(
    plan: Plan,
    now: ZonedDateTime,
    goingHome: Boolean,
    onSetGoingHome: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    timer: @Composable () -> Unit = {},
) {
    val today = now.toLocalDate()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 24.dp)
            .padding(top = 8.dp, bottom = 20.dp),
    ) {
        TopBar(goingHome, onSetGoingHome, onOpenSettings)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            val first = plan.trips.firstOrNull()
            if (first == null) {
                Spacer(Modifier.height(48.dp))
                Text("No service found in the next 8 days", style = MaterialTheme.typography.headlineSmall)
            } else {
                Spacer(Modifier.height(28.dp))
                Headline(first, now)
                Spacer(Modifier.height(28.dp))
                TripCard(first, plan.from, plan.to)
                if (plan.trips.size > 1) {
                    Spacer(Modifier.height(28.dp))
                    LaterList(plan.trips, today)
                }
                ServiceNotice(plan, today)
            }
        }
        Spacer(Modifier.height(12.dp))
        timer()
        Spacer(Modifier.height(12.dp))
        Footer(plan)
    }
}

@Composable
private fun TopBar(goingHome: Boolean, onSetGoingHome: (Boolean) -> Unit, onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SingleChoiceSegmentedButtonRow {
            SegmentedButton(
                selected = !goingHome,
                onClick = { onSetGoingHome(false) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                icon = {},
            ) { Text("To work") }
            SegmentedButton(
                selected = goingHome,
                onClick = { onSetGoingHome(true) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                icon = {},
            ) { Text("To home") }
        }
        IconButton(onClick = onOpenSettings) {
            Icon(painterResource(R.drawable.ic_settings), contentDescription = "Settings", tint = dim())
        }
    }
}

@Composable
private fun Headline(first: Trip, now: ZonedDateTime) {
    val remaining = Duration.between(now, first.leaveAt)
    val minutes = Math.floorDiv(remaining.seconds, 60L)
    val urgent = minutes <= 3
    val color = if (urgent) Accent else MaterialTheme.colorScheme.onSurface

    Text(if (minutes <= 0) "LEAVE" else "LEAVE IN", style = eyebrow(), color = dim())
    // (big text, small unit) keyed on the string so a change cross-fades instead of popping.
    val hero: Pair<String, String?> = when {
        minutes <= 0 -> "now" to null
        minutes >= 100 -> "%d h %02d".format(minutes / 60, minutes % 60) to null
        else -> "$minutes" to "min"
    }
    AnimatedContent(
        targetState = hero,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "hero",
    ) { (big, unit) ->
        Row(verticalAlignment = Alignment.Bottom) {
            val style = if (unit == null) Hero.copy(fontSize = 84.sp, lineHeight = 92.sp) else Hero
            Text(big, style = style, color = color)
            if (unit != null) {
                Text(
                    " $unit",
                    style = MaterialTheme.typography.headlineSmall,
                    color = dim(),
                    modifier = Modifier.padding(bottom = 18.dp),
                )
            }
        }
    }
    val prefix = dayLabel(first.leaveAt.toLocalDate(), now.toLocalDate())
    Text(
        (if (prefix.isEmpty()) "" else "$prefix ") + "at ${first.leaveAt.hhmm()}",
        style = MaterialTheme.typography.headlineSmall.merge(Tabular),
    )
    // Drains over the last 15 minutes (the longest headway); hidden before that.
    if (remaining < BAR_WINDOW) {
        val fraction = (remaining.seconds.toFloat() / BAR_WINDOW.seconds).coerceIn(0f, 1f)
        val animated by animateFloatAsState(fraction, label = "countdown")
        Spacer(Modifier.height(16.dp))
        LinearProgressIndicator(
            progress = { animated },
            modifier = Modifier.fillMaxWidth().height(3.dp),
            color = if (urgent) Accent else MaterialTheme.colorScheme.onSurface,
            trackColor = MaterialTheme.colorScheme.outlineVariant,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

@Composable
private fun TripCard(trip: Trip, from: Station, to: Station) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            StationColumn(trip.boardLabel(), from, Alignment.Start, Modifier.weight(1f))
            Text(
                "→",
                style = MaterialTheme.typography.titleLarge,
                color = dim(),
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            StationColumn("~" + trip.arriveAt.hhmmRounded(), to, Alignment.End, Modifier.weight(1f))
        }
    }
}

@Composable
private fun StationColumn(time: String, station: Station, align: Alignment.Horizontal, modifier: Modifier) {
    val textAlign = if (align == Alignment.End) TextAlign.End else TextAlign.Start
    Column(modifier = modifier, horizontalAlignment = align) {
        Text(time, style = MaterialTheme.typography.titleLarge.merge(Tabular), textAlign = textAlign)
        Spacer(Modifier.height(4.dp))
        // Each Text holds a single script, so nothing needs bidi isolation.
        Text(station.hebrew, style = MaterialTheme.typography.bodyLarge, textAlign = textAlign)
        Text(station.english, style = MaterialTheme.typography.bodySmall, color = dim(), textAlign = textAlign)
    }
}

@Composable
private fun LaterList(trips: List<Trip>, today: LocalDate) {
    Text("LATER", style = eyebrow(), color = dim())
    Spacer(Modifier.height(8.dp))
    ThreeColumns("leave", "board", "arrive", MaterialTheme.typography.labelMedium, dim())
    var previousDay = trips[0].leaveAt.toLocalDate()
    for (trip in trips.drop(1)) {
        val day = trip.leaveAt.toLocalDate()
        if (day != previousDay) {
            Spacer(Modifier.height(10.dp))
            Text(dayLabel(day, today, capitalized = true).uppercase(), style = eyebrow(), color = dim())
            previousDay = day
        }
        Spacer(Modifier.height(6.dp))
        ThreeColumns(
            trip.leaveAt.hhmm(), trip.boardLabel(), "~" + trip.arriveAt.hhmmRounded(),
            MaterialTheme.typography.bodyLarge.merge(Tabular), MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ThreeColumns(a: String, b: String, c: String, style: TextStyle, color: Color) {
    Row(Modifier.fillMaxWidth()) {
        Text(a, style = style, color = color, modifier = Modifier.weight(1f))
        Text(b, style = style, color = color, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        Text(c, style = style, color = color, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
    }
}

/** Explains why the headline is not today. */
@Composable
private fun ServiceNotice(plan: Plan, today: LocalDate) {
    if (plan.serviceDay == today) return
    val text = when {
        plan.todayType == DayType.CLOSED ->
            "No service today (" + (if (today.dayOfWeek == DayOfWeek.SATURDAY) "Shabbat" else "holiday") + ")"
        plan.missedLast != null ->
            "Last train today ${plan.missedLast.boardLabel()} · had to leave by ${plan.missedLast.leaveAt.hhmm()}"
        else -> return
    }
    Spacer(Modifier.height(24.dp))
    Text(text, style = MaterialTheme.typography.bodyMedium.merge(Tabular), color = dim())
}

@Composable
private fun Footer(plan: Plan) {
    val dayName = plan.serviceDay.dayOfWeek.getDisplayName(DateTextStyle.FULL, Locale.ENGLISH)
    Box(Modifier.fillMaxWidth()) {
        Text(
            "$dayName · ${timetableLabel(plan.dayType, plan.isDst)}",
            style = MaterialTheme.typography.bodySmall,
            color = dim(),
        )
    }
}

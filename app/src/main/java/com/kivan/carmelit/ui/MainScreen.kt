package com.kivan.carmelit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kivan.carmelit.Plan
import com.kivan.carmelit.Trip
import com.kivan.carmelit.timetableLabel
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private fun ZonedDateTime.hhmm(): String = format(HHMM)
private fun ZonedDateTime.hhmmRounded(): String = plusSeconds(30).format(HHMM)

private val Dim = Color(0xFF9A9A9A)

@Composable
fun MainScreen(
    plan: Plan,
    now: ZonedDateTime,
    goingHome: Boolean,
    onToggleDirection: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .safeDrawingPadding()
            .padding(24.dp),
    ) {
        val first = plan.trips.firstOrNull()
        if (first == null) {
            Text("No service found", style = MaterialTheme.typography.headlineMedium)
        } else {
            Headline(first, now)
            Spacer(Modifier.height(32.dp))
            Text(
                "${first.departure.hhmm()} train from ${plan.from.hebrew} → ~${first.arriveAt.hhmmRounded()} at ${plan.to.hebrew}",
                style = MaterialTheme.typography.bodyLarge,
            )
            if (plan.trips.size > 1) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "then " + plan.trips.drop(1).joinToString(", ") { "${it.departure.hhmm()} (leave ${it.leaveAt.hhmm()})" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Dim,
                )
            }
            Spacer(Modifier.height(24.dp))
            val dayName = plan.serviceDay.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
            Text(
                "$dayName · ${timetableLabel(plan.dayType, plan.isDst)}",
                style = MaterialTheme.typography.bodySmall,
                color = Dim,
            )
        }
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = goingHome,
                onClick = onToggleDirection,
                label = { Text("Going home") },
            )
            TextButton(onClick = onOpenSettings) { Text("Settings") }
        }
    }
}

@Composable
private fun Headline(first: Trip, now: ZonedDateTime) {
    val remaining = Duration.between(now, first.leaveAt)
    val minutes = Math.floorDiv(remaining.seconds, 60L)
    Text("Leave in", style = MaterialTheme.typography.labelLarge, color = Dim)
    when {
        minutes <= 0 -> Text("now", fontSize = 96.sp, fontWeight = FontWeight.Bold, lineHeight = 104.sp)
        minutes >= 100 -> Text(
            "${minutes / 60} h ${minutes % 60}",
            fontSize = 72.sp, fontWeight = FontWeight.Bold, lineHeight = 80.sp,
        )
        else -> Row(verticalAlignment = Alignment.Bottom) {
            Text("$minutes", fontSize = 128.sp, fontWeight = FontWeight.Bold, lineHeight = 136.sp)
            Text(" min", style = MaterialTheme.typography.headlineSmall, color = Dim,
                modifier = Modifier.padding(bottom = 20.dp))
        }
    }
    val dayPrefix = when (val days = Duration.between(now.toLocalDate().atStartOfDay(now.zone), first.leaveAt.toLocalDate().atStartOfDay(now.zone)).toDays()) {
        0L -> ""
        1L -> "tomorrow "
        else -> first.leaveAt.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " "
    }
    Text("${dayPrefix}at ${first.leaveAt.hhmm()}", style = MaterialTheme.typography.headlineSmall)
}

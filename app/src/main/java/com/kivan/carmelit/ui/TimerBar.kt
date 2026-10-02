package com.kivan.carmelit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kivan.carmelit.Run
import com.kivan.carmelit.Source
import com.kivan.carmelit.Step
import com.kivan.carmelit.ZONE
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

/** Seconds as "m:ss", with a sign for negatives. */
fun mmss(sec: Long): String = (if (sec < 0) "−" else "") + "%d:%02d".format(Math.abs(sec) / 60, Math.abs(sec) % 60)

private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** What one finished run measured, for the line shown until the next run starts. */
fun Run.summary(): String {
    fun d(a: Step, b: Step): Long? = this[a]?.let { x -> this[b]?.let { y -> x - y } }
    return listOfNotNull(
        when (source) { Source.GPS -> "GPS"; Source.MERGED -> "taps + GPS"; Source.MANUAL -> null },
        d(Step.PLATFORM, Step.LEAVE)?.let { "walk ${mmss(it)}" }
            ?: entranceAt?.let { e -> this[Step.LEAVE]?.let { "to station ${mmss(e - it)}" } },
        departure?.let { "train " + Instant.ofEpochSecond(it).atZone(ZONE).format(HHMM) },
        d(Step.DOORS_CLOSE, Step.PLATFORM)?.let { "${mmss(it)} to spare" },
        (this[Step.ARRIVE]?.let { a -> (this[Step.DOORS_CLOSE] ?: this[Step.DOORS_OPEN])?.let { a - it } })?.let { "ride ${mmss(it)}" },
        d(Step.DOOR, Step.ARRIVE)?.let { "last leg ${mmss(it)}" },
        doorToDoor?.let { "door to door ${mmss(it)}" },
        if (departure == null && (taps.drop(2).any { it != null } || surfaceAt != null)) "no train matched" else null,
    ).joinToString(" · ").ifEmpty { "nothing measured" }
}

/**
 * The six-tap trip timer. Idle: one button that starts a run in the current direction. Running:
 * one big button naming the next event, with Undo / Skip / Finish underneath.
 */
@Composable
fun TimerBar(
    active: Run?,
    last: Run?,
    goingHome: Boolean,
    onStart: () -> Unit,
    onChange: (Run) -> Unit,
    onFinish: () -> Unit,
    onDiscardActive: () -> Unit,
    onDiscardLast: () -> Unit,
) {
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth()) {
        if (active == null) {
            if (last != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Saved: ${last.summary()}", style = MaterialTheme.typography.bodySmall.merge(Tabular),
                        color = dim, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDiscardLast) { Text("Discard") }
                }
            }
            OutlinedButton(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                Text("Time this trip · ${Step.LEAVE.label(goingHome)}")
            }
        } else {
            var nowSec by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
            LaunchedEffect(Unit) {
                while (true) { nowSec = System.currentTimeMillis() / 1000; delay(1000) }
            }
            val previous = Step.entries.lastOrNull { it.ordinal < active.pos && active[it] != null }
            val since = previous?.let { active[it] } ?: nowSec
            Text(
                "TIMING · ${previous?.label(active.goingHome) ?: ""} ${mmss((nowSec - since).coerceAtLeast(0))} ago",
                style = eyebrow().merge(Tabular), color = dim,
            )
            Spacer(Modifier.height(8.dp))
            val next = active.next
            Button(
                onClick = { if (next != null) onChange(active.tap(System.currentTimeMillis() / 1000)) },
                modifier = Modifier.fillMaxWidth().height(64.dp),
            ) { Text(next?.label(active.goingHome) ?: "", style = MaterialTheme.typography.titleLarge) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (active.pos > 1) TextButton(onClick = { onChange(active.undo()) }) { Text("Undo") }
                else TextButton(onClick = onDiscardActive) { Text("Cancel") }
                TextButton(onClick = { onChange(active.skip()) }) { Text("Skip") }
                TextButton(onClick = onFinish) { Text("Finish") }
            }
        }
    }
}

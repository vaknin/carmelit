package com.kivan.carmelit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kivan.carmelit.Direction
import com.kivan.carmelit.Leg
import com.kivan.carmelit.R
import com.kivan.carmelit.Run
import com.kivan.carmelit.RunStats
import com.kivan.carmelit.Settings
import com.kivan.carmelit.Station
import com.kivan.carmelit.Summary
import com.kivan.carmelit.defaultOffsetSeconds
import com.kivan.carmelit.stopsFromOrigin

@Composable
fun SettingsScreen(initial: Settings, runs: List<Run>, onDone: (Settings) -> Unit) {
    var home by remember { mutableStateOf(initial.home) }
    var work by remember { mutableStateOf(initial.work) }
    var walkHome by remember { mutableStateOf(initial.walkHomeMin.toString()) }
    var walkWork by remember { mutableStateOf(initial.walkWorkMin.toString()) }
    var margin by remember { mutableStateOf(initial.marginMin.toString()) }
    var toWorkOffset by remember { mutableStateOf(initial.toWorkOffsetMin?.let(::fmt) ?: "") }
    var fromWorkOffset by remember { mutableStateOf(initial.fromWorkOffsetMin?.let(::fmt) ?: "") }
    // Only runs between the stations currently chosen count, per direction.
    val toWorkStats = remember(runs, home, work) { RunStats(runs, false, home, work) }
    val toHomeStats = remember(runs, home, work) { RunStats(runs, true, work, home) }
    var calibrationOpen by remember {
        mutableStateOf(
            initial.toWorkOffsetMin != null || initial.fromWorkOffsetMin != null ||
                toWorkStats.arriveOffset != null || toHomeStats.boardOffset != null,
        )
    }
    var picking by remember { mutableStateOf<StationSlot?>(null) }

    fun build() = Settings(
        home = home,
        work = work,
        walkHomeMin = walkHome.trim().toIntOrNull() ?: initial.walkHomeMin,
        walkWorkMin = walkWork.trim().toIntOrNull() ?: initial.walkWorkMin,
        marginMin = margin.trim().toIntOrNull() ?: 0,
        toWorkOffsetMin = toWorkOffset.trim().toDoubleOrNull(),
        fromWorkOffsetMin = fromWorkOffset.trim().toDoubleOrNull(),
    )

    val done = { onDone(build()) }
    BackHandler(onBack = done)

    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    val toWorkDir = Direction.between(home, work)
    val fromWorkDir = Direction.between(work, home)
    val toWorkStops = stopsFromOrigin(work, toWorkDir)
    val fromWorkStops = stopsFromOrigin(work, fromWorkDir)
    val toWorkDefault = defaultOffsetSeconds(work, toWorkDir) / 60.0
    val fromWorkDefault = defaultOffsetSeconds(work, fromWorkDir) / 60.0

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 8.dp, end = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = done) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
            }
            Spacer(Modifier.width(8.dp))
            Text("Settings", style = MaterialTheme.typography.headlineSmall)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 16.dp, bottom = 24.dp),
        ) {
            Section("ROUTE")
            StationRow("Home station", home) { picking = StationSlot.HOME }
            StationRow("Work station", work) { picking = StationSlot.WORK }
            Spacer(Modifier.height(4.dp))
            if (home == work) {
                Text("Home and work are the same station", color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            } else {
                val ride = (defaultOffsetSeconds(work, toWorkDir) - defaultOffsetSeconds(home, toWorkDir)) / 60.0
                val stops = Math.abs(work.index - home.index)
                Text(
                    "${if (toWorkDir == Direction.DOWN) "Down" else "Up"} the hill to work · " +
                        "$stops ${if (stops == 1) "stop" else "stops"} · ~${fmt(ride)} min ride",
                    style = MaterialTheme.typography.bodySmall, color = dim,
                )
            }

            Spacer(Modifier.height(32.dp))
            Section("WALKING")
            Spacer(Modifier.height(4.dp))
            MinutesField("Home → station", walkHome) { walkHome = it }
            // Planning for the slow end: a walk that usually works is not one that catches trains.
            toWorkStats.walk?.let { m -> Measured(m, "slow ${mmss(m.high)}", ceilMin(m.high).toString()) { walkHome = it } }
            Spacer(Modifier.height(12.dp))
            MinutesField("Work → station", walkWork) { walkWork = it }
            toHomeStats.walk?.let { m -> Measured(m, "slow ${mmss(m.high)}", ceilMin(m.high).toString()) { walkWork = it } }
            Spacer(Modifier.height(12.dp))
            MinutesField("Safety margin", margin, hint = "Leave this many minutes earlier", imeAction = ImeAction.Done) { margin = it }

            Spacer(Modifier.height(32.dp))
            val rotation by animateFloatAsState(if (calibrationOpen) 90f else 0f, label = "chevron")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { calibrationOpen = !calibrationOpen }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Section("CALIBRATION")
                Icon(
                    painterResource(R.drawable.ic_chevron_right),
                    contentDescription = if (calibrationOpen) "Collapse" else "Expand",
                    tint = dim,
                    modifier = Modifier.rotate(rotation),
                )
            }
            AnimatedVisibility(calibrationOpen) {
                Column {
                    Text(
                        "Minutes after the terminus departs until the train reaches ${work.english}. " +
                            "Leave empty to use the table estimate.",
                        style = MaterialTheme.typography.bodySmall, color = dim,
                    )
                    Spacer(Modifier.height(16.dp))
                    OffsetField("To work · $toWorkStops stops, default ${fmt(toWorkDefault)}", toWorkOffset) { toWorkOffset = it }
                    toWorkStats.arriveOffset?.let { m -> Measured(m, "latest ${mmss(m.high)}", fmt(halfMin(m.median))) { toWorkOffset = it } }
                    Spacer(Modifier.height(12.dp))
                    OffsetField("From work · $fromWorkStops stops, default ${fmt(fromWorkDefault)}", fromWorkOffset) { fromWorkOffset = it }
                    // The train you board: plan for its earliest arrival, rounded down.
                    // Not when work is a terminus: there the doors open before the departure.
                    toHomeStats.boardOffset?.takeIf { it.low > 0 }?.let { m ->
                        Measured(m, "earliest ${mmss(m.low)}", fmt(Math.floor(m.low / 30.0) / 2)) { fromWorkOffset = it }
                    }
                }
            }

            MeasuredReport("TO WORK", toWorkStats, Leg.of(build(), false))
            MeasuredReport("TO HOME", toHomeStats, Leg.of(build(), true))

            Spacer(Modifier.height(40.dp))
            val context = LocalContext.current
            val version = remember {
                runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
            }
            Text("v$version · timetable from carmelithaifa.co.il", style = MaterialTheme.typography.bodySmall, color = dim)
        }
    }

    picking?.let { slot ->
        StationDialog(
            title = if (slot == StationSlot.HOME) "Home station" else "Work station",
            current = if (slot == StationSlot.HOME) home else work,
            onPick = { if (slot == StationSlot.HOME) home = it else work = it; picking = null },
            onDismiss = { picking = null },
        )
    }
}

private enum class StationSlot { HOME, WORK }

private fun fmt(v: Double): String = if (v == Math.floor(v)) v.toInt().toString() else v.toString()

private fun ceilMin(sec: Long): Long = (sec + 59) / 60
private fun halfMin(sec: Long): Double = Math.round(sec / 30.0) / 2.0

/** One line under a field: what the trip timer measured, and a button that fills the field in. */
@Composable
private fun Measured(m: Summary, extreme: String, useValue: String, onUse: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Measured ${mmss(m.median)} typical · $extreme · ${m.n} ${if (m.n == 1) "run" else "runs"}",
            style = MaterialTheme.typography.bodySmall.merge(Tabular),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).padding(start = 16.dp),
        )
        TextButton(onClick = { onUse(useValue) }) { Text("Use $useValue") }
    }
}

/** A lead time in words; negative when boarding mid-line lets you leave after the terminus departure. */
private fun lead(sec: Long): String = "${mmss(Math.abs(sec))} ${if (sec < 0) "after" else "before"}"

/** What the timed trips of one direction say about the advice the app gives. */
@Composable
private fun MeasuredReport(title: String, st: RunStats, leg: Leg) {
    val lines = listOfNotNull(
        st.neededLead?.let {
            val app = leg.walk.seconds + leg.margin.seconds - leg.boardOffset.seconds
            "To just make the doors you had to leave ${lead(it.median)} the published departure " +
                "(worst: ${lead(it.high)}; ${it.n} ${if (it.n == 1) "run" else "runs"}). The app now says ${lead(app)}."
        },
        st.boardOffset?.let {
            if (it.median < 0) "Doors open ${mmss(-it.median)} before the published minute (latest ${mmss(-it.high)} before)."
            else "Doors open ${mmss(it.median)} after the published minute (earliest ${mmss(it.low)})."
        },
        st.closeOffset?.let { "Doors close ${mmss(it.median)} after the published minute (earliest ${mmss(it.low)})." },
        st.earlyBy?.let { "You left ${lead(it.median)} the app's time, with ${st.spare?.let { s -> mmss(s.median) } ?: "?"} to spare on the platform." },
        st.lastLeg?.let { "Station to door: ${mmss(it.median)} (slow ${mmss(it.high)})." },
    )
    if (lines.isEmpty()) return
    Spacer(Modifier.height(32.dp))
    Section("MEASURED · $title")
    for (line in lines) {
        Spacer(Modifier.height(8.dp))
        Text(line, style = MaterialTheme.typography.bodyMedium.merge(Tabular))
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = eyebrow(), color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun StationRow(label: String, value: Station, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(value.english, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(value.hebrew, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.width(4.dp))
        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StationDialog(title: String, current: Station, onPick: (Station) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.selectableGroup()) {
                // Top of the hill first, matching the line as you ride it down.
                for (s in Station.entries.reversed()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = s == current, role = Role.RadioButton) { onPick(s) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = s == current, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(s.hebrew, style = MaterialTheme.typography.bodyLarge)
                            Text(s.english, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun MinutesField(
    label: String,
    value: String,
    hint: String? = null,
    imeAction: ImeAction = ImeAction.Next,
    onChange: (String) -> Unit,
) {
    val invalid = value.isNotBlank() && value.trim().toIntOrNull() == null
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        suffix = { Text("min") },
        isError = invalid,
        supportingText = when {
            invalid -> ({ Text("Enter a whole number of minutes") })
            hint != null -> ({ Text(hint) })
            else -> null
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = imeAction),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun OffsetField(label: String, value: String, onChange: (String) -> Unit) {
    val invalid = value.isNotBlank() && value.trim().toDoubleOrNull() == null
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        suffix = { Text("min") },
        isError = invalid,
        supportingText = if (invalid) ({ Text("Enter minutes, e.g. 5 or 4.5") }) else null,
        trailingIcon = if (value.isNotEmpty()) ({ TextButton(onClick = { onChange("") }) { Text("Reset") } }) else null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
    )
}

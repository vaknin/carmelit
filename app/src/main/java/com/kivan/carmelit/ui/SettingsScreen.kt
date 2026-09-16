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
import com.kivan.carmelit.R
import com.kivan.carmelit.Settings
import com.kivan.carmelit.Station
import com.kivan.carmelit.defaultOffsetSeconds
import com.kivan.carmelit.stopsFromOrigin

@Composable
fun SettingsScreen(initial: Settings, onDone: (Settings) -> Unit) {
    var home by remember { mutableStateOf(initial.home) }
    var work by remember { mutableStateOf(initial.work) }
    var walkHome by remember { mutableStateOf(initial.walkHomeMin.toString()) }
    var walkWork by remember { mutableStateOf(initial.walkWorkMin.toString()) }
    var margin by remember { mutableStateOf(initial.marginMin.toString()) }
    var toWorkOffset by remember { mutableStateOf(initial.toWorkOffsetMin?.let(::fmt) ?: "") }
    var fromWorkOffset by remember { mutableStateOf(initial.fromWorkOffsetMin?.let(::fmt) ?: "") }
    var calibrationOpen by remember { mutableStateOf(initial.toWorkOffsetMin != null || initial.fromWorkOffsetMin != null) }
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
            Spacer(Modifier.height(12.dp))
            MinutesField("Work → station", walkWork) { walkWork = it }
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
                    Spacer(Modifier.height(12.dp))
                    OffsetField("From work · $fromWorkStops stops, default ${fmt(fromWorkDefault)}", fromWorkOffset) { fromWorkOffset = it }
                }
            }

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

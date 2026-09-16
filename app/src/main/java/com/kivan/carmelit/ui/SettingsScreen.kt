package com.kivan.carmelit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kivan.carmelit.Direction
import com.kivan.carmelit.Settings
import com.kivan.carmelit.Station
import com.kivan.carmelit.defaultOffsetSeconds

@Composable
fun SettingsScreen(initial: Settings, onDone: (Settings) -> Unit) {
    var home by remember { mutableStateOf(initial.home) }
    var work by remember { mutableStateOf(initial.work) }
    var walkHome by remember { mutableStateOf(initial.walkHomeMin.toString()) }
    var walkWork by remember { mutableStateOf(initial.walkWorkMin.toString()) }
    var margin by remember { mutableStateOf(initial.marginMin.toString()) }
    var toWorkOffset by remember { mutableStateOf(initial.toWorkOffsetMin?.let(::fmt) ?: "") }
    var fromWorkOffset by remember { mutableStateOf(initial.fromWorkOffsetMin?.let(::fmt) ?: "") }

    fun build() = Settings(
        home = home,
        work = work,
        walkHomeMin = walkHome.trim().toIntOrNull() ?: initial.walkHomeMin,
        walkWorkMin = walkWork.trim().toIntOrNull() ?: initial.walkWorkMin,
        marginMin = margin.trim().toIntOrNull() ?: 0,
        toWorkOffsetMin = toWorkOffset.trim().toDoubleOrNull(),
        fromWorkOffsetMin = fromWorkOffset.trim().toDoubleOrNull(),
    )

    BackHandler { onDone(build()) }

    val toWorkDefault = defaultOffsetSeconds(work, Direction.between(home, work)) / 60.0
    val fromWorkDefault = defaultOffsetSeconds(work, Direction.between(work, home)) / 60.0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))

        StationPicker("Home station", home) { home = it }
        Spacer(Modifier.height(12.dp))
        StationPicker("Work station", work) { work = it }
        if (home == work) {
            Spacer(Modifier.height(8.dp))
            Text("Home and work are the same station", color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(24.dp))

        NumberField("Walk home → station (min)", walkHome) { walkHome = it }
        Spacer(Modifier.height(12.dp))
        NumberField("Walk work → station (min)", walkWork) { walkWork = it }
        Spacer(Modifier.height(12.dp))
        NumberField("Safety margin (min)", margin) { margin = it }
        Spacer(Modifier.height(24.dp))

        Text("Advanced: minutes after the terminus departure until the train reaches ${work.english}",
            style = MaterialTheme.typography.bodySmall, color = Color(0xFF9A9A9A))
        Spacer(Modifier.height(12.dp))
        NumberField("To work (arrival estimate)", toWorkOffset, placeholder = fmt(toWorkDefault), decimal = true) { toWorkOffset = it }
        Spacer(Modifier.height(12.dp))
        NumberField("From work (boarding time)", fromWorkOffset, placeholder = fmt(fromWorkDefault), decimal = true) { fromWorkOffset = it }
        Spacer(Modifier.height(32.dp))

        Button(onClick = { onDone(build()) }, modifier = Modifier.fillMaxWidth()) { Text("Done") }
    }
}

private fun fmt(v: Double): String = if (v == Math.floor(v)) v.toInt().toString() else v.toString()

@Composable
private fun StationPicker(label: String, value: Station, onChange: (Station) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label: ${value.hebrew} · ${value.english}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (s in Station.entries.reversed()) {
                DropdownMenuItem(
                    text = { Text("${s.hebrew} · ${s.english}") },
                    onClick = { onChange(s); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    placeholder: String? = null,
    decimal: Boolean = false,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

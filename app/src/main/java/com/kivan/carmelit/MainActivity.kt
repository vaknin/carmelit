package com.kivan.carmelit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.kivan.carmelit.ui.CarmelitTheme
import com.kivan.carmelit.ui.MainScreen
import com.kivan.carmelit.ui.SettingsScreen
import kotlinx.coroutines.delay
import java.time.ZonedDateTime

class MainActivity : ComponentActivity() {
    /** Bumped on every resume so the clock refreshes the moment the app comes back. */
    private var resumeTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        val store = SettingsStore(this)
        setContent {
            CarmelitTheme { App(store, resumeTick) }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
    }
}

@Composable
private fun App(store: SettingsStore, resumeTick: Int) {
    var settings by remember { mutableStateOf(store.load()) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var goingHome by rememberSaveable { mutableStateOf(false) }
    var now by remember { mutableStateOf(ZonedDateTime.now(ZONE)) }

    LaunchedEffect(resumeTick) {
        while (true) {
            now = ZonedDateTime.now(ZONE)
            delay(10_000)
        }
    }

    if (showSettings) {
        SettingsScreen(settings) { updated ->
            settings = updated
            store.save(updated)
            showSettings = false
        }
    } else {
        val plan = remember(now, settings, goingHome) { nextTrips(now, settings, goingHome) }
        MainScreen(
            plan = plan,
            now = now,
            goingHome = goingHome,
            onSetGoingHome = { goingHome = it },
            onOpenSettings = { showSettings = true },
        )
    }
}

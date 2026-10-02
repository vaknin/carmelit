package com.kivan.carmelit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.kivan.carmelit.ui.CarmelitTheme
import com.kivan.carmelit.ui.MainScreen
import com.kivan.carmelit.ui.SettingsScreen
import com.kivan.carmelit.ui.TimerBar
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZonedDateTime

private const val STALE_RUN_SEC = 3 * 3600L

class MainActivity : ComponentActivity() {
    /** Bumped on every resume so the clock refreshes the moment the app comes back. */
    private var resumeTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        val store = SettingsStore(this)
        val runStore = RunStore(this)
        // Fences are lost on location toggles and Play Services updates; re-adding is cheap.
        Geofences.sync(this, store.load())
        setContent {
            CarmelitTheme { App(store, runStore, resumeTick) }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
    }
}

@Composable
private fun App(store: SettingsStore, runStore: RunStore, resumeTick: Int) {
    var settings by remember { mutableStateOf(store.load()) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var goingHome by rememberSaveable { mutableStateOf(false) }
    var runs by remember { mutableStateOf(runStore.all()) }
    var activeRun by remember { mutableStateOf(runStore.active()) }
    var lastRun by remember { mutableStateOf<Run?>(null) }
    val context = LocalContext.current
    val session by TripSession.active.collectAsState()
    val runsChanged by TripSession.runsChanged.collectAsState()

    // The recording service wrote the store: take its runs, the open run it merged into, and its result.
    LaunchedEffect(runsChanged) {
        if (runsChanged == 0) return@LaunchedEffect
        runs = runStore.all()
        activeRun = runStore.active()
        TripSession.lastSaved.value?.let { lastRun = it }
    }
    // A recording knows which way you are going.
    LaunchedEffect(session) { session?.let { goingHome = it.goingHome } }

    fun setActive(run: Run?) { activeRun = run; runStore.saveActive(run) }
    fun finish(run: Run) {
        val finished = run.finished(settings)
        // From the store, not memory: the service may have saved a run since.
        runs = runStore.all() + finished
        runStore.saveAll(runs)
        lastRun = finished
        setActive(null)
    }
    // A run nobody finished (phone died, forgot): thrown away, since its late taps cannot be
    // trusted, unless GPS filled it in; then what was recorded is saved.
    LaunchedEffect(resumeTick) {
        activeRun?.let { r ->
            val start = r[Step.LEAVE] ?: 0
            if (System.currentTimeMillis() / 1000 - start > STALE_RUN_SEC) {
                if (r.source == Source.MERGED) finish(r) else setActive(null)
            }
        }
    }

    var now by remember { mutableStateOf(ZonedDateTime.now(ZONE)) }

    LaunchedEffect(resumeTick) {
        while (true) {
            now = ZonedDateTime.now(ZONE)
            delay(10_000)
        }
    }

    if (showSettings) {
        SettingsScreen(settings, runs) { updated ->
            settings = updated
            store.save(updated)
            Geofences.sync(context, updated)
            showSettings = false
        }
    } else {
        // While recording this direction, trains count as catchable from when you really left.
        val leftAt = session?.takeIf { it.goingHome == goingHome }?.let { Instant.ofEpochSecond(it.leftAt).atZone(ZONE) }
        val plan = remember(now, settings, goingHome, leftAt) { nextTrips(now, settings, goingHome, leftAt = leftAt) }
        MainScreen(
            plan = plan,
            now = now,
            goingHome = goingHome,
            onSetGoingHome = { goingHome = it },
            onOpenSettings = { showSettings = true },
            timer = {
                TimerBar(
                    active = activeRun,
                    last = lastRun,
                    goingHome = goingHome,
                    onStart = {
                        val leg = Leg.of(settings, goingHome)
                        lastRun = null
                        setActive(Run(goingHome, leg.from, leg.to).tap(System.currentTimeMillis() / 1000))
                    },
                    onChange = { if (it.done) finish(it) else setActive(it) },
                    onFinish = { activeRun?.let(::finish) },
                    onDiscardActive = { setActive(null) },
                    onDiscardLast = {
                        runs = runs.filterNot { it === lastRun }
                        runStore.saveAll(runs)
                        lastRun = null
                    },
                )
            },
        )
    }
}

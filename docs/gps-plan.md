# Carmelit: making trip recording work

Research of 2026-10-12, for the ask of 2026-10-11 (P1): the app should notice by itself when
you leave home and when you get to work, and the same on the way back, so it learns how long
each trip takes door to door.

## In short

- **Why it doesn't work now.** The app only keeps a trip if GPS sees you at the Carmelit
  station. Leaving work, the station is a 5 minute walk away, and Android often reports
  "left work" 1 to 3 minutes late, so the recording starts when you are almost there or already
  underground. Then the whole trip is thrown away. Several smaller bugs make it fail silently.
- **The fix.** Count a trip from just two events: "left home" and "arrived at work" (and back).
  Both come from Android's own home/work zones, which work even when the app is closed. GPS
  during the walk becomes a bonus that adds the station times, not a condition.
- **Battery.** Watching the zones costs almost nothing. GPS runs only while you are on the
  way, about an hour a day: an estimated 1 to 3 % of the battery a day (to be measured).
- **My pick:** the zones plus a short GPS recording per trip, fix the bugs below, and add a
  small log of what the phone saw so every miss can be explained. Activity detection and Wi-Fi
  are kept as a second step, only if the log shows the zones still miss trips.

## What I looked at

The code (`Tracking.kt`, `TrackService.kt`, `Geo.kt`, `Runs.kt`, `MainActivity.kt`,
`ui/SettingsScreen.kt`), the notes in `PROGRESS.md` and `IDEA.md`, and Android's own
documentation. GPS recording was installed on 2026-10-02 but never seen working on the Pixel.
The task board was not readable from this research sandbox, so the bug list below comes from
reading the code, not from reported bugs.

## Why trips are lost today

Ordered by how much each one stops a trip from being recorded. "Sure" means it follows from the
code; "likely" means it depends on how the phone behaves and needs one real check.

| # | What goes wrong | Where | How sure |
|---|---|---|---|
| 1 | **A trip that GPS never sees at the station is thrown away**, even when leaving and arriving are both known. The station point comes from the national transit feed and was never checked against the real entrance; fixes worse than 50 m are ignored. | `Geo.kt` `judge`, `reconstruct` | sure |
| 2 | **"Left work" comes too late for the 5 minute walk.** The zone is 120 m wide (about 90 s of walking) and Android adds "usually less than 2 minutes, about 2–3 minutes" in the background, up to 6 after standing still (Android docs). The station is about 390 m from work, so the first GPS fix often comes after you went underground → item 1 drops the trip. | `Tracking.kt`, `Geo.kt` | likely |
| 3 | **Zone errors are ignored.** If location is switched off or Google's location accuracy is off, Android drops the zones and sends an error; the app discards it and nothing re-adds them until the app is opened. Adding the zones can also fail with only a log line. | `Tracking.kt` `GeofenceReceiver`, `sync` | sure |
| 4 | **The settings line says "Recording trips automatically"** whenever both doors and permissions are set, without checking that the zones were really added, location is on, or notifications are allowed. So a broken setup looks fine. | `SettingsScreen.kt` | sure |
| 5 | **A killed recording can't resume.** Android restarts the service from the background, which isn't allowed to start a location service; the restart fails and the half-recorded trip is left behind. | `TrackService.kt` `onStartCommand` | likely |
| 6 | **A leftover half-recorded trip hijacks the next one**: a new "left work" resumes the old file (and its direction) if it is less than 90 minutes old. | `TrackService.kt` | sure |
| 7 | **The zones are re-added every time the app opens**, which may reset whether Android thinks you are inside; and the zones only report leaving, never arriving, which reports say makes leaving less reliable. | `MainActivity.kt`, `Tracking.kt` | guess |
| 8 | **Permission dead end.** If "Allow all the time" was once declined, the Grant button does nothing; it should open the app's system settings. | `SettingsScreen.kt` | likely |
| 9 | **A missing trigger location drops the event**, and the trigger fix itself is never saved in the track. | `Tracking.kt`, `TrackService.kt` | likely |
| 10 | **Discard doesn't work for a trip saved by GPS**: it compares the run by object identity, and the GPS run is a different object than the one in the list. | `MainActivity.kt` `onDiscardLast` | sure |
| 11 | **Any walk out of home or work** (shop, dog) runs full-power GPS for up to walk + 20 min; once at the station, up to 90 min if work is never seen. | `Geo.kt`, `TrackService.kt` | sure |

Smaller: two tracks with the same leave second overwrite each other; the train is matched from
"surfaced − 60 s" when nothing better exists, which can be minutes late.

## Ways to notice leaving and arriving (Pixel 8)

| Way | Notices | Works with the app closed | Battery | Weak spot |
|---|---|---|---|---|
| **Home/work zones** (Play Services geofences) | leaving and arriving | yes, and may start the recording (an allowed exception) | very low: uses Wi-Fi and cell location, not GPS | late by "usually < 2 min", up to 6; needs Wi-Fi on (or Wi-Fi scanning) and Google location accuracy on |
| **GPS while on the way** (what the app does now) | the exact minute you pass the door and the station | only once started by something else | the main cost; about 1 h a day | can't start itself; nothing underground |
| **Activity detection** (walking / in a vehicle / still) | that you started moving, not where | yes, and may start the recording (an allowed exception) | very low (motion sensor) | needs the "Physical activity" permission; says nothing about place |
| **Wi-Fi** (home/work network) | arriving: the phone joins the network, often at the door | yes, Android can wake the app when a network appears | none | only "joined", not "left", reaches a closed app; needs Wi-Fi at work; joining can lag a scan cycle |
| Regular background location | everything, roughly | yes | low | Android allows only a few fixes per hour in the background: too coarse |

**Battery estimate** (no phone measurement exists yet): a phone with GPS on draws in the order of
a few hundred mW (receiver alone 10–25 mW today, the rest is the processor waking every 10 s).
The Pixel 8 battery holds about 17.6 Wh (4,575 mAh × 3.85 V). At 300 mW for 1 h a day that is
0.3 Wh ≈ 1.7 % a day; at 500 mW, ≈ 2.8 %. The zones and activity detection add almost nothing.
Check after a week in Settings → Battery → app usage.

**Pick: the zones decide the trip; GPS only adds detail.**

- Gain: a trip is kept whenever both ends are seen, even if GPS, the service or the station fix
  fails; no new permission; no new library.
- Loss: the door times are as late as the zone report (1–3 min) when GPS didn't run; corrected
  from the trigger location, as now.
- Second step, only if the log shows misses after two weeks of real trips: activity detection
  as a backup for "left", then Wi-Fi as a backup for "arrived".

## The plan

### Step 1: trip = left one door, arrived at the other (the core fix)

1. Register both zones for **leaving and arriving** (`ENTER | EXIT`, plus `DWELL` 1 min for
   arriving to skip walking past). Re-add them only when a door or a permission changed, not on
   every app start.
2. **Leaving** a door opens a trip (saved at once: direction, leave time from the trigger
   location moved back to the door, as now) and starts the GPS recording.
3. **Arriving** at the other door closes the trip, whether or not GPS is running. The door time
   is the earliest of: the first good GPS fix inside the zone, the zone's arrival report.
4. **Coming back** to the same door before reaching the other one: not a commute, dropped.
   No arrival within 3 h: dropped.
5. A trip that passed the boarding station gets the station times as now (entrance, surfacing,
   matched train). One that didn't is still saved as **door to door, "no Carmelit seen"**.
   Settings shows door-to-door per direction from all saved trips, with Carmelit trips apart.
6. Stop the GPS at arrival, after 90 min, or 10 min after the station with no new fix
   underground (then wait for the arrival report instead). Use balanced power (not full GPS)
   once the station is passed.
7. A leftover trip file is resumed only for a restart with no new event; a new "left" always
   starts a new trip. Don't rely on the service restarting itself: the arrival report closes the
   trip anyway.

### Step 2: no silent failures

1. Zone errors (location off, location accuracy off): re-add the zones when location comes back
   (listen for the location setting changing) and remember the last error.
2. Before adding zones, check the location settings (Play Services settings check) and that
   Wi-Fi or Wi-Fi scanning is on; offer the one-tap system dialog if not.
3. The Settings line tells the truth: zones added and when, location on, notifications allowed,
   "last trip seen: … ". The Grant button opens the app's system settings when Android no longer
   shows its own prompt.
4. Ask once to turn off "Pause app activity if unused", so Android never removes the permission
   from an app you rarely open (it works on its own now).
5. A small **trip log** in the phone's built-in SQLite (no new library): every zone report,
   service start or refusal, error and verdict, with time and accuracy. Kept 60 days. It is what
   explains a missed trip, and what decides whether step 4 is needed.

### Step 3: smaller bugs

- Discard compares by value, not identity; the "Saved" line doesn't come back after a restart.
- Missing trigger location: use the zone centre and the event time; save the trigger fix.
- Unique track file names.

### Step 4 (only if the log shows misses): backups

- Activity detection: "started walking" or "in a vehicle" while the last known place is inside
  a door's zone, and no trip open → take one fix; outside the zone → open the trip.
- Wi-Fi: learn the network when "Set to here" is pressed; joining it closes an open trip.

## How it gets tested

- **Unit tests** (JVM, as now): the trip rules on recorded event sequences: left → arrived;
  left → back home; left → nothing for 3 h; arrival with no GPS; GPS lost underground; a stale
  file plus a new "left"; each bug in step 3 gets a test that would have caught it.
- **On the Pixel without walking**: a debug-only fake route (Play Services mock mode, the app
  allowed as mock location app over adb) walks the phone out of the home zone, to the station
  and into the work zone. Check the trip log, the notification and the saved trip with
  screenshots. Whether zone reports react to the fake route must be confirmed on the phone
  first; if not, the same route is fed to the recording directly and the zone part waits for a
  real walk.
- **Real trips**: the owner just commutes; after a week the log and Settings show which trips
  were seen, how late each zone report was, and the battery used. Two weeks of no misses = done.

## What it does not cover

Trips to places other than home and work; a different boarding station is recorded as "no
Carmelit seen" until station choice follows the route. Doors open / closed on the platform stay
taps: GPS can't see underground.

## Sources

- Android geofencing (latency, radius, when zones are dropped, Wi-Fi):
  https://developer.android.com/develop/sensors-and-location/location/geofencing
- Starting a location service from the background (zone and activity reports are allowed
  exceptions; "Allow all the time" needed): https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- Activity detection: https://developer.android.com/develop/sensors-and-location/location/transitions
- Wi-Fi waking a closed app (`registerNetworkCallback` with a PendingIntent, sent "when a
  network is available"): https://developer.android.com/reference/android/net/ConnectivityManager
- Android 17 (the app's target) changes nothing for location or geofences:
  https://developer.android.com/about/versions/17/behavior-changes-17
- GPS receiver power (10–25 mW): u-blox low-power tracking white paper,
  https://content.u-blox.com/sites/default/files/Low-power-tracking-applications_WhitePaper_UBX-21014570(1).pdf

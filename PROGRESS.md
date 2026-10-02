# Carmelit "When to leave" — progress log

Spec: `IDEA.md`. Reference calendar: `hebcal.js`. Build/run: `README.md`.
Keep this file current: one dated entry per work session, plus the living sections below.

## Session 2026-10-02 — GPS trip recording, earlier trains (v0.3)

Idea 3489a227: forgetting "Leaving" (or pressing it midway) loses the trip, and once on the way
the app hid the train about to be caught. Design in IDEA.md "C. GPS recording".

- **Earlier trains**: `Plan.earlier` (≤ 2 trains past their leave time but not yet gone from
  your station, 1 min grace) shown dimmed above the headline. `nextTrips(leftAt=)` judges
  catchability from a recorded leave time; the headline then reads "Board in".
- **GPS**: `Geo.kt`, `Tracking.kt`, `TrackService.kt`; `Run.source/entranceAt/surfaceAt`
  (old 7-field run lines still load); `merge`/`record`; Settings GPS section + Trips report.
  New dependency play-services-location 21.4.0; permissions fine/background location,
  location FGS, notifications, boot. Station points from the GTFS feed of 2026-10-01.
- 55 JVM tests pass (16 new: `GeoTest`, planner earlier/leftAt, merge/record).
- Installed on the Pixel 8 (16:20 Friday, no service): launches, no crash in logcat. **Not yet
  verified on device**: the earlier block and "Board in" headline on screen, the permission
  flow, a geofence firing, the service recording and saving. Needs the owner: turn on Settings →
  GPS, set both doors on site, grant "Allow all the time", then ride once each way.
  Tracks are kept in `files/tracks/` (`adb shell run-as com.kivan.carmelit ls files/tracks`).
- Unverified assumptions to check on the first rides: geofence exit latency; that the GTFS
  station points are within 80 m of the entrances used; surfacing within ~1 min of the doors.

## Session 2026-09-17 (later) — closing trains, Yom Kippur dropped, six-tap trip timer

- Re-checked the official page (unchanged) and compared with the Ministry of Transport GTFS feed;
  findings in IDEA.md "Accuracy ideas". Departure minutes match; the feed's per-station times are
  filler, so stop offsets can only come from our own measurements.
- **Closing-time trains added** (24:00, 15:00 Friday, 14:00 erev Pesach) — see Decisions.
- **Yom Kippur no longer modelled** (owner's call): `DayType.EREV_YK` / `YOM_KIPPUR` removed; YK
  is an ordinary rest day. On 2026-09-20 the app will wrongly show trains 13:00–15:00 and on
  09-21 a 20:00 start instead of 21:00. Accepted.
- **Trip timer built** (`Runs.kt`, `ui/TimerBar.kt`, measured lines + report in Settings). Six
  taps in both directions (incl. "doors open" at the terminus, since a later fix), Undo/Skip/Finish, run
  survives process death, Discard after saving. Algorithm and rationale: IDEA.md A2/A3.
  `Leg` extracted from `nextTrips` so the planner and the run matcher share the offsets.
- 39 JVM tests pass (10 new in `RunsTest`). Installed on the Pixel 8 and screenshot-verified:
  idle button, running state, run surviving a force-stop, "Saved: …" line with Discard, and the
  Measured line + report in Settings. The fake test run showed the matcher accepting doors that
  "closed" 4 min before a departure; the window is now −2…+6 min. A second pass with fake
  runs then verified on device: the to-home flow incl. "Doors open" and Undo back to it, a run
  surviving screen lock + force-stop, a matched train (16:12, doors +3:19 → stored departure and
  advice correct), "no train matched" for taps between two trains' windows, early Finish, the
  Calibration measured line ("Use 3"), and the report wording ("… after the published
  departure" when the lead is negative). All fake runs discarded; run store left empty.
  A third pass ran the full to-work flow (platform → doors closed → arrived → at work, "Doors
  open" hidden, auto-save on the last tap, matched the 16:24 with doors −1:43, advice 16:02) and
  pressed **Use** (field filled, saved to prefs; then restored to 22). A fourth pass, after un-hiding "Doors
  open" at the terminus (owner asked why it was hidden; the reason was an untested assumption):
  all six labels seen going to work, auto-save, 16:24 matched, "Doors open 1:21 after" line in
  the report. A forgotten run is now discarded after 3 h instead of saved (owner's call);
  verified by planting a 4 h old active run (gone, nothing saved) and a 10 min old one (kept).

## Session 2026-09-17 — two "To home" bugs found in the field

Standing at HaNevi'im at ~08:51 in "To home" mode, the app said *Leave in 7 min · at 08:58* over a
card reading `09:00 → ~09:08`, and `leave 09:10 · train 09:12` in the Later list. A 2 min gap
between leaving and the train, with a 5 min walk configured. Two independent bugs:

1. **The screen showed the wrong station's time.** The trip card and the Later list printed
   `Trip.departure` (the minute the train leaves the *far* terminus, Ir Tahtit) while the leave
   time is derived from `Trip.boardAt` (departure + the stop offset). The 5 min walk *was* being
   applied, to an 09:03 that was never shown. Invisible in the morning, where home is the terminus
   and `boardAt == departure`. Fixed: `Trip.boardLabel()` in `MainScreen.kt` prints the time at
   *your* station — exact at a terminus, `~HH:MM` elsewhere — and is used by the trip card, the
   Later list and the "last train today" notice. The Later column header is now `board`, not
   `train`, so the row reads leave → board → arrive.
2. **The 2-stop offset was too short.** Observed: the 08:48 and 09:00 trains from Ir Tahtit were at
   HaNevi'im ~08:53 and ~09:04, ~5 min rather than the estimated 3. Whether the ride is slower than
   the 8 min end-to-end figure or the cars just leave the terminus late, the fix is the same.
   `OFFSET_SECONDS` is now `0, 210, 300, 390, 480, 570` (a ~2 min terminus lag + 1.5 min/stop,
   exact at the one measured point), replacing `0, 90, 180, 300, 390, 480`.

The timetable itself was never wrong: the 12 min headway and the window boundaries matched what
was on the platform. 29 JVM tests pass with the updated expectations (morning arrival at
HaNevi'im 08:12 → 08:18:30; evening boarding 17:00 → 17:05).

## Status (2026-09-16)

**v0.2: UI redesign built and installed on the Pixel 8** (design in IDEA.md "UI design (v0.2)").
Everything in IDEA.md except the home-screen widget is implemented. 30 JVM unit tests pass.

| Area | State |
|---|---|
| Build scaffold (AGP 9.4, Kotlin 2.4.10, Compose BOM 2026.08.00, Gradle 9.6.1) | done, mirrors `../Pronounce` |
| Hebrew calendar port (`HebrewCalendar.kt`) | done, golden-tested against `hebcal.js` for Hebrew years 5760–5800 (615 dates) |
| Timetable (`Timetable.kt`) | done, all 7 day types |
| Day classifier (`DayClassifier.kt`) | done, incl. DST via `Asia/Jerusalem` zone rules |
| Planner (`Planner.kt`) | done, scans 8 days ahead, both directions |
| Settings + SharedPreferences (`Settings.kt`) | done, verified to persist across restart |
| Theme (`ui/Theme.kt`) | done: black OLED scheme, one red accent, tabular numerals |
| Main screen (`ui/MainScreen.kt`) | done, 10 s tick, refreshes on resume; v0.2: segmented direction, urgency colour, countdown bar, trip card, "Later" table with day dividers, service notice |
| Settings screen (`ui/SettingsScreen.kt`) | done; v0.2: sections, station dialogs, "min" fields with validation, collapsible calibration with Reset |
| Direction switch ("To work / To home") | done |
| Home-screen widget | **not started** (nice-to-have) |
| Calibration of the HaNevi'im offset from a real ride | evening (2 stops up = 5 min) measured 2026-09-17; morning still extrapolated |

## Decisions made (and why)

- **Closing-time train included** (changed 2026-09-17; it was excluded before). Windows are closed
  at the end, so the last trains are 24:00 (weekday, motzei), 15:00 (Friday), 14:00 (erev Pesach). The site does not mention them, but the Ministry of Transport
  GTFS feed of 2026-09-16 lists 15:00, 13:00 and the 24:00 (as 00:00 on Sun–Fri); 14:00 erev
  Pesach is by analogy. `departures()` now returns minutes of the service day (1440 = 24:00) since
  `LocalTime` cannot hold 24:00. Still unconfirmed by eye: check once that a 24:00 train leaves.
- **DST from the real zone rules**, evaluated at noon of the service day, not the site's
  "April–October" wording. The two differ in late March and late October; the Carmelit's own
  practice there is unknown.
- **Two calibration fields instead of one** (deviation from IDEA.md). Morning arrival at
  HaNevi'im is 3 stops from Merkaz HaCarmel (≈5 min); evening boarding at HaNevi'im is 2 stops
  from Ir Tahtit (≈3 min). One number cannot serve both. Both fields are optional and show the
  table default in their label. Home station always uses the table.
- **Rest-day rules** as in IDEA.md: rest day = Shabbat, chag, or Yom Kippur. Rest day followed by
  a rest day → closed; otherwise motzei evening service. The erev Pesach window wins even on
  a Friday. Chol hamoed, Purim, Independence Day are ordinary days.
- **SharedPreferences, not DataStore**; no navigation library; no dependencies beyond core-ktx,
  activity-compose, Compose UI/Material3 and JUnit. Zero permissions in the manifest.
- **Offsets in seconds** (0, 90, 180, 300, 390, 480) so 1.5 / 6.5 min are exact; the UI floors
  the remaining time to whole minutes, which errs on the early side.
- **`Plan.todayType` / `Plan.missedLast`** (v0.2) exist only so the screen can say *why* the
  headline jumped to another day ("Last train today 23:45 · had to leave by 23:23" or "No
  service today (Shabbat)"). The owner hit this at 23:27: the 22 min walk makes the 23:45
  uncatchable after 23:23 and the 24:00 closing train is excluded (decision 1 above).
- **No icon library**: `material3` 1.4 no longer pulls in `material-icons-core`, so the three
  icons are vector drawables in `res/drawable`. Dependency list unchanged.

## Verified on device (2026-09-16, Wed 23:14–23:16 IDT)

- Headline "Leave in 7 min · at 23:23" for the 23:45 train, ~23:50 at HaNevi'im; "then" list rolls
  over to Thursday 06:00 / 06:15 / 06:30; the number ticks down.
- Going home: 23:30 train boards at HaNevi'im 23:33, leave 23:28 (5 min walk), arrive 23:38.
  *(Superseded 2026-09-17: the same train now boards ~23:35, leave 23:30, arrive ~23:39:30,
  and the card shows the boarding time instead of the 23:30 terminus departure.)*
- Settings: margin 3 saved via back-press, app force-stopped and relaunched, leave time moved
  23:23 → 23:20. Margin reset to 0 afterwards.
- No crashes in logcat.

Bugs found only on the device, all fixed in commit `5a2b57d`:
1. All primary text was black on black (no `Surface`, so `LocalContentColor` was black).
2. Hebrew station names reordered the English/time text (fixed with bidi isolates U+2068/U+2069).
3. Calibration defaults invisible (Material 3 hides placeholders behind labels; default moved into the label).

## Edge cases covered by tests (`app/src/test`)

Rosh Hashana on Sat+Sun 2026 (Sat closed, Sun motzei) · YK on Mon 2026, Thu 2025, Shabbat 2028 ·
erev YK on a Friday 2028 · chag on Thursday (Pesach 2027, Sukkot 2028: motzei, then Friday hours) ·
chag on Friday (Shavuot 2026 and 2027: closed) · Shabbat before a Sunday chag (2029: closed) ·
erev Pesach on a Friday 2029 · two-day closure Thu+Fri 2028 · DST boundaries 2026 ·
late-night rollover · Friday afternoon → motzei Shabbat (summer 20:00, winter 19:00) ·
closed Shabbat → Sunday evening · margin · going home · both offset overrides.

## Open questions / next steps

1. **Time a morning ride.** The evening leg is now measured (2 stops up = 5 min, 2026-09-17), but
   the morning arrival at HaNevi'im (3 stops down, ~6.5 min) is still extrapolated from it. Note the
   minute the doors open after a known Merkaz HaCarmel departure and, if it differs, enter it in
   Settings → Calibration → "To work".
2. ~~Does the closing-time train run?~~ — included since 2026-09-17 on GTFS evidence; confirm on
   the platform once. (Yom Kippur is no longer modelled at all.)
3. **Late March / late October motzei start.** Check what the Carmelit does in the gap between
   real DST and the site's "April–October"; adjust `isDst` if it follows the calendar months.
4. **Widget** ("Leave in N min" on the home screen). Would need Glance or a RemoteViews
   AppWidgetProvider plus a periodic update; deliberately deferred.
5. ~~Label "then" trips on a different day~~ — done in v0.2 (day divider in the "Later" table).

## Working with the phone

Wireless adb, no cable. The phone must have Wireless debugging on and be on the home Wi-Fi
("Otot"); the IP was 192.168.1.245, the port changes per session. Pair once per phone with the
one-time code shown on the phone, then connect:

```
adb pair 192.168.1.245:<pair-port> <code>
adb connect 192.168.1.245:<port>
./gradlew installDebug && adb shell am start -n com.kivan.carmelit/.MainActivity
adb exec-out screencap -p > shot.png      # eyeball the UI; green builds are not enough
```

## Session log

- **2026-09-16** — Wrote IDEA.md and hebcal.js (owner). Then: scaffolded project, ported the
  calendar, wrote timetable/classifier/planner with 27 tests, built the Compose UI, paired the
  Pixel 8 over Wi-Fi, installed, found and fixed three rendering bugs, verified persistence.
  Commits `b8b7e6b` → `5a2b57d`.
- **2026-09-16 (evening)** — v0.2 UI redesign after the owner asked for a better UX/UI: theme
  file, rewritten main and settings screens, `todayType`/`missedLast` on `Plan` with 3 new
  tests, vector icons, version 0.2. Design captured in IDEA.md first.

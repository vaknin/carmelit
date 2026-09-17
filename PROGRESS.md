# Carmelit "When to leave" — progress log

Spec: `IDEA.md`. Reference calendar: `hebcal.js`. Build/run: `README.md`.
Keep this file current: one dated entry per work session, plus the living sections below.

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

- **Closing-time train excluded.** Windows are half-open `[start, end)`, so the last guaranteed
  trains are 23:45 (weekday), 14:48 (Friday), 12:48 (erev YK), 13:48 (erev Pesach). The site
  does not say whether the 15:00 / 24:00 train runs. Change `Timetable.kt` if it does.
- **DST from the real zone rules**, evaluated at noon of the service day, not the site's
  "April–October" wording. The two differ in late March and late October; the Carmelit's own
  practice there is unknown.
- **Two calibration fields instead of one** (deviation from IDEA.md). Morning arrival at
  HaNevi'im is 3 stops from Merkaz HaCarmel (≈5 min); evening boarding at HaNevi'im is 2 stops
  from Ir Tahtit (≈3 min). One number cannot serve both. Both fields are optional and show the
  table default in their label. Home station always uses the table.
- **Rest-day rules** as in IDEA.md: rest day = Shabbat, chag, or Yom Kippur. Rest day followed by
  a rest day → closed; otherwise motzei evening service. Erev YK / erev Pesach windows win even on
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
2. **Does the closing-time train run?** If the 15:00 Friday or 24:00 train exists, make the
   windows closed at the end in `Timetable.kt` (one-line change) and update `TimetableTest`.
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

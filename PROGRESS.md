# Carmelit "When to leave" — progress log

Spec: `IDEA.md`. Reference calendar: `hebcal.js`. Build/run: `README.md`.
Keep this file current: one dated entry per work session, plus the living sections below.

## Status (2026-09-16)

**v0.1 is complete, installed on the Pixel 8 and verified by screenshot.** Everything in
IDEA.md except the home-screen widget is implemented. 27 JVM unit tests pass.

| Area | State |
|---|---|
| Build scaffold (AGP 9.4, Kotlin 2.4.10, Compose BOM 2026.08.00, Gradle 9.6.1) | done, mirrors `../Pronounce` |
| Hebrew calendar port (`HebrewCalendar.kt`) | done, golden-tested against `hebcal.js` for Hebrew years 5760–5800 (615 dates) |
| Timetable (`Timetable.kt`) | done, all 7 day types |
| Day classifier (`DayClassifier.kt`) | done, incl. DST via `Asia/Jerusalem` zone rules |
| Planner (`Planner.kt`) | done, scans 8 days ahead, both directions |
| Settings + SharedPreferences (`Settings.kt`) | done, verified to persist across restart |
| Main screen (`ui/MainScreen.kt`) | done, 10 s tick, refreshes on resume |
| Settings screen (`ui/SettingsScreen.kt`) | done |
| "Going home" swap chip | done |
| Home-screen widget | **not started** (nice-to-have) |
| Calibration of the ~5 min HaNevi'im offset from a real ride | **not done** (needs a ride) |

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

## Verified on device (2026-09-16, Wed 23:14–23:16 IDT)

- Headline "Leave in 7 min · at 23:23" for the 23:45 train, ~23:50 at HaNevi'im; "then" list rolls
  over to Thursday 06:00 / 06:15 / 06:30; the number ticks down.
- Going home: 23:30 train boards at HaNevi'im 23:33, leave 23:28 (5 min walk), arrive 23:38.
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

1. **Ride once and calibrate.** Note the minute the train actually reaches HaNevi'im after a
   known terminus departure; enter it in Settings → "To work". Same for the evening boarding time.
2. **Does the closing-time train run?** If the 15:00 Friday or 24:00 train exists, make the
   windows closed at the end in `Timetable.kt` (one-line change) and update `TimetableTest`.
3. **Late March / late October motzei start.** Check what the Carmelit does in the gap between
   real DST and the site's "April–October"; adjust `isDst` if it follows the calendar months.
4. **Widget** ("Leave in N min" on the home screen). Would need Glance or a RemoteViews
   AppWidgetProvider plus a periodic update; deliberately deferred.
5. Small polish ideas, none blocking: hide "then" trips that are on a different day than the
   headline, or label them; a "Tomorrow" prefix already appears on the headline.

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

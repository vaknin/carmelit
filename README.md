# Carmelit — "When to leave"

Minimal offline Android app: when do I need to leave the house to catch the next Carmelit?
See `IDEA.md` for the spec (timetable, holiday rules, station timing) and `hebcal.js` for the
reference Hebrew-calendar implementation that `HebrewCalendar.kt` ports.

## Build (CLI only, no Android Studio)

```
./gradlew testDebugUnitTest      # schedule engine tests (pure JVM)
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug && adb shell am start -n com.kivan.carmelit/.MainActivity
```

## Layout

- `Stations.kt` — the six stations, direction, per-stop timing table.
- `HebrewCalendar.kt` — holiday dates (golden-tested against `hebcal.js`).
- `Timetable.kt` — terminus departures per day type.
- `DayClassifier.kt` — which timetable a date runs (Shabbat, chag, erev, motzei, DST).
- `Planner.kt` — next catchable trains given walk time, margin and station offsets.
- `Settings.kt` — settings data class + SharedPreferences store.
- `ui/` — the one screen and the settings screen.

# Carmelit "When to leave" — idea

Minimal Android app (Kotlin, no backend) that answers one question:
**"When do I need to leave the house to catch the next Carmelit?"**

Moovit / Egged apps and the official site are useless for this. The Carmelit
runs on a fixed, published, deterministic timetable, so the app needs no live
data at all: it is pure clock arithmetic + the official schedule + the Jewish
holiday calendar.

## The one screen

```
  Leave in 14 min          <- huge, the only thing you really look at
  at 08:10

  08:32  train from Merkaz HaCarmel  ->  ~08:37 at HaNevi'im
  then 08:44 (leave 08:22), 08:56 (leave 08:34), ...

  Wednesday · weekday timetable · every 12 min 07:00-22:00
```

My commute: board at **מרכז הכרמל (Carmel Center)**, the top terminus, ride down and get off at
**הנביאים (HaNevi'im)**, the 4th stop (3 stops travelled). Boarding at a terminus means the
published departure time is exact for me; only the arrival at HaNevi'im is an estimate (~6.5 min).

Settings (one small screen, saved locally):
- Home station, work station (direction follows from these). Defaults: Carmel Center -> HaNevi'im.
- Walk time home -> station (e.g. 22 min). Optional walk time work -> station for the way back.
- Safety margin in minutes (default 0; stays "exact").
- Advanced: "minutes from terminus departure until the train reaches my station"
  (pre-filled estimate, user can calibrate after riding once — see below).

Nice-to-have, later: a home-screen widget showing "Leave in N min", and a
"going home" swap button. Nothing else. No maps, no accounts, no network.

## Official timetable (source of truth)

Source: https://www.carmelithaifa.co.il/כרמלית-חיפה/שעות-פעילות-וכרטיסים/ (checked 2026-09-16)

Trains leave **both termini (עיר תחתית and מרכז הכרמל) at the same minute**.

| Day type | Windows (departures from each terminus) |
|---|---|
| Sun–Thu | 06:00–07:00 every 15 min (06:00, 06:15, 06:30, 06:45); 07:00–22:00 every 12 min (07:00, 07:12, 07:24 …); 22:00–24:00 every 15 min (22:00, 22:15 …) |
| Friday & erev chag | 06:00–07:00 every 15 min; 07:00–15:00 every 12 min |
| Motzei Shabbat & motzei chag | summer time (DST): 20:00–24:00 every 15 min; winter time: 19:00–24:00 every 15 min |
| ~~Erev Yom Kippur 06:00–13:00, Yom Kippur night 21:00–24:00~~ | **not modelled** (owner, 2026-09-17: once a year, don't care). The app treats YK as a plain chag: Friday hours the day before, motzei service that night |
| Erev Pesach | 06:00–14:00 |

Notes:
- Every boundary lands exactly on a departure (07:00 + 12·75 = 22:00, 22:00 + 15·8 = 24:00, 07:00 + 12·40 = 15:00).
  The site does not say whether the closing-time train (15:00 / 24:00) runs, but the GTFS feed lists it (see "Accuracy ideas"), so the app includes it since 2026-09-17.
- The site says both "April–October / November–March" and "שעון קיץ / שעון חורף" for the Shabbat start. Use real Israeli DST (Asia/Jerusalem offset +3 = summer); note the mismatch in late March / late October.
- Ticket prices are only published as an image; irrelevant for the app.

## Day classification rules

A day is a **rest day** if it is Shabbat or one of: Rosh Hashana (2 days), Yom Kippur,
Sukkot day 1, Shmini Atzeret/Simchat Torah, Pesach day 1, Pesach day 7, Shavuot.
(Other holidays — Purim, chol hamoed, Independence Day, etc. — are normal days, like buses.)

- Rest day whose next day is also a rest day → **no service at all** (e.g. Rosh Hashana I 2026 = Saturday, day II = Sunday → Saturday closed).
- Rest day whose next day is normal → evening service only (motzei schedule).
- Yom Kippur → no special case since 2026-09-17; it is an ordinary rest day.
- Day before a rest day (erev chag, or a plain Friday) → Friday hours. Erev Pesach has its own shorter window.
- Otherwise → weekday.

`hebcal.js` in this folder is a tested reference implementation of the Hebrew-calendar
math (Reingold & Dershowitz algorithm, ~40 lines, no library) producing the holiday
dates; port it to Kotlin. Verified output: Rosh Hashana 2026-09-12, Yom Kippur 2026-09-21,
Pesach 2027-04-22, Shavuot 2027-06-11, Rosh Hashana 2027-10-02.

## Stations and intermediate timing

Stations bottom → top: עיר תחתית (Ir Tahtit) · הדר-עירייה (Hadar) · הנביאים (HaNevi'im) ·
מסדה (Masada) · בני ציון (Bnei Zion) · מרכז הכרמל (Merkaz HaCarmel).

Only terminus departures are published. End-to-end trip is ~8 min (1.8 km); the passing
loop is midway between HaNevi'im and Masada, so the two trains cross at ~minute 4.
Minutes after the published terminus departure until the train reaches a station, by number of
stops travelled:

| stops from terminus | 0 | 1 | 2 | 3 | 4 | 5 |
|---|---|---|---|---|---|---|
| minutes | 0 | 3.5 | 5 | 6.5 | 8 | 9.5 |

Same table in both directions. **Measured 2026-09-17** standing at HaNevi'im going up: the 08:48
and 09:00 departures from Ir Tahtit reached the platform at ~08:53 and ~09:04, i.e. ~5 min for
2 stops, not the 3 min the first estimate gave. So the table is now a ~2 min lag between the
published minute and the train actually moving, plus 1.5 min per stop; the 12 min headway was
confirmed at the same time. Index 0 stays 0: at a terminus the published minute is when you have
to be on the platform. These are still the only non-exact numbers in the app, hence the
calibration fields in settings. My morning trip (Carmel Center -> HaNevi'im, 3 stops) is ~6.5 min
and remains an extrapolation until a morning ride is timed.

## Core computation

```
now            = current wall-clock time in Asia/Jerusalem (never device zone)
departures(d)  = terminus departure minutes for day d from the table above
stationTime    = departure + offset(homeStation, direction)
leaveAt        = stationTime - walk - margin
answer         = first train with leaveAt >= now
                 (also look at tomorrow / day after, for late night, Shabbat, holidays)
arrival        = departure + offset(workStation, direction)
```

Refresh every ~10 s while visible. Everything is offline and deterministic.

## Tech

Kotlin, single Activity, Jetpack Compose, `java.time` with `ZoneId.of("Asia/Jerusalem")`
(gives DST for free), DataStore/SharedPreferences for settings. No dependencies beyond
that. Target: just my phone.

## UI design (v0.2)

Decided 2026-09-16: black OLED background, one accent (Carmelit red `#E5322D`) reserved for
urgency and the selected direction; English chrome, Hebrew station names with the English
transliteration underneath (no RTL layout). No new dependencies: the two icons are vector
drawables. Tabular figures everywhere a number ticks or sits in a column.

| Token | Value | Used for |
|---|---|---|
| background / surface | `#000000` | screens |
| onSurface | `#F2F2F2` | primary text |
| surfaceVariant / onSurfaceVariant | `#141414` / `#9A9A9A` | trip card, dialog / dim text |
| outlineVariant | `#262626` | dividers, segmented-button border |
| primary | `#E5322D` | ≤ 3 min countdown, "now", selected direction, countdown bar |
| error | `#FF6E6E` | validation text |

### Main screen

```
[ To work │ To home ]                 ⚙     segmented direction control + settings
LEAVE IN
14                                          ~120 sp, red when ≤ 3 min; "now" under 60 s; "6 h 11" ≥ 100 min
at 08:10                                    "tomorrow at 05:38" when not today
████████████░░░░░░░░░░░░░░░░░░░░░░░░░░      2 dp bar draining over one headway
┌──────────────────────────────────────┐
│ 08:32                        ~08:37  │    time at MY station → estimated arrival
                                             ("~08:53" when my station is not the terminus)
│ מרכז הכרמל                  הנביאים │    each Text is pure Hebrew (no bidi tricks)
│ Merkaz HaCarmel            HaNevi'im │
└──────────────────────────────────────┘
LATER                                       columns: leave · board · arrive
leave 08:22      08:44 board     ~08:49
leave 08:34      08:56 board     ~09:01
TOMORROW                                    day divider only when the day changes
leave 05:38      06:00 train     ~06:05
Last train today 23:45 · had to leave by 23:23     only when the headline is not today
Wednesday · weekday timetable · every 12 min 07:00–22:00
```

### Settings screen

```
←  Settings                                 back arrow and system back both save; no Done button
ROUTE
Home station              מרכז הכרמל  ›     tap → dialog with the six stations top→bottom, radio
Work station                 הנביאים  ›
Down the hill, 3 stops · ~6.5 min           computed
WALKING
Home → station                 [22] min     number keyboard, error if not a number
Work → station                 [ 5] min
Safety margin                  [ 0] min
CALIBRATION                           ▾     collapsed unless an override is set
To work · 3 stops, default 6.5   [    ]     Reset button when set
From work · 2 stops, default 5   [    ]
v0.2 · timetable from carmelithaifa.co.il
```

## Accuracy ideas (2026-09-17) — not built yet

### A. Trip timer ("stopwatch my commute")

What it measures: the walk time (today a hand-typed guess, and the biggest error in the chain —
the leave time is `train − walk − margin`, so a walk that is 2 min off makes everything 2 min off)
and, with one more tap, the ride offset to HaNevi'im (the only other non-exact number).

Flow, one button on the main screen that changes label as it goes:

```
[ Leaving now ]  →  [ At the platform ]  →  [ Doors open at destination ]  →  saved
   tap at the door      walk time = t2 − t1      ride offset = t3 − published departure
```

- The third tap is optional (long-press / "skip" ends after the walk). The published departure
  it refers to is the first train boardable at or after t2, which the app already knows.
- Store every sample (direction, date, walk seconds, offset seconds) in SharedPreferences.
  Settings shows "measured: 21:40 median of 5 (19:50–23:10)" next to each field with a
  **Use** button; never overwrite the user's number silently.
- Suggest the **median** for display but make it easy to pick a slower value: for catching a
  train the right number is a pessimistic one (e.g. the 2nd slowest), not the average.
  The spread also tells what safety margin is honest.
- The timer must survive the app being killed: store the start instant, not a running counter.
  No service, no notification needed.

**Timer, not GPS.** GPS would need location permission (the app has zero permissions), a
foreground service to keep tracking with the screen off, and does not work underground at the
platform — which is exactly the point being measured. Two taps are more accurate than a geofence
and cost ~50 lines. GPS rejected unless tapping proves too annoying.

### B. External data — researched 2026-09-17

Moovit, Google Maps etc. do not track the Carmelit by GPS; they all replay the Ministry of
Transport GTFS feed (`https://gtfs.mot.gov.il/gtfsfiles/israel-public-transportation.zip`,
needs a browser User-Agent; agency_id 20, routes 19087 down / 19088 up). Pulled the feed dated
2026-09-16 and compared:

- **Departure minutes: identical to the app** in every window (weekday, Friday, motzei Shabbat
  20:00 every 15, erev Yom Kippur). Second independent confirmation of the timetable.
- **Intermediate stops: useless.** The feed puts the 4 middle stations at +0:31, +0:59, +1:04,
  +1:33 and the far terminus at +12:00 — interpolated filler, not measurements. This is why
  Moovit's per-station times are wrong, and it means no online source has real stop offsets.
  Only idea A (or a stopwatch on a ride) can produce them.
- **Closing-time trains exist in the feed**: 15:00 on Fridays, 13:00 on erev Yom Kippur, and a
  00:00 trip on Sun–Fri (= the 24:00 train of Sat-night–Thu; none on Saturday 00:00, matching
  Friday's 15:00 close). The app currently excludes them. Evidence, not proof; keeping them
  excluded is still the safe side. **Decided 2026-09-17: included.**
- **Yom Kippur night differs**: feed has 21:00, 21:12, 21:24, 21:36, 21:48 then 22:00 every 15;
  the app assumes every 15 from 21:00 (the site gives no frequency). Only 21:00 and 22:00 onward
  appear in both. Affects Mon 2026-09-21.
- Sukkot and Simchat Torah 2026 both fall on Shabbat, so the feed says nothing new about how
  chag days are handled. No real-time (SIRI) data exists for the Carmelit.

Possible follow-up: a small script in the repo that downloads the feed and diffs agency 20's
departures against `Timetable.kt`, run by hand now and then, to catch timetable changes.

### A2. Six-tap version (owner's proposal, 2026-09-17) — supersedes the 3-tap flow above

| # | Tap when | Gives | Verdict |
|---|---|---|---|
| 1 | leaving home | start of walk | essential |
| 2 | on the platform, ticket bought | **walk time** = t2 − t1 | essential |
| 3 | doors open | mid-line: **boarding offset** = t3 − published departure (replaces the single 5 min measurement). At a terminus: how long before its departure the train is there to board (negative offset); Skip if it is already standing open | keep in both directions (2026-09-17: first built hidden at termini on an untested assumption; the owner had said all six, so it is shown. It never anchors the train match at a terminus) |
| 4 | doors close | the **real deadline**. t4 − published departure at the terminus = how late the train really leaves (the "~2 min lag" guess); t4 − t3 mid-line = dwell, i.e. how many seconds of slack exist after the train shows up | essential — the most valuable tap after 1 and 2 |
| 5 | doors open at destination | **arrival offset** = t5 − published departure; ride = t5 − t4 | essential |
| 6 | at the work door | last leg = t6 − t5. Not used for "when to leave", but gives true door-to-door time and enables a later "arrive by HH:MM" mode; reversed, it seeds the work → station walk | keep, optional |

Nothing is truly redundant; tap 3 is the only one that is sometimes empty. Rules that keep six
taps bearable:

- One big button whose label names the *next* event; every step has **Skip**, and the run can be
  ended at any step. A run with only taps 1–2 is still a valid walk sample.
- **Undo** (go back one step) for a mis-tap; a sample is only saved at the end or on "Finish".
- Taps 3 and 4 are seconds apart mid-line and the phone is in hand anyway.
- The train a run belongs to = the departure whose predicted door-close is nearest to t4 (t3/t5
  as fallback). Store it with the sample so offsets are always relative to a published minute.
- Same six steps mirrored for the way home (leave work → platform → open → close → arrive →
  home door).
- Works offline and underground; start instant persisted so a killed app resumes the run.
- After ≥3 samples Settings offers measured values (median, and the slow-side value) with **Use**.

### A3. Leaving at varying times — how the data stays honest (built 2026-09-17)

The owner rarely leaves at exactly the advised minute (often 3–5 min early, sometimes late), and
rides both ways. So nothing the timer learns may depend on the app's advice:

1. **The train is identified from the taps, not from the advice.** On finish, the run is matched
   to the published departure whose predicted time is nearest to "doors closed" (fallbacks: doors
   open, arrival). Trains are ≥12 min apart and the prediction is good to ~2 min, so the match is
   unambiguous; more than 6 min after or 2 min before any train's predicted time = "no train matched" (disruption) and the run only
   contributes its walk. Yesterday's 24:00 train is considered too.
2. **Parameters vs outcomes.** Walk (t2−t1), boarding offset (t3−dep), door-close offset
   (t4−dep), arrival offset (t5−dep) and last leg (t6−t5) are properties of the route — identical
   whether you left early or late. Platform wait (t4−t2) and "left N min before the advice"
   (advice−t1) are *outcomes* of when you left; they are stored and shown but never fed back.
3. **One number that judges the app:** `neededLead = dep − (t4 − walk)`, i.e. how long before the
   published departure you had to leave to just make the doors on that run. It is the same
   whenever you really left, so runs with 12 min of slack and runs with 1 min are comparable.
   Settings shows it next to what the app currently advises (`walk + margin − boardOffset`).
4. **Plan for the bad end, not the average.** Median is displayed, but the **Use** buttons take
   the 80th-percentile walk (max under 5 runs, rounded up) and the *earliest* observed boarding
   offset going home (rounded down to 0.5). Arrival offset uses the median — it only affects a
   displayed time. Known bias: a run with lots of slack is walked slower, a tight one faster;
   the slow-end choice errs safe, and `neededLead` high end is the cross-check.
5. **Nothing is applied silently**, runs are kept raw (six timestamps + matched train + advice at
   the time, last 200, own prefs file) so any better statistic can be computed later. Runs count
   only for the station pair currently set, per direction. A forgotten run is discarded (not saved) 3 h after its first tap.

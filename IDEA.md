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
published departure time is exact for me; only the arrival at HaNevi'im is an estimate (~5 min).

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
| Erev Yom Kippur | 06:00–13:00 (same 15/12 pattern) |
| Yom Kippur (night) | 21:00–24:00 |
| Erev Pesach | 06:00–14:00 |

Notes:
- Every boundary lands exactly on a departure (07:00 + 12·75 = 22:00, 22:00 + 15·8 = 24:00, 07:00 + 12·40 = 15:00).
  Whether the closing-time train (15:00 / 24:00) actually runs is not stated; treat the one before it as the last guaranteed one.
- The site says both "April–October / November–March" and "שעון קיץ / שעון חורף" for the Shabbat start. Use real Israeli DST (Asia/Jerusalem offset +3 = summer); note the mismatch in late March / late October.
- Ticket prices are only published as an image; irrelevant for the app.

## Day classification rules

A day is a **rest day** if it is Shabbat or one of: Rosh Hashana (2 days), Yom Kippur,
Sukkot day 1, Shmini Atzeret/Simchat Torah, Pesach day 1, Pesach day 7, Shavuot.
(Other holidays — Purim, chol hamoed, Independence Day, etc. — are normal days, like buses.)

- Rest day whose next day is also a rest day → **no service at all** (e.g. Rosh Hashana I 2026 = Saturday, day II = Sunday → Saturday closed).
- Rest day whose next day is normal → evening service only (motzei schedule).
- Yom Kippur → 21:00–24:00 (YK never falls on Fri/Sun, so no conflict).
- Day before a rest day (erev chag, or a plain Friday) → Friday hours. Erev Yom Kippur / Erev Pesach have their own shorter windows.
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
Working estimate of minutes after terminus departure, by number of stops travelled:

| stops from terminus | 0 | 1 | 2 | 3 | 4 | 5 |
|---|---|---|---|---|---|---|
| minutes | 0 | 1.5 | 3 | 5 | 6.5 | 8 |

Same table in both directions. For my trip (Carmel Center -> HaNevi'im, 3 stops) that is
~5 min, arriving just after the trains cross at the passing loop. This is the only non-exact
number in the whole app, hence the calibration field in settings. The way back
(HaNevi'im -> Carmel Center) needs the estimate on the boarding side: the up train reaches
HaNevi'im ~3 min after leaving Ir Tahtit.

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

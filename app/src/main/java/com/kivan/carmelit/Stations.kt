package com.kivan.carmelit

/** The six Carmelit stations, bottom (Ir Tahtit) to top (Merkaz HaCarmel). */
enum class Station(val index: Int, val hebrew: String, val english: String) {
    IR_TAHTIT(0, "עיר תחתית", "Ir Tahtit"),
    HADAR(1, "הדר-עירייה", "Hadar"),
    HANEVIIM(2, "הנביאים", "HaNevi'im"),
    MASADA(3, "מסדה", "Masada"),
    BNEI_ZION(4, "בני ציון", "Bnei Zion"),
    MERKAZ_HACARMEL(5, "מרכז הכרמל", "Merkaz HaCarmel"),
}

/** Travel direction; [origin] is the terminus whose published departure times apply. */
enum class Direction(val origin: Station) {
    UP(Station.IR_TAHTIT),
    DOWN(Station.MERKAZ_HACARMEL);

    companion object {
        fun between(from: Station, to: Station): Direction =
            if (to.index > from.index) UP else DOWN
    }
}

/**
 * Estimated seconds after the published terminus departure until the train reaches a station,
 * indexed by number of stops travelled: 0, 3.5, 5, 6.5, 8, 9.5 minutes.
 *
 * Measured 2026-09-17 at HaNevi'im going up: the 08:48 and 09:00 trains from Ir Tahtit arrived
 * ~08:53 and ~09:04, i.e. ~5 min for 2 stops, not the 3 min first estimated from the 8 min
 * end-to-end run. The table therefore models a ~2 min lag between the published minute and the
 * train actually moving, plus 1.5 min per stop. Index 0 stays 0: at a terminus the published
 * minute is when you have to be on the platform, whenever the doors finally close.
 * Same table in both directions; override per direction in Settings if a ride says otherwise.
 */
val OFFSET_SECONDS = intArrayOf(0, 210, 300, 390, 480, 570)

fun stopsFromOrigin(station: Station, direction: Direction): Int =
    if (direction == Direction.UP) station.index else Station.MERKAZ_HACARMEL.index - station.index

fun defaultOffsetSeconds(station: Station, direction: Direction): Int =
    OFFSET_SECONDS[stopsFromOrigin(station, direction)]

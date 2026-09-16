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
 * Working estimate of seconds after terminus departure until the train reaches a station,
 * indexed by number of stops travelled (IDEA.md: 0, 1.5, 3, 5, 6.5, 8 minutes).
 * Same table in both directions.
 */
val OFFSET_SECONDS = intArrayOf(0, 90, 180, 300, 390, 480)

fun stopsFromOrigin(station: Station, direction: Direction): Int =
    if (direction == Direction.UP) station.index else Station.MERKAZ_HACARMEL.index - station.index

fun defaultOffsetSeconds(station: Station, direction: Direction): Int =
    OFFSET_SECONDS[stopsFromOrigin(station, direction)]

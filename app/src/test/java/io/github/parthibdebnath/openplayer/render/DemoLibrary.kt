package io.github.parthibdebnath.openplayer.render

/** A made-up library for the theme renders: every artist and title here is invented. */
internal object DemoLibrary {
    data class Track(val title: String, val artist: String, val seconds: Int)

    private fun t(title: String, artist: String, seconds: Int) = Track(title, artist, seconds)

    val playlists: List<Pair<String, List<Track>>> = listOf(
        "Late Night Drive" to listOf(
            t("Amber Streetlights", "The Satellites", 241),
            t("Glass Hearts", "Nova Lane", 214),
            t("Overpass", "Harbor Lights", 198),
            t("Neon Horizons", "The Satellites", 245),
            t("Slow Motion City", "Velvet Arcade", 263),
            t("Afterglow", "Juniper Coast", 227),
            t("Paper Planes at Dawn", "Mira Sol", 189),
            t("Static Bloom", "Echo Park Radio", 236),
            t("Westbound", "Harbor Lights", 255),
            t("Electric Blue Hour", "Nova Lane", 212),
            t("Satellite Love", "Velvet Arcade", 248),
            t("Low Tide", "Juniper Coast", 201),
            t("Fading Signals", "The Satellites", 279),
            t("Rearview", "Mira Sol", 222),
            t("Cinematic", "Echo Park Radio", 231),
            t("Long Way Home", "Harbor Lights", 296),
            t("Polaroid Summer", "Nova Lane", 207),
            t("Tunnel Vision", "Velvet Arcade", 218),
            t("Coastline", "Juniper Coast", 244),
            t("Midnight Radio", "The Satellites", 260),
            t("Silver Lining", "Mira Sol", 199),
            t("Kaleidoscope", "Echo Park Radio", 233),
            t("City of Glass", "Harbor Lights", 251),
            t("Open Road", "Nova Lane", 228),
        ),
        "Morning Coffee" to listOf(
            t("Sunday Papers", "Willow & Wren", 184),
            t("Honey Light", "Cedar Folk", 203),
            t("Window Seat", "Lila Hart", 176),
            t("Slow Kettle", "Willow & Wren", 192),
            t("Linen", "June Avenue", 215),
            t("Little Victories", "Cedar Folk", 208),
            t("Corner Café", "Lila Hart", 187),
            t("Early Bird", "June Avenue", 169),
            t("Steam", "Willow & Wren", 221),
            t("Porch Swing", "Cedar Folk", 199),
            t("Grey Skies, Warm Mugs", "Lila Hart", 205),
            t("Good Morning, Stranger", "June Avenue", 196),
            t("Daylight Savings", "Willow & Wren", 210),
            t("Wildflowers", "Cedar Folk", 183),
            t("Second Cup", "Lila Hart", 174),
            t("Sun Through Blinds", "June Avenue", 222),
            t("Easy Does It", "Willow & Wren", 190),
            t("Quiet Street", "Cedar Folk", 201),
        ),
        "Workout Mix" to List(31) { i ->
            val titles = listOf("Pulse", "Overdrive", "Run It Back", "Heartbeat 140", "Fast Lane", "Adrenaline", "Push", "No Brakes", "Sprint", "Ignition", "Breakaway")
            val artists = listOf("DJ Kinetic", "Voltage", "Rush Hour", "Sonic Youthquake", "Metronome")
            t(titles[i % titles.size] + if (i >= titles.size) " (Remix ${i / titles.size})" else "", artists[i % artists.size], 180 + (i * 17) % 90)
        },
        "Road Trip '08" to List(42) { i ->
            val titles = listOf("Highway Hymn", "Gas Station Sunset", "Mile Marker 9", "Desert Bloom", "Truck Stop Diner", "Big Sky", "Motel Pool", "State Line")
            val artists = listOf("The Wanderers", "Dusty Roads", "Sunset Motel", "Prairie Fire")
            t(titles[i % titles.size] + if (i >= titles.size) " Pt. ${i / titles.size + 1}" else "", artists[i % artists.size], 200 + (i * 23) % 100)
        },
        "Chill Beats" to List(27) { i ->
            val titles = listOf("Lo-Fi Rain", "Tape Hiss", "Vinyl Dreams", "Study Session", "Bedroom Window", "Cloud Nine", "Mellow Yellow Light")
            val artists = listOf("Kōji Beats", "Soft Static", "Rainy Tapes")
            t(titles[i % titles.size] + if (i >= titles.size) " ${i / titles.size + 1}" else "", artists[i % artists.size], 150 + (i * 13) % 80)
        },
        "Indie Favorites" to List(15) { i ->
            val titles = listOf("Paper Moon", "The Garden Party", "Bicycle Song", "Thrift Store Sweater", "Postcards", "Rooftops")
            val artists = listOf("The Polaroids", "Marigold", "Bright Young Things")
            t(titles[i % titles.size] + if (i >= titles.size) " (Live)" else "", artists[i % artists.size], 190 + (i * 11) % 70)
        },
    )
}


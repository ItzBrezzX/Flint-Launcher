package com.flintlauncher.android.partners

/**
 * Default list: five well-known public servers, taken from another launcher's
 * server list. These are NOT Flint partners and Flint is not affiliated with them.
 *
 * Addresses were cross-checked against public server guides (Hypixel, MCC Island,
 * DonutSMP, Mineplex). Hoplite's address and every website URL come from the
 * reference screenshot or are inferred and must be re-verified. A wrong entry shows
 * up as Offline/Unknown, never as fake data.
 */
object DefaultServers {
    const val DEFAULT_LIMIT = 5

    val list: List<ServerEntry> = listOf(
        ServerEntry("Hypixel", "mc.hypixel.net", Edition.JAVA, "https://hypixel.net"),
        ServerEntry("Mineplex", "mineplex.com", Edition.JAVA, "https://mineplex.com"),
        ServerEntry("MCC Island", "play.mccisland.net", Edition.JAVA, "https://mccisland.net"),
        ServerEntry("DonutSMP", "donutsmp.net", Edition.JAVA, "https://donutsmp.net"),
        ServerEntry("Hoplite", "mc.hoplite.gg", Edition.JAVA, "https://hoplite.gg"),
    )
}

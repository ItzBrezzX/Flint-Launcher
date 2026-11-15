package com.flintlauncher.android.minecraft.version

import com.flintlauncher.android.minecraft.manifest.VersionType

/**
 * How a version encodes its launch arguments in the version JSON.
 * The version JSON itself is always authoritative; this is only the era's *expected*
 * format, used for diagnostics and for versions whose JSON we had to synthesize.
 */
enum class ArgsFormat {
    /** Pre-1.13: one space-separated `minecraftArguments` string. */
    LEGACY_STRING,
    /** 1.13+: structured `arguments { game, jvm }` with rules. */
    MODERN_OBJECT,
}

/** How assets are laid out on disk for this era. */
enum class AssetsLayout {
    /** <=1.5.x: named files under `<game>/resources/` (`map_to_resources`). */
    RESOURCES_DIR,
    /** 1.6.x: `assets/virtual/legacy/<name>` mirrored tree (`virtual: true`). */
    VIRTUAL_LEGACY,
    /** >=1.7: content-addressed `assets/objects/<hh>/<sha1>` read through the index. */
    CONTENT_ADDRESSED,
}

/**
 * Minecraft release eras. Classification is by version id pattern and manifest type;
 * every trait below is a *fallback expectation*. The parsed version JSON always wins
 * when a field is present. Classic/Indev/Infdev are not published in Mojang's modern
 * `version_manifest_v2.json` — installing them needs legacy metadata, which Flint does
 * not fetch yet (see [VersionEra.inMojangManifest]).
 */
enum class VersionEra(
    val displayName: String,
    val lwjglMajor: Int,
    val argsFormat: ArgsFormat,
    val assetsLayout: AssetsLayout,
    /** Java major assumed when the version JSON omits `javaVersion`. */
    val fallbackJavaMajor: Int,
    /** Main class assumed when the version JSON omits `mainClass` (legacy only). */
    val fallbackMainClass: String,
    val inMojangManifest: Boolean,
) {
    CLASSIC(
        "Classic (2009)", lwjglMajor = 2, argsFormat = ArgsFormat.LEGACY_STRING,
        assetsLayout = AssetsLayout.RESOURCES_DIR, fallbackJavaMajor = 8,
        fallbackMainClass = "net.minecraft.client.Minecraft", inMojangManifest = false,
    ),
    INDEV(
        "Indev (2009–2010)", lwjglMajor = 2, argsFormat = ArgsFormat.LEGACY_STRING,
        assetsLayout = AssetsLayout.RESOURCES_DIR, fallbackJavaMajor = 8,
        fallbackMainClass = "net.minecraft.client.Minecraft", inMojangManifest = false,
    ),
    INFDEV(
        "Infdev (2010)", lwjglMajor = 2, argsFormat = ArgsFormat.LEGACY_STRING,
        assetsLayout = AssetsLayout.RESOURCES_DIR, fallbackJavaMajor = 8,
        fallbackMainClass = "net.minecraft.client.Minecraft", inMojangManifest = false,
    ),
    ALPHA(
        "Alpha (a1.0–a1.2.6)", lwjglMajor = 2, argsFormat = ArgsFormat.LEGACY_STRING,
        assetsLayout = AssetsLayout.RESOURCES_DIR, fallbackJavaMajor = 8,
        fallbackMainClass = "net.minecraft.client.Minecraft", inMojangManifest = true,
    ),
    BETA(
        "Beta (b1.0–b1.8.1)", lwjglMajor = 2, argsFormat = ArgsFormat.LEGACY_STRING,
        assetsLayout = AssetsLayout.RESOURCES_DIR, fallbackJavaMajor = 8,
        fallbackMainClass = "net.minecraft.client.Minecraft", inMojangManifest = true,
    ),
    RELEASE_1_0_TO_1_12(
        "1.0–1.12.2", lwjglMajor = 2, argsFormat = ArgsFormat.LEGACY_STRING,
        assetsLayout = AssetsLayout.CONTENT_ADDRESSED, fallbackJavaMajor = 8,
        // 1.6+ actually uses net.minecraft.client.main.Main; <=1.5.2 uses the legacy
        // main class. The version JSON is authoritative; this fallback covers both by
        // pointing at the newer one, which is present for 1.6+ and absent for <=1.5.2
        // (where the JSON supplies the legacy name).
        fallbackMainClass = "net.minecraft.client.main.Main", inMojangManifest = true,
    ),
    RELEASE_1_13_TO_1_16(
        "1.13–1.16.5", lwjglMajor = 3, argsFormat = ArgsFormat.MODERN_OBJECT,
        assetsLayout = AssetsLayout.CONTENT_ADDRESSED, fallbackJavaMajor = 8,
        fallbackMainClass = "net.minecraft.client.main.Main", inMojangManifest = true,
    ),
    RELEASE_1_17_TO_1_20(
        "1.17–1.20.4", lwjglMajor = 3, argsFormat = ArgsFormat.MODERN_OBJECT,
        assetsLayout = AssetsLayout.CONTENT_ADDRESSED,
        // 1.17 specifically requires Java 16; 1.18–1.20.4 require Java 17. The version
        // JSON's javaVersion.majorVersion is authoritative and always present here.
        fallbackJavaMajor = 17,
        fallbackMainClass = "net.minecraft.client.main.Main", inMojangManifest = true,
    ),
    RELEASE_1_21(
        "1.21.x (and 1.20.5+)", lwjglMajor = 3, argsFormat = ArgsFormat.MODERN_OBJECT,
        assetsLayout = AssetsLayout.CONTENT_ADDRESSED, fallbackJavaMajor = 21,
        fallbackMainClass = "net.minecraft.client.main.Main", inMojangManifest = true,
    ),
    SNAPSHOT(
        "Snapshots / pre-releases / RC", lwjglMajor = 3, argsFormat = ArgsFormat.MODERN_OBJECT,
        assetsLayout = AssetsLayout.CONTENT_ADDRESSED,
        // Modern snapshots use Java 21; very old snapshots (13w*, 14w*) use Java 8.
        // The version JSON always carries javaVersion for snapshots that need a bump.
        fallbackJavaMajor = 21,
        fallbackMainClass = "net.minecraft.client.main.Main", inMojangManifest = true,
    ),
    EXPERIMENTAL(
        "Experimental snapshots", lwjglMajor = 3, argsFormat = ArgsFormat.MODERN_OBJECT,
        assetsLayout = AssetsLayout.CONTENT_ADDRESSED, fallbackJavaMajor = 21,
        fallbackMainClass = "net.minecraft.client.main.Main", inMojangManifest = true,
    ),
    ;

    /** True when Mojang ships natives inside separate classifier jars (LWJGL 2 eras). */
    val usesSeparateNativeJars: Boolean get() = lwjglMajor == 2

    companion object {
        private val SNAPSHOT_PATTERN = Regex("""^\d{2}w\d{2}[a-z]?$""", RegexOption.IGNORE_CASE)
        private val PRE_RC_PATTERN = Regex(""".*-(pre|rc)\d+$""", RegexOption.IGNORE_CASE)
        private val RELEASE_PATTERN = Regex("""^1\.(\d+)(\.\d+)?$""")

        /**
         * Classify a version id (and optional manifest type) into an era.
         * Never throws: unrecognized ids fall back to [SNAPSHOT] only when they look like
         * one, otherwise [RELEASE_1_21] (the newest known era) so callers can still proceed
         * with the parsed JSON as the source of truth.
         */
        fun classify(id: String, type: VersionType = VersionType.UNKNOWN): VersionEra {
            val lower = id.lowercase()
            if (lower.contains("experimental")) return EXPERIMENTAL
            if (lower.startsWith("rd-") || lower.startsWith("c0.") || lower.startsWith("classic")) return CLASSIC
            if (lower.startsWith("in-")) return INDEV
            if (lower.startsWith("inf-")) return INFDEV
            if (type == VersionType.OLD_ALPHA || lower.startsWith("a1.") || lower.startsWith("alpha")) return ALPHA
            if (type == VersionType.OLD_BETA || lower.startsWith("b1.") || lower.startsWith("beta")) return BETA
            if (type == VersionType.SNAPSHOT || SNAPSHOT_PATTERN.matches(id) || PRE_RC_PATTERN.matches(lower)) {
                return SNAPSHOT
            }
            val release = RELEASE_PATTERN.matchEntire(lower)
            if (release != null) {
                val minor = release.groupValues[1].toIntOrNull() ?: return RELEASE_1_21
                return when {
                    minor <= 12 -> RELEASE_1_0_TO_1_12
                    minor <= 16 -> RELEASE_1_13_TO_1_16
                    minor <= 20 -> RELEASE_1_17_TO_1_20
                    else -> RELEASE_1_21
                }
            }
            // Loader version ids like "fabric-loader-0.15.0-1.21" or "1.21-forge-49" embed
            // the vanilla version. Recurse on the embedded release id.
            embeddedRelease(lower)?.let { return classify(it, VersionType.RELEASE) }
            return RELEASE_1_21
        }

        private fun embeddedRelease(id: String): String? {
            val m = Regex("""(?:^|[^0-9])(1\.\d+(?:\.\d+)?)""").find(id) ?: return null
            return m.groupValues[1]
        }
    }
}

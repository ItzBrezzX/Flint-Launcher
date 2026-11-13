package com.flintlauncher.android.minecraft.manifest

import kotlinx.serialization.json.Json

/** Mojang adds fields over time, so unknown keys are ignored and unknown enum values coerced. */
internal val MojangJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

object VersionManifestParser {
    fun parse(text: String): VersionManifest =
        MojangJson.decodeFromString(VersionManifest.serializer(), text)
}

object VersionMetaParser {
    fun parse(text: String): VersionMeta =
        MojangJson.decodeFromString(VersionMeta.serializer(), text)
}

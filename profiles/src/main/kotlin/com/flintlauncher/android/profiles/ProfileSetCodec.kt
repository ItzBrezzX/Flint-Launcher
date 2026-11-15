package com.flintlauncher.android.profiles

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.Instant

/**
 * Versioned container for exporting / importing the full profile set as one JSON file.
 * Single-profile export/import already lives on [ProfileRepository]; this handles the
 * "share / back up all profiles" case. Format is versioned so future schema changes can
 * migrate old imports. Imported profiles are untrusted: risky JVM arguments are dropped
 * (via [ProfileSecurity]) and reported, never applied.
 */
object ProfileSetCodec {
    const val FORMAT_VERSION = 1

    @Serializable
    private data class ExportedSet(
        val format: Int = FORMAT_VERSION,
        val app: String = "flint",
        val exportedAt: String = "",
        val profiles: List<Profile> = emptyList(),
    )

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun exportAll(profiles: List<Profile>): String =
        json.encodeToString(
            ExportedSet.serializer(),
            ExportedSet(
                format = FORMAT_VERSION,
                exportedAt = Instant.now().toString(),
                // Strip ids and default flags so imports never collide with existing profiles.
                profiles = profiles.map { it.copy(id = "", isDefault = false) },
            ),
        )

    data class ImportedProfile(val profile: Profile, val droppedArguments: List<String>)

    data class ImportSetResult(val profiles: List<Profile>, val droppedArguments: List<String>)

    /**
     * Parse and sanitize an exported set. Returns sanitized profile drafts (no id assigned;
     * the caller saves them through [ProfileRepository] which assigns ids and unique names).
     * Risky JVM arguments are dropped and aggregated in the result.
     */
    fun importAll(text: String): ImportSetResult {
        val set = try {
            json.decodeFromString(ExportedSet.serializer(), text)
        } catch (e: Exception) {
            throw ProfileValidationException(listOf("Not a valid Flint profile set file"))
        }
        if (set.format !in 1..FORMAT_VERSION) {
            throw ProfileValidationException(listOf("Unsupported profile set format version: ${set.format}"))
        }
        val dropped = mutableListOf<String>()
        val sanitized = set.profiles.map { profile ->
            val (safeJvm, droppedJvm) = profile.jvmArguments.partition { !ProfileSecurity.isRisky(it) }
            val (safeGame, droppedGame) = profile.gameArguments.partition { !ProfileSecurity.isRisky(it) }
            dropped += droppedJvm + droppedGame
            profile.copy(
                id = "",
                isDefault = false,
                jvmArguments = safeJvm,
                gameArguments = safeGame,
            )
        }
        return ImportSetResult(sanitized, dropped)
    }
}

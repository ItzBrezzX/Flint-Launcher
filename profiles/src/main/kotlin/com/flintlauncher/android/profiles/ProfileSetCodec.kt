package com.flintlauncher.android.profiles

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

/**
 * Versioned backup/restore format for a complete set of launcher profiles.
 * Imports return drafts without IDs/default selection; callers save them through
 * ProfileRepository so identifiers and unique names are assigned locally.
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
                exportedAt = Instant.now().toString(),
                profiles = profiles.map { it.copy(id = "", isDefault = false) },
            ),
        )

    data class ImportSetResult(
        val profiles: List<Profile>,
        val droppedArguments: List<String>,
    )

    fun importAll(text: String): ImportSetResult {
        val set = try {
            json.decodeFromString(ExportedSet.serializer(), text)
        } catch (e: Exception) {
            throw ProfileValidationException(listOf("Not a valid Flint profile set file"))
        }
        if (set.app != "flint") {
            throw ProfileValidationException(listOf("This file is not a Flint profile backup"))
        }
        if (set.format !in 1..FORMAT_VERSION) {
            throw ProfileValidationException(listOf("Unsupported profile set format version: ${set.format}"))
        }

        val dropped = mutableListOf<String>()
        val sanitized = set.profiles.map { profile ->
            val (safeJvm, droppedJvm) = profile.jvmArguments.partition { !ProfileSecurity.isRisky(it) }
            val (safeGame, droppedGame) = profile.gameArguments.partition { !ProfileSecurity.isRisky(it) }
            dropped += droppedJvm + droppedGame
            val draft = profile.copy(
                id = "",
                isDefault = false,
                jvmArguments = safeJvm,
                gameArguments = safeGame,
            )
            val validationErrors = ProfileValidator.validate(draft)
            if (validationErrors.isNotEmpty()) {
                throw ProfileValidationException(validationErrors.map { "Profile ${profile.name}: $it" })
            }
            draft
        }
        return ImportSetResult(sanitized, dropped)
    }
}

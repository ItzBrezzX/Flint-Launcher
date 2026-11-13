package com.flintlauncher.android.profiles

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/** JVM arguments that can run arbitrary code or replace core classes. Dropped from imported profiles. */
object ProfileSecurity {
    private val riskyPrefixes = listOf(
        "-javaagent", "-agentlib", "-agentpath", "-Xbootclasspath", "-Xrun",
        "-XX:OnError", "-XX:OnOutOfMemoryError", "-XX:Flags", "-XX:VMOptionsFile",
        "-Djava.system.class.loader", "-Djava.security.manager", "-Djava.security.policy",
    )

    fun isRisky(argument: String): Boolean {
        val trimmed = argument.trim()
        return riskyPrefixes.any { trimmed.startsWith(it, ignoreCase = true) } ||
            trimmed.startsWith("@") // argument files can smuggle anything in
    }
}

@Serializable
private data class ExportedProfile(val format: Int = 1, val profile: Profile)

data class ImportResult(val profile: Profile, val droppedArguments: List<String>)

/**
 * Profiles stored as one JSON file (written atomically). Room can replace this later behind
 * the same operations. Deleting a profile does not delete its files: the caller decides that
 * after asking the user.
 */
class ProfileRepository(
    private val file: File,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val listSerializer = ListSerializer(Profile.serializer())
    private var profiles: List<Profile> = load()

    @Synchronized
    fun list(): List<Profile> = profiles

    @Synchronized
    fun get(id: String): Profile? = profiles.firstOrNull { it.id == id }

    @Synchronized
    fun default(): Profile? = profiles.firstOrNull { it.isDefault }

    @Synchronized
    fun create(draft: Profile): Profile {
        validate(draft, ignoreId = null)
        val created = draft.copy(id = newId(), isDefault = profiles.isEmpty())
        save(profiles + created)
        return created
    }

    @Synchronized
    fun update(profile: Profile): Profile {
        val existing = get(profile.id) ?: throw ProfileValidationException(listOf("Profile not found"))
        validate(profile, ignoreId = profile.id)
        val updated = profile.copy(isDefault = existing.isDefault)
        save(profiles.map { if (it.id == profile.id) updated else it })
        return updated
    }

    @Synchronized
    fun delete(id: String): Boolean {
        val target = get(id) ?: return false
        var remaining = profiles.filter { it.id != id }
        if (target.isDefault && remaining.isNotEmpty()) {
            remaining = remaining.mapIndexed { index, p -> if (index == 0) p.copy(isDefault = true) else p }
        }
        save(remaining)
        return true
    }

    @Synchronized
    fun clone(id: String, newName: String? = null): Profile {
        val source = get(id) ?: throw ProfileValidationException(listOf("Profile not found"))
        return create(source.copy(name = newName ?: uniqueName("${source.name} copy"), isDefault = false))
    }

    @Synchronized
    fun setDefault(id: String) {
        if (get(id) == null) throw ProfileValidationException(listOf("Profile not found"))
        save(profiles.map { it.copy(isDefault = it.id == id) })
    }

    @Synchronized
    fun export(id: String): String {
        val source = get(id) ?: throw ProfileValidationException(listOf("Profile not found"))
        return json.encodeToString(ExportedProfile.serializer(), ExportedProfile(profile = source.copy(id = "", isDefault = false)))
    }

    /** Imported profiles are untrusted: risky JVM arguments are removed and reported, never applied. */
    @Synchronized
    fun importProfile(text: String): ImportResult {
        val exported = try {
            json.decodeFromString(ExportedProfile.serializer(), text)
        } catch (e: Exception) {
            throw ProfileValidationException(listOf("Not a valid Flint profile file"))
        }
        val (safe, dropped) = exported.profile.jvmArguments.partition { !ProfileSecurity.isRisky(it) }
        val draft = exported.profile.copy(
            name = uniqueName(exported.profile.name),
            jvmArguments = safe,
            gameArguments = exported.profile.gameArguments.filterNot { ProfileSecurity.isRisky(it) },
        )
        return ImportResult(create(draft), dropped)
    }

    private fun uniqueName(base: String): String {
        val taken = profiles.map { it.name.lowercase() }.toSet()
        if (base.lowercase() !in taken) return base
        var counter = 2
        while ("$base $counter".lowercase() in taken) counter++
        return "$base $counter"
    }

    private fun validate(profile: Profile, ignoreId: String?) {
        val errors = ProfileValidator.validate(profile).toMutableList()
        if (profiles.any { it.id != ignoreId && it.name.equals(profile.name, ignoreCase = true) }) {
            errors += "A profile named \"${profile.name}\" already exists"
        }
        if (errors.isNotEmpty()) throw ProfileValidationException(errors)
    }

    private fun save(updated: List<Profile>) {
        file.absoluteFile.parentFile?.mkdirs()
        val temp = File(file.path + ".tmp")
        temp.writeText(json.encodeToString(listSerializer, updated))
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        profiles = updated
    }

    private fun load(): List<Profile> {
        if (!file.isFile) return emptyList()
        return try {
            json.decodeFromString(listSerializer, file.readText())
        } catch (e: Exception) {
            emptyList()
        }
    }
}

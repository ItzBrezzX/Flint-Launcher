package com.flintlauncher.android.profiles

import kotlinx.serialization.Serializable
import java.io.File

object Loaders {
    const val VANILLA = "vanilla"
    const val FABRIC = "fabric"
    const val QUILT = "quilt"
    const val FORGE = "forge"
    const val NEOFORGE = "neoforge"
    val all = setOf(VANILLA, FABRIC, QUILT, FORGE, NEOFORGE)
}

@Serializable
data class Profile(
    val id: String = "",
    val name: String,
    val icon: String = "grass",
    val minecraftVersion: String,
    val loader: String = Loaders.VANILLA,
    /** Null means "latest compatible", resolved at install time. */
    val loaderVersion: String? = null,
    /** Null means "choose from the version's requirement". */
    val javaMajor: Int? = null,
    val memoryMb: Int = 2048,
    val jvmArguments: List<String> = emptyList(),
    val gameArguments: List<String> = emptyList(),
    val enabled: Boolean = true,
    val isDefault: Boolean = false,
)

class ProfileValidationException(val errors: List<String>) : IllegalArgumentException(errors.joinToString("; "))

object ProfileValidator {
    const val MIN_MEMORY_MB = 256
    const val MAX_MEMORY_MB = 32_768

    fun validate(profile: Profile): List<String> {
        val errors = mutableListOf<String>()
        if (profile.name.isBlank()) errors += "Name is required"
        if (profile.name.length > 64) errors += "Name must be 64 characters or fewer"
        if (profile.name.any { it.isISOControl() }) errors += "Name contains control characters"
        if (profile.minecraftVersion.isBlank()) errors += "Minecraft version is required"
        if (profile.loader !in Loaders.all) errors += "Unknown loader: ${profile.loader}"
        if (profile.memoryMb !in MIN_MEMORY_MB..MAX_MEMORY_MB) {
            errors += "Memory must be between $MIN_MEMORY_MB and $MAX_MEMORY_MB MB"
        }
        if (profile.jvmArguments.any { it.isBlank() }) errors += "JVM arguments must not be blank"
        if (profile.gameArguments.any { it.isBlank() }) errors += "Game arguments must not be blank"
        return errors
    }
}

/** Every profile gets its own directory tree so profiles never share mods, saves or config. */
class ProfileDirs(launcherRoot: File, val profileId: String) {
    init {
        require(profileId.isNotEmpty() && profileId.all { it.isLetterOrDigit() || it == '-' }) {
            "Invalid profile id"
        }
    }

    val gameDir: File = File(File(launcherRoot.absoluteFile, "profiles"), profileId)
    val mods: File get() = File(gameDir, "mods")
    val config: File get() = File(gameDir, "config")
    val resourcePacks: File get() = File(gameDir, "resourcepacks")
    val shaderPacks: File get() = File(gameDir, "shaderpacks")
    val saves: File get() = File(gameDir, "saves")

    fun createAll() {
        listOf(gameDir, mods, config, resourcePacks, shaderPacks, saves).forEach { it.mkdirs() }
    }
}

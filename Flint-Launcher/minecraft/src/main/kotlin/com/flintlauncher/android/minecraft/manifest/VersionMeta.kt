package com.flintlauncher.android.minecraft.manifest

import kotlinx.serialization.Serializable

@Serializable
data class JavaVersionSpec(
    val component: String? = null,
    val majorVersion: Int,
)

/**
 * The subset of a per-version JSON that the runtime layer needs first.
 * Libraries, assets and arguments are modelled in later stages.
 */
@Serializable
data class VersionMeta(
    val id: String,
    val type: VersionType = VersionType.UNKNOWN,
    val mainClass: String? = null,
    val javaVersion: JavaVersionSpec? = null,
) {
    /** Old versions omit `javaVersion`; Java 8 is the only safe assumption for them. */
    val requiredJavaMajor: Int
        get() = javaVersion?.majorVersion ?: LEGACY_JAVA_MAJOR

    companion object {
        const val LEGACY_JAVA_MAJOR = 8
    }
}

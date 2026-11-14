package com.flintlauncher.android

import com.flintlauncher.android.account.AccountType
import com.flintlauncher.android.account.FlintAccount
import com.flintlauncher.android.installer.GameLayout
import com.flintlauncher.android.installer.InstalledVersion
import com.flintlauncher.android.minecraft.version.Environment
import com.flintlauncher.android.minecraft.version.LaunchCommandBuilder
import com.flintlauncher.android.minecraft.version.LaunchConfigurationException
import com.flintlauncher.android.minecraft.version.LaunchContext
import com.flintlauncher.android.profiles.ProfileSecurity
import com.flintlauncher.android.runtime.JavaRuntimeInfo
import com.flintlauncher.android.runtime.LaunchRequest
import com.flintlauncher.android.runtime.RendererInfo
import java.io.File

/**
 * Converts a verified installation + selected identity/runtime/renderer into the exact
 * request consumed by GameLauncher. This assembles arguments only; it does not start JNI.
 */
object LaunchRequestFactory {

    fun create(
        installed: InstalledVersion,
        layout: GameLayout,
        gameDirectory: File,
        nativesDirectory: File,
        account: FlintAccount,
        accessToken: String,
        runtime: JavaRuntimeInfo,
        renderer: RendererInfo,
        launcherVersion: String,
        runtimeHeapMb: Int,
        customJvmArguments: String = "",
        profileJvmArguments: List<String> = emptyList(),
        profileGameArguments: List<String> = emptyList(),
        resolutionWidth: Int? = null,
        resolutionHeight: Int? = null,
        environment: Environment = Environment(),
    ): LaunchRequest {
        if (!runtime.verified) {
            throw LaunchConfigurationException("Selected Java runtime has not passed verification")
        }
        if (runtime.majorVersion < installed.requiredJavaMajor) {
            throw LaunchConfigurationException(
                "Minecraft ${installed.versionId} requires Java ${installed.requiredJavaMajor}+; selected Java is ${runtime.majorVersion}",
            )
        }
        if (!renderer.available) {
            throw LaunchConfigurationException("Selected renderer is not verified as available")
        }
        if ((resolutionWidth == null) != (resolutionHeight == null)) {
            throw LaunchConfigurationException("Custom resolution requires both width and height")
        }
        if (resolutionWidth != null) {
            val height = resolutionHeight
                ?: throw LaunchConfigurationException("Custom resolution requires both width and height")
            if (resolutionWidth !in 320..7680 || height !in 240..4320) {
                throw LaunchConfigurationException("Custom resolution is outside the supported range")
            }
        }
        val missingClasspath = installed.classpath.filterNot { it.isFile }
        if (missingClasspath.isNotEmpty()) {
            throw LaunchConfigurationException(
                "Minecraft classpath is incomplete: ${missingClasspath.take(3).joinToString { it.name }}",
            )
        }
        if (!installed.clientJar.isFile) {
            throw LaunchConfigurationException("Minecraft client JAR is missing")
        }
        if (!installed.assetsRoot.isDirectory || !layout.assetIndex(installed.assetIndexId).isFile) {
            throw LaunchConfigurationException("Minecraft assets or asset index are missing")
        }
        if (!gameDirectory.exists() && !gameDirectory.mkdirs()) {
            throw LaunchConfigurationException("Could not create the selected game directory")
        }
        if (!gameDirectory.isDirectory || !gameDirectory.canWrite()) {
            throw LaunchConfigurationException("Selected game directory is not writable")
        }
        if (!nativesDirectory.isDirectory) {
            throw LaunchConfigurationException("Native library directory has not been prepared")
        }

        val token = when (account.type) {
            AccountType.OFFLINE -> "0"
            AccountType.MICROSOFT -> accessToken.takeIf { it.isNotBlank() }
                ?: throw LaunchConfigurationException("Microsoft account token is missing")
        }
        val userType = if (account.type == AccountType.MICROSOFT) "msa" else "legacy"

        val unsafeProfileArguments = profileJvmArguments.filter(ProfileSecurity::isRisky)
        if (unsafeProfileArguments.isNotEmpty()) {
            throw LaunchConfigurationException(
                "Profile contains blocked JVM arguments: ${unsafeProfileArguments.joinToString()}",
            )
        }

        val context = LaunchContext(
            playerName = account.username,
            uuid = account.uuid,
            accessToken = token,
            userType = userType,
            versionName = installed.versionId,
            versionType = installed.version.type.name.lowercase(),
            gameDirectory = gameDirectory.absolutePath,
            assetsRoot = installed.assetsRoot.absolutePath,
            assetsIndexName = installed.assetIndexId,
            nativesDirectory = nativesDirectory.absolutePath,
            libraryDirectory = layout.librariesDir.absolutePath,
            classpath = installed.classpath.joinToString(File.pathSeparator) { it.absolutePath },
            launcherVersion = launcherVersion,
            resolutionWidth = resolutionWidth,
            resolutionHeight = resolutionHeight,
        )
        val extraJvm = buildList {
            installed.loggingJvmArgument?.let(::add)
            addAll(profileJvmArguments)
        }
        val command = LaunchCommandBuilder.build(
            version = installed.version,
            context = context,
            environment = environment,
            extraJvmArguments = extraJvm,
            extraGameArguments = profileGameArguments,
            runtimeHeapMb = runtimeHeapMb,
            customJvmArguments = customJvmArguments,
        )

        return LaunchRequest(
            versionId = installed.versionId,
            gameDir = gameDirectory,
            assetsDir = installed.assetsRoot,
            classpath = installed.classpath,
            nativeDir = nativesDirectory,
            mainClass = command.mainClass,
            jvmArgs = command.jvmArguments,
            gameArgs = command.gameArguments,
            accountUsername = account.username,
            accountUuid = account.uuid,
            accessToken = token,
            userType = userType,
            runtime = runtime,
            renderer = renderer,
        )
    }
}

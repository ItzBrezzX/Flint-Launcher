package com.flintlauncher.android.mods

import com.flintlauncher.android.download.DownloadManager
import com.flintlauncher.android.profiles.Loaders
import com.flintlauncher.android.profiles.Profile
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class ModrinthModInstallerTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private val profile = Profile(
        id = "test-profile",
        name = "Test",
        minecraftVersion = "1.21.1",
        loader = Loaders.FABRIC,
    )
    private val project = ModrinthProject(
        projectId = "mod-project",
        projectType = "mod",
        title = "Example Mod",
    )
    private val installer by lazy {
        ModrinthModInstaller(
            downloads = DownloadManager(OkHttpClient()),
            launcherRoot = temporary.root,
        )
    }

    @Test
    fun refusesDownloadsFromHostsOtherThanTheModrinthCdn() = runBlocking {
        val version = compatibleVersion(
            file = ModrinthFile(
                url = "https://example.invalid/mod.jar",
                filename = "mod.jar",
                primary = true,
                size = 100,
                hashes = mapOf("sha512" to "a".repeat(128)),
            ),
        )

        try {
            installer.install(profile, project, version)
            throw AssertionError("Non-Modrinth host should have been rejected")
        } catch (e: IOException) {
            assertTrue(e.message.orEmpty().contains("non-Modrinth"))
        }
    }

    @Test
    fun refusesFilesWithoutAModrinthChecksum() = runBlocking {
        val version = compatibleVersion(
            file = ModrinthFile(
                url = "https://cdn.modrinth.com/data/abc/mod.jar",
                filename = "mod.jar",
                primary = true,
                size = 100,
                hashes = emptyMap(),
            ),
        )

        try {
            installer.install(profile, project, version)
            throw AssertionError("A missing checksum should have been rejected")
        } catch (e: IOException) {
            assertTrue(e.message.orEmpty().contains("checksum", ignoreCase = true))
        }
    }

    @Test
    fun refusesNonJarFiles() = runBlocking {
        val version = compatibleVersion(
            file = ModrinthFile(
                url = "https://cdn.modrinth.com/data/abc/source.zip",
                filename = "source.zip",
                primary = true,
                size = 100,
                hashes = mapOf("sha512" to "a".repeat(128)),
            ),
        )

        try {
            installer.install(profile, project, version)
            throw AssertionError("A non-JAR artifact should have been rejected")
        } catch (e: IOException) {
            assertTrue(e.message.orEmpty().contains("JAR"))
        }
    }

    @Test
    fun refusesVersionsThatDoNotMatchTheSelectedProfile() = runBlocking {
        val version = compatibleVersion(
            file = ModrinthFile(
                url = "https://cdn.modrinth.com/data/abc/mod.jar",
                filename = "mod.jar",
                primary = true,
                size = 100,
                hashes = mapOf("sha512" to "a".repeat(128)),
            ),
            gameVersions = listOf("1.20.1"),
        )

        try {
            installer.install(profile, project, version)
            throw AssertionError("An incompatible Minecraft version should have been rejected")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty().contains("does not support"))
        }
    }

    private fun compatibleVersion(
        file: ModrinthFile,
        gameVersions: List<String> = listOf("1.21.1"),
    ) = ModrinthVersion(
        id = "version-1",
        projectId = project.projectId,
        versionNumber = "1.0.0",
        gameVersions = gameVersions,
        loaders = listOf("fabric"),
        datePublished = "2026-01-01T00:00:00Z",
        files = listOf(file),
    )
}

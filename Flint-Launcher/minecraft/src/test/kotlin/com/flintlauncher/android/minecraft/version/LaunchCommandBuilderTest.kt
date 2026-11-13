package com.flintlauncher.android.minecraft.version

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LaunchCommandBuilderTest {
    private val context = LaunchContext(
        playerName = "Steve", uuid = "00000000-0000-0000-0000-000000000001", accessToken = "token",
        userType = "msa", versionName = "test", versionType = "release",
        gameDirectory = "/g", assetsRoot = "/a", assetsIndexName = "idx",
        nativesDirectory = "/n", libraryDirectory = "/l", classpath = "/c/a.jar:/c/b.jar",
        launcherVersion = "1.0",
    )

    // Synthetic, modern-style (1.13+) arguments block.
    private val modern = VersionJsonParser.parse(
        """
        {
          "id": "test", "mainClass": "net.example.Main",
          "arguments": {
            "game": [
              "--username", "${'$'}{auth_player_name}",
              "--uuid", "${'$'}{auth_uuid}",
              { "rules": [ { "action": "allow", "features": { "is_demo_user": true } } ], "value": "--demo" },
              { "rules": [ { "action": "allow", "features": { "has_custom_resolution": true } } ],
                "value": [ "--width", "${'$'}{resolution_width}", "--height", "${'$'}{resolution_height}" ] }
            ],
            "jvm": [
              { "rules": [ { "action": "allow", "os": { "name": "windows" } } ], "value": "-XX:HeapDumpPath=win" },
              { "rules": [ { "action": "allow", "os": { "name": "linux" } } ], "value": "-Dlinux.only=true" },
              "-Djava.library.path=${'$'}{natives_directory}",
              "-cp", "${'$'}{classpath}"
            ]
          }
        }
        """.trimIndent(),
    )

    @Test
    fun appliesRulesAndSubstitutesPlaceholders() {
        val command = LaunchCommandBuilder.build(modern, context)

        assertEquals(listOf("-Dlinux.only=true", "-Djava.library.path=/n", "-cp", "/c/a.jar:/c/b.jar"), command.jvmArguments)
        assertEquals("net.example.Main", command.mainClass)
        assertEquals(listOf("--username", "Steve", "--uuid", "00000000-0000-0000-0000-000000000001"), command.gameArguments)
        assertFalse("--demo" in command.gameArguments)
    }

    @Test
    fun customResolutionFeatureAddsWidthAndHeight() {
        val command = LaunchCommandBuilder.build(modern, context.copy(resolutionWidth = 1280, resolutionHeight = 720))
        assertTrue(command.gameArguments.containsAll(listOf("--width", "1280", "--height", "720")))
    }

    @Test
    fun extraArgumentsGoInTheRightPlaces() {
        val command = LaunchCommandBuilder.build(modern, context, extraJvmArguments = listOf("-Xmx2G"), extraGameArguments = listOf("--fullscreen"))
        assertEquals("-Xmx2G", command.jvmArguments.first())
        assertEquals("--fullscreen", command.gameArguments.last())
        assertEquals("-Xmx2G", command.toArgumentList().first())
    }

    @Test
    fun legacyVersionsUseMinecraftArgumentsAndDefaultJvmArguments() {
        val legacy = VersionJsonParser.parse(
            """{"id":"1.8.9","mainClass":"net.minecraft.client.main.Main",
                "minecraftArguments":"--username ${'$'}{auth_player_name} --session ${'$'}{auth_session} --gameDir ${'$'}{game_directory}"}""",
        )
        val command = LaunchCommandBuilder.build(legacy, context)

        assertEquals(listOf("-Djava.library.path=/n", "-cp", "/c/a.jar:/c/b.jar"), command.jvmArguments)
        assertEquals(
            listOf("--username", "Steve", "--session", "token:token:00000000-0000-0000-0000-000000000001", "--gameDir", "/g"),
            command.gameArguments,
        )
    }

    @Test
    fun unresolvedPlaceholdersAreAnErrorNotASilentBadLaunch() {
        val broken = VersionJsonParser.parse(
            """{"id":"x","mainClass":"M","arguments":{"game":["--thing","${'$'}{mystery_value}"],"jvm":["-cp","${'$'}{classpath}"]}}""",
        )
        try {
            LaunchCommandBuilder.build(broken, context)
            fail("expected LaunchConfigurationException")
        } catch (e: LaunchConfigurationException) {
            assertTrue(e.message!!.contains("mystery_value"))
        }
    }

    @Test
    fun missingMainClassIsAnError() {
        val noMain = VersionJsonParser.parse("""{"id":"x","minecraftArguments":"--a b"}""")
        try {
            LaunchCommandBuilder.build(noMain, context)
            fail("expected LaunchConfigurationException")
        } catch (e: LaunchConfigurationException) {
            assertTrue(e.message!!.contains("main class"))
        }
    }

    @Test
    fun tokensAreNotLeakedIntoUnrelatedArguments() {
        val command = LaunchCommandBuilder.build(modern, context)
        assertFalse(command.toArgumentList().any { it.contains("token") })
    }
}

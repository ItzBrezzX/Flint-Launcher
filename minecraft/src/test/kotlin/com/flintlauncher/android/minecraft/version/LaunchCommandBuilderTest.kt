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
    fun addsManagedClasspathWhenVersionMetadataOmitsIt() {
        val noClasspath = VersionJsonParser.parse(
            """{"id":"test","mainClass":"M","arguments":{"game":["--demo"],"jvm":["-Dfile.encoding=UTF-8"]}}""",
        )

        val command = LaunchCommandBuilder.build(noClasspath, context)

        assertEquals(
            listOf("-Dfile.encoding=UTF-8", "-cp", context.classpath),
            command.jvmArguments,
        )
    }

    @Test
    fun rejectsDuplicateClasspathDeclarationsInsteadOfPassingAmbiguousArgv() {
        val duplicated = VersionJsonParser.parse(
            """{"id":"test","mainClass":"M","arguments":{"game":["--demo"],"jvm":["-cp","/first","--class-path","/second"]}}""",
        )
        try {
            LaunchCommandBuilder.build(duplicated, context)
            fail("expected LaunchConfigurationException")
        } catch (error: LaunchConfigurationException) {
            assertTrue(error.message!!.contains("more than one"))
        }
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
    @Test
    fun runtimeSettingsOverrideVersionDefaultsAndManagedHeapIsLast() {
        val command = LaunchCommandBuilder.build(
            modern,
            context,
            runtimeHeapMb = 1024,
            customJvmArguments = """-Duser.language=en -XX:+UseG1GC""",
        )

        assertEquals(
            listOf(
                "-Dlinux.only=true",
                "-Djava.library.path=/n",
                "-cp",
                "/c/a.jar:/c/b.jar",
                "-Duser.language=en",
                "-XX:+UseG1GC",
                "-Xmx1024M",
            ),
            command.jvmArguments,
        )
    }

    @Test
    fun customJvmArgumentsCanBeAppliedWithoutOverridingManagedHeap() {
        val command = LaunchCommandBuilder.build(
            modern,
            context,
            customJvmArguments = "-Dfile.encoding=UTF-8",
        )

        assertEquals("-Dfile.encoding=UTF-8", command.jvmArguments.last())
        assertFalse(command.jvmArguments.any { it.startsWith("-Xmx") })
    }


    @Test
    fun legacyArgumentsPreserveQuotedValuesAndPathsWithSpaces() {
        val legacy = VersionJson(
            id = "1.2.5",
            mainClass = "net.minecraft.client.Minecraft",
            minecraftArguments =
                "--username \"Player One\" --gameDir \"\${game_directory}\" --title \"A \"quoted\" title\"",
        )

        val command = LaunchCommandBuilder.build(
            legacy,
            context.copy(gameDirectory = "/data/user/0/flint/game files"),
        )

        assertEquals(
            listOf(
                "--username", "Player One",
                "--gameDir", "/data/user/0/flint/game files",
                "--title", "A \"quoted\" title",
            ),
            command.gameArguments,
        )
    }

    @Test
    fun malformedLegacyArgumentQuotesAreRejected() {
        val legacy = VersionJson(
            id = "1.2.5",
            mainClass = "net.minecraft.client.Minecraft",
            minecraftArguments = "--username \"unfinished",
        )

        try {
            LaunchCommandBuilder.build(legacy, context)
            fail("expected LaunchConfigurationException")
        } catch (error: LaunchConfigurationException) {
            assertTrue(error.message!!.contains("unterminated quote"))
        }
    }


    @Test
    fun b173LegacyMetadataBuildsQuotedLaunchArguments() {
        val beta = VersionJsonParser.parse(
            """{"id":"b1.7.3","type":"old_beta","mainClass":"net.minecraft.client.Minecraft",
                "minecraftArguments":"--username \"${'$'}{auth_player_name}\" --session \"${'$'}{auth_session}\" --gameDir \"${'$'}{game_directory}\" --assetsDir \"${'$'}{game_assets}\""}""",
        )

        val command = LaunchCommandBuilder.build(
            beta,
            context.copy(versionName = "b1.7.3", versionType = "old_beta"),
        )

        assertEquals("net.minecraft.client.Minecraft", command.mainClass)
        assertEquals(
            listOf(
                "--username", "Steve",
                "--session", "token:token:00000000-0000-0000-0000-000000000001",
                "--gameDir", "/g",
                "--assetsDir", "/a",
            ),
            command.gameArguments,
        )
    }


}

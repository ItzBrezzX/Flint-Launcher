package com.flintlauncher.android.minecraft.version

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleEvaluatorTest {
    private val linux = Environment(osName = "linux", osArch = "arm64")

    @Test
    fun noRulesMeansAllowed() {
        assertTrue(RuleEvaluator.allows(null, linux))
        assertTrue(RuleEvaluator.allows(emptyList(), linux))
    }

    @Test
    fun osSpecificAllowRuleOnlyMatchesThatOs() {
        val rules = listOf(Rule("allow", os = OsRule(name = "windows")))
        assertFalse(RuleEvaluator.allows(rules, linux))
        assertTrue(RuleEvaluator.allows(rules, linux.copy(osName = "windows")))
    }

    @Test
    fun theLastMatchingRuleWins() {
        val rules = listOf(Rule("allow"), Rule("disallow", os = OsRule(name = "osx")))
        assertTrue(RuleEvaluator.allows(rules, linux))
        assertFalse(RuleEvaluator.allows(rules, linux.copy(osName = "osx")))
    }

    @Test
    fun archRulesCompareExactly() {
        val x86Only = listOf(Rule("allow", os = OsRule(arch = "x86")))
        assertFalse(RuleEvaluator.allows(x86Only, linux))
        assertTrue(RuleEvaluator.allows(x86Only, linux.copy(osArch = "x86")))
    }

    @Test
    fun featureRulesNeedTheFeatureToBeEnabled() {
        val demo = listOf(Rule("allow", features = mapOf("is_demo_user" to true)))
        assertFalse(RuleEvaluator.allows(demo, linux))
        assertTrue(RuleEvaluator.allows(demo, linux.copy(features = mapOf("is_demo_user" to true))))
    }
}

class MavenCoordinateTest {
    @Test
    fun buildsRepositoryPaths() {
        val c = MavenCoordinate.parse("org.ow2.asm:asm:9.6")!!
        assertEquals("org/ow2/asm/asm/9.6/asm-9.6.jar", c.path)
        assertEquals("org.ow2.asm:asm", c.key)
    }

    @Test
    fun supportsClassifierAndExtension() {
        val c = MavenCoordinate.parse("org.lwjgl:lwjgl:3.3.3:natives-linux@zip")!!
        assertEquals("org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-linux.zip", c.path)
    }

    @Test
    fun rejectsMalformedNames() {
        assertNull(MavenCoordinate.parse("nonsense"))
        assertNull(MavenCoordinate.parse("a::c"))
    }
}

class LibraryResolverTest {
    private val linux = Environment(osName = "linux", osArch = "arm64")

    // Synthetic version JSON in the shape Mojang uses; hashes and URLs are made up.
    private val json = """
        {
          "id": "test", "mainClass": "net.example.Main",
          "libraries": [
            { "name": "com.example:plain:1.0",
              "downloads": { "artifact": { "path": "com/example/plain/1.0/plain-1.0.jar",
                "sha1": "aa", "size": 10, "url": "https://libs.example.invalid/plain-1.0.jar" } } },
            { "name": "com.example:windows-only:1.0",
              "rules": [ { "action": "allow", "os": { "name": "windows" } } ],
              "downloads": { "artifact": { "path": "com/example/windows-only/1.0/windows-only-1.0.jar",
                "sha1": "bb", "size": 10, "url": "https://libs.example.invalid/w.jar" } } },
            { "name": "com.example:native-lib:2.0",
              "natives": { "linux": "natives-linux-${'$'}{arch}", "windows": "natives-windows" },
              "downloads": { "classifiers": {
                "natives-linux-64": { "sha1": "cc", "size": 20, "url": "https://libs.example.invalid/n.jar" } } } },
            { "name": "net.example:loader:0.1", "url": "https://maven.example.invalid" },
            { "name": "com.example:no-source:1.0" }
          ]
        }
    """.trimIndent()

    private val version = VersionJsonParser.parse(json)

    @Test
    fun resolvesArtifactsAndSkipsLibrariesThatDoNotApply() {
        val result = LibraryResolver.resolve(version.libraries, linux)
        val paths = result.files.map { it.path }

        assertTrue("com/example/plain/1.0/plain-1.0.jar" in paths)
        assertFalse(paths.any { it.contains("windows-only") })
    }

    @Test
    fun mapsNativeClassifiersWithTheArchPlaceholder() {
        val native = LibraryResolver.resolve(version.libraries, linux).files.single { it.isNative }
        assertEquals("com/example/native-lib/2.0/native-lib-2.0-natives-linux-64.jar", native.path)
        assertEquals("cc", native.sha1)
    }

    @Test
    fun mavenStyleLibrariesGetAUrlFromTheirRepository() {
        val loader = LibraryResolver.resolve(version.libraries, linux).files.single { it.key == "net.example:loader" }
        assertEquals("https://maven.example.invalid/net/example/loader/0.1/loader-0.1.jar", loader.url)
        assertNull(loader.sha1)
    }

    @Test
    fun reportsLibrariesWithNoDownloadInformation() {
        assertEquals(listOf("com.example:no-source:1.0"), LibraryResolver.resolve(version.libraries, linux).unresolved)
    }

    @Test
    fun loaderLibraryWinsOverTheSameArtifactInTheParent() {
        val parent = VersionJsonParser.parse(
            """{"id":"1.0","mainClass":"p.Main","libraries":[
                {"name":"org.ow2.asm:asm:9.0","downloads":{"artifact":{"path":"org/ow2/asm/asm/9.0/asm-9.0.jar","url":"https://x.invalid/9.0"}}}]}""",
        )
        val child = VersionJsonParser.parse(
            """{"id":"loader-1.0","inheritsFrom":"1.0","libraries":[
                {"name":"org.ow2.asm:asm:9.6","downloads":{"artifact":{"path":"org/ow2/asm/asm/9.6/asm-9.6.jar","url":"https://x.invalid/9.6"}}}]}""",
        )
        val merged = child.mergedOnto(parent)

        val asm = LibraryResolver.resolve(merged.libraries, linux).files.filter { it.key == "org.ow2.asm:asm" }
        assertEquals(listOf("org/ow2/asm/asm/9.6/asm-9.6.jar"), asm.map { it.path })
        assertEquals("p.Main", merged.mainClass)
        assertNull(merged.inheritsFrom)
    }
}

package com.flintlauncher.android.minecraft.manifest

import org.junit.Assert.assertEquals
import org.junit.Test

class VersionMetaParserTest {

    // Synthetic fixtures shaped like a per-version JSON; component names are made up.
    @Test
    fun readsJavaMajorVersion() {
        val meta = VersionMetaParser.parse(
            """
            {
              "id": "26.2", "type": "release",
              "mainClass": "net.minecraft.client.main.Main",
              "javaVersion": { "component": "java-runtime-test", "majorVersion": 25 },
              "minimumLauncherVersion": 21
            }
            """.trimIndent(),
        )
        assertEquals(25, meta.requiredJavaMajor)
        assertEquals(VersionType.RELEASE, meta.type)
    }

    @Test
    fun missingJavaVersionAssumesJava8() {
        val meta = VersionMetaParser.parse("""{ "id": "1.6.4", "type": "release" }""")
        assertEquals(VersionMeta.LEGACY_JAVA_MAJOR, meta.requiredJavaMajor)
    }
}

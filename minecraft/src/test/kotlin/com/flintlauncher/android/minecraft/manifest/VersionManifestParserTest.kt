package com.flintlauncher.android.minecraft.manifest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class VersionManifestParserTest {

    // Synthetic fixture: the shape follows version_manifest_v2.json, but hashes,
    // URLs and dates are made up. It is not a copy of Mojang data.
    private val fixture = """
        {
          "latest": { "release": "26.2", "snapshot": "26.3-snapshot-9" },
          "versions": [
            { "id": "26.3-snapshot-9", "type": "snapshot", "url": "https://example.invalid/a.json",
              "time": "2026-08-17T10:00:00+00:00", "releaseTime": "2026-08-17T10:00:00+00:00",
              "sha1": "0000000000000000000000000000000000000001", "complianceLevel": 1 },
            { "id": "26.2", "type": "release", "url": "https://example.invalid/b.json",
              "time": "2026-06-16T09:00:00+00:00", "releaseTime": "2026-06-16T09:00:00+00:00",
              "sha1": "0000000000000000000000000000000000000002", "complianceLevel": 1 },
            { "id": "1.21.11", "type": "release", "url": "https://example.invalid/c.json",
              "time": "2025-12-09T09:00:00+00:00", "releaseTime": "2025-12-09T09:00:00+00:00",
              "sha1": "0000000000000000000000000000000000000003", "complianceLevel": 1 },
            { "id": "b1.7.3", "type": "old_beta", "url": "https://example.invalid/d.json",
              "time": "2011-07-08T00:00:00+00:00", "releaseTime": "2011-07-08T00:00:00+00:00" },
            { "id": "a1.0.0", "type": "old_alpha", "url": "https://example.invalid/e.json",
              "time": "2010-06-30T00:00:00+00:00", "releaseTime": "2010-06-30T00:00:00+00:00" },
            { "id": "exp-1", "type": "experiment", "url": "https://example.invalid/f.json",
              "time": "2026-01-01T00:00:00+00:00", "releaseTime": "2026-01-01T00:00:00+00:00",
              "someFutureField": true }
          ]
        }
    """.trimIndent()

    private val manifest = VersionManifestParser.parse(fixture)

    @Test
    fun parsesLatestPointers() {
        assertEquals("26.2", manifest.latest.release)
        assertEquals("26.3-snapshot-9", manifest.latest.snapshot)
        assertEquals(6, manifest.versions.size)
    }

    @Test
    fun filtersByType() {
        assertEquals(listOf("26.2", "1.21.11"), manifest.ofType(VersionType.RELEASE).map { it.id })
        assertEquals(listOf("26.3-snapshot-9"), manifest.ofType(VersionType.SNAPSHOT).map { it.id })
        assertEquals(
            listOf("b1.7.3", "a1.0.0"),
            manifest.ofType(VersionType.OLD_BETA, VersionType.OLD_ALPHA).map { it.id },
        )
    }

    @Test
    fun sortsNewestFirstAndOldestFirst() {
        assertEquals("26.3-snapshot-9", manifest.newestFirst().first().id)
        assertEquals("a1.0.0", manifest.oldestFirst().first().id)
    }

    @Test
    fun unknownTypeFallsBackAndUnknownKeysAreIgnored() {
        val entry = manifest.find("exp-1")
        assertNotNull(entry)
        assertEquals(VersionType.UNKNOWN, entry!!.type)
    }

    @Test
    fun v1StyleEntryHasNoHash() {
        val entry = manifest.find("b1.7.3")
        assertNotNull(entry)
        assertNull(entry!!.sha1)
        assertNull(entry.complianceLevel)
    }

    @Test
    fun parsesOffsetTimestamps() {
        assertEquals(Instant.parse("2026-06-16T09:00:00Z"), manifest.find("26.2")!!.releaseInstant)
    }
}

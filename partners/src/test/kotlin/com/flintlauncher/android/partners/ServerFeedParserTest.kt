package com.flintlauncher.android.partners

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerFeedParserTest {

    @Test
    fun defaultListHasExactlyFiveUniqueValidServers() {
        assertEquals(5, DefaultServers.list.size)
        assertEquals(5, DefaultServers.list.map { it.address.lowercase() }.toSet().size)
        // every bundled entry must survive the same validation remote feeds go through
        val roundTrip = ServerFeedParser.parse(
            DefaultServers.list.joinToString(prefix = """{"servers":[""", postfix = "]}") {
                """{"name":"${it.name}","address":"${it.address}","website":"${it.website}"}"""
            },
        )
        assertEquals(DefaultServers.list, roundTrip)
    }

    @Test
    fun parsesAValidFeed() {
        val servers = ServerFeedParser.parse(
            """{"version":1,"servers":[
                {"name":"Alpha","address":"play.alpha.invalid","edition":"java","website":"https://alpha.invalid"},
                {"name":"Beta","address":"beta.invalid:19132","edition":"bedrock"}
            ]}""",
        )
        assertEquals(listOf("Alpha", "Beta"), servers.map { it.name })
        assertEquals(Edition.BEDROCK, servers[1].edition)
        assertNull(servers[1].website)
    }

    @Test
    fun dropsInvalidEntriesAndUnsafeWebsites() {
        val servers = ServerFeedParser.parse(
            """{"servers":[
                {"name":"","address":"empty-name.invalid"},
                {"name":"Scheme","address":"http://scheme.invalid"},
                {"name":"Spaces","address":"has space.invalid"},
                {"name":"BadEdition","address":"edition.invalid","edition":"console"},
                {"name":"Insecure","address":"insecure.invalid","website":"http://insecure.invalid"},
                {"name":"Script","address":"script.invalid","website":"javascript:alert(1)"}
            ]}""",
        )
        assertEquals(listOf("Insecure", "Script"), servers.map { it.name })
        assertTrue(servers.all { it.website == null })
    }

    @Test
    fun removesDuplicatesIgnoringCaseAndAppliesLimit() {
        val feed = """{"servers":[
            {"name":"One","address":"a.invalid"},
            {"name":"One again","address":"A.INVALID"},
            {"name":"Two","address":"b.invalid"},
            {"name":"Three","address":"c.invalid"}
        ]}"""
        assertEquals(listOf("One", "Two", "Three"), ServerFeedParser.parse(feed).map { it.name })
        assertEquals(listOf("One", "Two"), ServerFeedParser.parse(feed, limit = 2).map { it.name })
    }

    @Test
    fun malformedFeedGivesEmptyList() {
        assertTrue(ServerFeedParser.parse("not json").isEmpty())
        assertTrue(ServerFeedParser.parse("""{"servers":"nope"}""").isEmpty())
        assertTrue(ServerFeedParser.parse("[]").isEmpty())
    }
}

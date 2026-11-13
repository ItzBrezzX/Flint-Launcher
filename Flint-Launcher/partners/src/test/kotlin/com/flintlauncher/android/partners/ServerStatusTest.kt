package com.flintlauncher.android.partners

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerStatusParserTest {
    @Test
    fun onlineWithPlayers() {
        val result = ServerStatusParser.parse("""{"online":true,"players":{"online":71,"max":100},"extra":1}""")
        assertEquals(ServerStatusResult.Online(71, 100), result)
    }

    @Test
    fun onlineWithoutPlayerInfoKeepsCountUnknown() {
        assertEquals(ServerStatusResult.Online(null, null), ServerStatusParser.parse("""{"online":true}"""))
    }

    @Test
    fun offline() {
        assertEquals(ServerStatusResult.Offline, ServerStatusParser.parse("""{"online":false}"""))
    }

    @Test
    fun anythingUnexpectedIsUnknownNeverOnline() {
        assertTrue(ServerStatusParser.parse("""{"players":{"online":5}}""") is ServerStatusResult.Unknown)
        assertTrue(ServerStatusParser.parse("""{"online":"yes"}""") is ServerStatusResult.Unknown)
        assertTrue(ServerStatusParser.parse("garbage") is ServerStatusResult.Unknown)
        assertTrue(ServerStatusParser.parse("[1,2]") is ServerStatusResult.Unknown)
    }
}

class McStatusClientTest {
    private val server = MockWebServer()

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    private fun client(): McStatusClient {
        server.start()
        return McStatusClient(OkHttpClient(), server.url("/v2/"))
    }

    private val java = ServerEntry("Test", "mc.example.invalid", Edition.JAVA)

    @Test
    fun usesTheJavaPathAndParsesPlayers() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"online":true,"players":{"online":12,"max":50}}"""))
        val result = client().status(java)

        assertEquals(ServerStatusResult.Online(12, 50), result)
        assertEquals("/v2/status/java/mc.example.invalid", server.takeRequest().path)
    }

    @Test
    fun bedrockEntriesUseTheBedrockPath() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"online":false}"""))
        val result = client().status(ServerEntry("B", "bed.example.invalid:19132", Edition.BEDROCK))

        assertEquals(ServerStatusResult.Offline, result)
        assertEquals("/v2/status/bedrock/bed.example.invalid:19132", server.takeRequest().path)
    }

    @Test
    fun rateLimitAndServerErrorsAreUnknownNotOffline() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429))
        server.enqueue(MockResponse().setResponseCode(500))
        val client = client()

        assertEquals(ServerStatusResult.Unknown("Rate limited"), client.status(java))
        assertEquals(ServerStatusResult.Unknown("HTTP 500"), client.status(java))
    }

    @Test
    fun networkFailureIsUnknown() = runBlocking {
        val client = client()
        server.shutdown()

        assertTrue(client.status(java) is ServerStatusResult.Unknown)
    }
}

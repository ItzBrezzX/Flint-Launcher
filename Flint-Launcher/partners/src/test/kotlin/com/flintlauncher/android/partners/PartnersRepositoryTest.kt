package com.flintlauncher.android.partners

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class PartnersRepositoryTest {

    private fun entries(n: Int) = (1..n).map { ServerEntry("S$it", "s$it.invalid") }

    private class CountingClient(
        private val result: ServerStatusResult = ServerStatusResult.Online(1, 10),
    ) : ServerStatusClient {
        val calls = AtomicInteger()
        private val running = AtomicInteger()
        val maxRunning = AtomicInteger()

        override suspend fun status(entry: ServerEntry): ServerStatusResult {
            calls.incrementAndGet()
            val now = running.incrementAndGet()
            maxRunning.updateAndGet { maxOf(it, now) }
            delay(20)
            running.decrementAndGet()
            return result
        }
    }

    @Test
    fun loadsAtMostTheLimitInFeedOrder() = runBlocking {
        val client = CountingClient()
        val repo = PartnersRepository(feed = { entries(9) }, statusClient = client, limit = 5)

        val result = repo.load()

        assertEquals(listOf("S1", "S2", "S3", "S4", "S5"), result.map { it.entry.name })
        assertEquals(5, client.calls.get())
    }

    @Test
    fun neverExceedsTheConcurrencyCap() = runBlocking {
        val client = CountingClient()
        PartnersRepository(feed = { entries(5) }, statusClient = client, maxConcurrency = 2).load()

        assertTrue("max concurrent was ${client.maxRunning.get()}", client.maxRunning.get() <= 2)
    }

    @Test
    fun cachesWithinTtlAndRefreshesWhenForcedOrExpired() = runBlocking {
        var now = 0L
        val client = CountingClient()
        val repo = PartnersRepository(
            feed = { entries(2) },
            statusClient = client,
            cacheTtlMillis = 1_000,
            clock = { now },
        )

        repo.load()
        repo.load()
        assertEquals(2, client.calls.get())          // second call served from cache

        repo.load(forceRefresh = true)
        assertEquals(4, client.calls.get())          // forced refresh queries again

        now = 5_000
        repo.load()
        assertEquals(6, client.calls.get())          // expired cache queries again
    }

    @Test
    fun unknownStatusIsPassedThroughNotReplaced() = runBlocking {
        val repo = PartnersRepository(
            feed = { entries(1) },
            statusClient = CountingClient(ServerStatusResult.Unknown("Rate limited")),
        )
        assertEquals(ServerStatusResult.Unknown("Rate limited"), repo.load().single().status)
    }
}

class RemoteServerFeedTest {
    private val server = MockWebServer()
    private val fallback = ServerFeed { listOf(ServerEntry("Fallback", "fallback.invalid")) }

    private fun feed(): RemoteServerFeed {
        server.start()
        return RemoteServerFeed(OkHttpClient(), server.url("/servers.json").toString(), fallback, allowInsecure = true)
    }

    @Test
    fun usesTheRemoteListWhenItIsValid() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"servers":[{"name":"Remote","address":"remote.invalid"}]}"""))
        val result = feed().load()
        server.shutdown()
        assertEquals(listOf("Remote"), result.map { it.name })
    }

    @Test
    fun fallsBackOnServerErrorOrEmptyFeed() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setBody("""{"servers":[]}"""))
        val feed = feed()
        val first = feed.load()
        val second = feed.load()
        server.shutdown()
        assertEquals("Fallback", first.single().name)
        assertEquals("Fallback", second.single().name)
    }

    @Test
    fun refusesNonHttpsUrlsByDefault() = runBlocking {
        val feed = RemoteServerFeed(OkHttpClient(), "http://example.invalid/servers.json", fallback)
        assertEquals("Fallback", feed.load().single().name)
    }
}

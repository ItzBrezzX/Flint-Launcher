package com.flintlauncher.android.partners

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/**
 * Loads the server list and their live status. Status lookups run with limited
 * concurrency (the status API rate-limits per IP) and results are cached briefly
 * so reopening the screen does not re-query every server.
 */
class PartnersRepository(
    private val feed: ServerFeed = BundledServerFeed,
    private val statusClient: ServerStatusClient,
    private val limit: Int = DefaultServers.DEFAULT_LIMIT,
    private val maxConcurrency: Int = 2,
    private val cacheTtlMillis: Long = 60_000L,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private var cached: List<ServerWithStatus>? = null
    private var cachedAt = 0L

    suspend fun load(forceRefresh: Boolean = false): List<ServerWithStatus> = mutex.withLock {
        val now = clock()
        val existing = cached
        if (!forceRefresh && existing != null && now - cachedAt < cacheTtlMillis) {
            return existing
        }
        val servers = feed.load().take(limit)
        val permits = Semaphore(maxConcurrency)
        val fresh = coroutineScope {
            servers.map { entry ->
                async { ServerWithStatus(entry, permits.withPermit { statusClient.status(entry) }) }
            }.awaitAll()
        }
        cached = fresh
        cachedAt = clock()
        fresh
    }
}

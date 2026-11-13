package com.flintlauncher.android.partners

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import kotlin.coroutines.cancellation.CancellationException

fun interface ServerFeed {
    suspend fun load(): List<ServerEntry>
}

object BundledServerFeed : ServerFeed {
    override suspend fun load(): List<ServerEntry> = DefaultServers.list
}

/**
 * Parses `{"version":1,"servers":[{"name","address","edition","website"}]}`.
 * Bad entries are skipped, not trusted: addresses must be a plain host[:port],
 * websites must be https, duplicates (by address) are dropped, and the list is
 * capped at [limit].
 */
object ServerFeedParser {
    private val json = Json { ignoreUnknownKeys = true }
    private val addressPattern = Regex("^[A-Za-z0-9]([A-Za-z0-9.-]*[A-Za-z0-9])?(:[0-9]{1,5})?$")

    fun parse(text: String, limit: Int = DefaultServers.DEFAULT_LIMIT): List<ServerEntry> {
        val array = try {
            json.parseToJsonElement(text).jsonObject["servers"]?.jsonArray
        } catch (e: Exception) {
            null
        } ?: return emptyList()

        val seen = mutableSetOf<String>()
        return array.asSequence()
            .mapNotNull { element ->
                try {
                    json.decodeFromJsonElement(ServerEntry.serializer(), element)
                } catch (e: Exception) {
                    null
                }
            }
            .filter { it.name.isNotBlank() && addressPattern.matches(it.address) }
            .map { it.copy(website = it.website?.takeIf { url -> isSafeWebsite(url) }) }
            .filter { seen.add(it.address.lowercase()) }
            .take(limit)
            .toList()
    }

    private fun isSafeWebsite(url: String): Boolean = try {
        val uri = URI(url)
        uri.scheme == "https" && !uri.host.isNullOrBlank()
    } catch (e: Exception) {
        false
    }
}

/** Fetches a configurable remote feed; any failure or empty result falls back to [fallback]. */
class RemoteServerFeed(
    private val client: OkHttpClient,
    private val url: String,
    private val fallback: ServerFeed = BundledServerFeed,
    private val limit: Int = DefaultServers.DEFAULT_LIMIT,
    private val allowInsecure: Boolean = false, // tests only
) : ServerFeed {
    override suspend fun load(): List<ServerEntry> {
        val remote: List<ServerEntry> = try {
            if (!allowInsecure && !url.startsWith("https://")) {
                emptyList<ServerEntry>()
            } else {
                withContext(Dispatchers.IO) {
                    client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                        if (response.isSuccessful) {
                            ServerFeedParser.parse(response.body?.string().orEmpty(), limit)
                        } else {
                            emptyList<ServerEntry>()
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList<ServerEntry>()
        }
        return remote.ifEmpty { fallback.load() }
    }
}

package com.flintlauncher.android.partners

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

fun interface ServerStatusClient {
    suspend fun status(entry: ServerEntry): ServerStatusResult
}

/**
 * Reads the fields shared by the mcstatus.io v2 and mcsrvstat.us v3 responses:
 * `online` (boolean) and `players.online` / `players.max`. Anything unexpected is
 * Unknown, never a guess.
 */
object ServerStatusParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(body: String): ServerStatusResult = try {
        val root = json.parseToJsonElement(body).jsonObject
        val online = root["online"]?.jsonPrimitive?.booleanOrNull
        when {
            online == null -> ServerStatusResult.Unknown("Unexpected response")
            !online -> ServerStatusResult.Offline
            else -> {
                val players = root["players"] as? JsonObject
                ServerStatusResult.Online(
                    players = players?.get("online")?.jsonPrimitive?.intOrNull,
                    max = players?.get("max")?.jsonPrimitive?.intOrNull,
                )
            }
        }
    } catch (e: Exception) {
        ServerStatusResult.Unknown("Unreadable response")
    }
}

/**
 * Status lookups through the public mcstatus.io API (`GET /v2/status/{java|bedrock}/{address}`).
 * The API allows about 5 requests per second per client IP, so callers must limit concurrency.
 * The user's IP address is visible to that service; no account data is sent.
 */
class McStatusClient(
    client: OkHttpClient,
    private val baseUrl: HttpUrl = "https://api.mcstatus.io/v2/".toHttpUrl(),
) : ServerStatusClient {
    private val http = client.newBuilder().callTimeout(8, TimeUnit.SECONDS).build()

    override suspend fun status(entry: ServerEntry): ServerStatusResult {
        val url = baseUrl.newBuilder()
            .addPathSegment("status")
            .addPathSegment(if (entry.edition == Edition.BEDROCK) "bedrock" else "java")
            .addPathSegment(entry.address)
            .build()
        return try {
            withContext(Dispatchers.IO) {
                http.newCall(Request.Builder().url(url).build()).await().use { response ->
                    when {
                        response.code == 429 -> ServerStatusResult.Unknown("Rate limited")
                        !response.isSuccessful -> ServerStatusResult.Unknown("HTTP ${response.code}")
                        else -> ServerStatusParser.parse(response.body?.string().orEmpty())
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            ServerStatusResult.Unknown("Network error")
        }
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            if (continuation.isActive) continuation.resume(response) else response.close()
        }
    })
}

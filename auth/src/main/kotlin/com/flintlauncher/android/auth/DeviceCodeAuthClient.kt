package com.flintlauncher.android.auth

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.long
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Device-code (RFC 8628) sign-in for devices without a convenient browser, or as a
 * fallback when the custom-scheme PKCE redirect cannot be handled. The user opens
 * [DeviceCodeChallenge.verificationUri] on any device, enters [userCode], and approves;
 * this client polls the token endpoint until approval, expiry, or denial.
 *
 * No passwords are handled. The device code is short-lived and single-use.
 */
class DeviceCodeAuthClient(
    private val http: OkHttpClient,
    private val clientId: String,
    private val endpoints: AuthEndpoints = AuthEndpoints(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Step 1: request a device code. Returns everything the UI must show the user. */
    suspend fun requestDeviceCode(): DeviceCodeChallenge {
        val form = FormBody.Builder()
            .add("client_id", clientId)
            .add("scope", "XboxLive.signin offline_access")
            .build()
        val response = send("devicecode", Request.Builder().url(endpoints.deviceCode).post(form).build(), allowErrorBody = true)
        if (response.code !in 200..299) throw AuthException(AuthError.Http("devicecode", response.code))
        val body = parse("devicecode", response.body)
        return DeviceCodeChallenge(
            deviceCode = body.string("device_code", "devicecode"),
            userCode = body.string("user_code", "devicecode"),
            verificationUri = body["verification_uri"]?.jsonPrimitive?.contentOrNull
                ?: body["verification_url"]?.jsonPrimitive?.contentOrNull
                ?: "https://www.microsoft.com/link",
            verificationUriComplete = body["verification_uri_complete"]?.jsonPrimitive?.contentOrNull,
            message = body["message"]?.jsonPrimitive?.contentOrNull ?: "",
            expiresInSeconds = body["expires_in"]?.jsonPrimitive?.long ?: 900L,
            intervalSeconds = body["interval"]?.jsonPrimitive?.long ?: 5L,
        )
    }

    /**
     * Step 2: poll until the user approves. Emits [DeviceCodePollStatus] so the UI can
     * reflect waiting / slow-down / expired. Completes with [MicrosoftTokens] on approval.
     * The caller collects the flow; cancellation stops polling cleanly.
     */
    fun pollUntilApproved(challenge: DeviceCodeChallenge): Flow<DeviceCodePollStatus> = flow {
        val deadline = System.currentTimeMillis() + challenge.expiresInSeconds * 1000L
        var interval = challenge.intervalSeconds.coerceAtLeast(1L)
        emit(DeviceCodePollStatus.Waiting(interval))
        while (System.currentTimeMillis() < deadline) {
            delay(interval * 1000L)
            val result = pollOnce(challenge.deviceCode)
            when (result) {
                is PollOutcome.Tokens -> {
                    emit(DeviceCodePollStatus.Approved(result.tokens))
                    return@flow
                }
                is PollOutcome.Pending -> emit(DeviceCodePollStatus.Waiting(interval))
                is PollOutcome.SlowDown -> {
                    interval += 5L
                    emit(DeviceCodePollStatus.SlowedDown(interval))
                }
                is PollOutcome.Denied -> {
                    emit(DeviceCodePollStatus.Denied)
                    return@flow
                }
                is PollOutcome.Expired -> {
                    emit(DeviceCodePollStatus.Expired)
                    return@flow
                }
                is PollOutcome.Error -> {
                    emit(DeviceCodePollStatus.Error(result.message))
                    return@flow
                }
            }
        }
        emit(DeviceCodePollStatus.Expired)
    }

    private sealed interface PollOutcome {
        data class Tokens(val tokens: MicrosoftTokens) : PollOutcome
        data object Pending : PollOutcome
        data object SlowDown : PollOutcome
        data object Denied : PollOutcome
        data object Expired : PollOutcome
        data class Error(val message: String) : PollOutcome
    }

    private suspend fun pollOnce(deviceCode: String): PollOutcome {
        val form = FormBody.Builder()
            .add("grant_type", DEVICE_CODE_GRANT)
            .add("client_id", clientId)
            .add("device_code", deviceCode)
            .build()
        val response = send("devicecode-token", Request.Builder().url(endpoints.msaToken).post(form).build(), allowErrorBody = true)
        if (response.code in 200..299) {
            val body = parse("microsoft", response.body)
            return PollOutcome.Tokens(
                MicrosoftTokens(
                    accessToken = body.string("access_token", "microsoft"),
                    refreshToken = body["refresh_token"]?.jsonPrimitive?.contentOrNull,
                    expiresInSeconds = body["expires_in"]?.jsonPrimitive?.long ?: 0L,
                ),
            )
        }
        val error = try {
            json.parseToJsonElement(response.body).jsonObject["error"]?.jsonPrimitive?.contentOrNull
        } catch (e: Exception) {
            null
        }
        return when (error) {
            "authorization_pending" -> PollOutcome.Pending
            "slow_down" -> PollOutcome.SlowDown
            "expired_token" -> PollOutcome.Expired
            "access_denied" -> PollOutcome.Denied
            "bad_verification_code" -> PollOutcome.Error("The device code was rejected. Start sign-in again.")
            else -> PollOutcome.Error("Device sign-in failed (HTTP ${response.code}).")
        }
    }

    private class Reply(val code: Int, val body: String)

    private suspend fun send(step: String, request: Request, allowErrorBody: Boolean = false): Reply =
        try {
            withContext(Dispatchers.IO) {
                http.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (!allowErrorBody && !response.isSuccessful) throw AuthException(AuthError.Http(step, response.code))
                    Reply(response.code, text)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: AuthException) {
            throw e
        } catch (e: IOException) {
            throw AuthException(AuthError.Network(step))
        }

    private fun parse(step: String, text: String) = try {
        json.parseToJsonElement(text).jsonObject
    } catch (e: Exception) {
        throw AuthException(AuthError.Malformed(step))
    }

    private fun kotlinx.serialization.json.JsonObject.string(key: String, step: String): String =
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() } ?: throw AuthException(AuthError.Malformed(step))

    private companion object {
        const val DEVICE_CODE_GRANT = "urn:ietf:params:oauth:grant-type:device_code"
    }
}

data class DeviceCodeChallenge(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val verificationUriComplete: String? = null,
    val message: String,
    val expiresInSeconds: Long,
    val intervalSeconds: Long,
) {
    val userCodeDisplay: String get() = userCode.chunked(4).joinToString("-")
}

sealed interface DeviceCodePollStatus {
    data class Waiting(val intervalSeconds: Long) : DeviceCodePollStatus
    data class SlowedDown(val intervalSeconds: Long) : DeviceCodePollStatus
    data class Approved(val tokens: MicrosoftTokens) : DeviceCodePollStatus
    data object Denied : DeviceCodePollStatus
    data object Expired : DeviceCodePollStatus
    data class Error(val message: String) : DeviceCodePollStatus
}

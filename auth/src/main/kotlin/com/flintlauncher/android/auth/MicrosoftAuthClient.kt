package com.flintlauncher.android.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

data class AuthEndpoints(
    val msaAuthorize: String = "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize",
    val msaToken: String = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token",
    val xboxUserAuth: String = "https://user.auth.xboxlive.com/user/authenticate",
    val xsts: String = "https://xsts.auth.xboxlive.com/xsts/authorize",
    val minecraftLogin: String = "https://api.minecraftservices.com/authentication/login_with_xbox",
    val entitlements: String = "https://api.minecraftservices.com/entitlements/mcstore",
    val profile: String = "https://api.minecraftservices.com/minecraft/profile",
)

/**
 * The official sign-in chain: Microsoft OAuth (authorization code + PKCE), Xbox Live,
 * XSTS, Minecraft Services, entitlement check, profile. Passwords are never handled:
 * the user signs in on Microsoft's page and only a one-time code returns to the app.
 *
 * [clientId] is YOUR Azure app registration, and Mojang must have approved it for
 * Minecraft services. There is deliberately no default. Endpoint URLs are from the
 * community-documented scheme and must be re-verified against current documentation.
 */
class MicrosoftAuthClient(
    private val http: OkHttpClient,
    private val clientId: String,
    private val redirectUri: String,
    private val endpoints: AuthEndpoints = AuthEndpoints(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun authorizationUrl(challenge: String, state: String): String =
        endpoints.msaAuthorize.toHttpUrl().newBuilder()
            .addQueryParameter("client_id", clientId)
            .addQueryParameter("response_type", "code")
            .addQueryParameter("redirect_uri", redirectUri)
            .addQueryParameter("scope", "XboxLive.signin offline_access")
            .addQueryParameter("code_challenge", challenge)
            .addQueryParameter("code_challenge_method", "S256")
            .addQueryParameter("state", state)
            .addQueryParameter("prompt", "select_account")
            .build()
            .toString()

    suspend fun exchangeCode(code: String, verifier: String): MicrosoftTokens = tokenRequest(
        FormBody.Builder()
            .add("client_id", clientId)
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", redirectUri)
            .add("code_verifier", verifier)
            .add("scope", "XboxLive.signin offline_access")
            .build(),
    )

    suspend fun refresh(refreshToken: String): MicrosoftTokens = tokenRequest(
        FormBody.Builder()
            .add("client_id", clientId)
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .add("scope", "XboxLive.signin offline_access")
            .build(),
    )

    /** Runs Xbox, XSTS, Minecraft login, entitlement and profile steps for a Microsoft access token. */
    suspend fun loginMinecraft(microsoftAccessToken: String): MinecraftSession {
        val xbl = xboxUserToken(microsoftAccessToken)
        val xsts = xstsToken(xbl.first)
        val identityToken = "XBL3.0 x=${xsts.second};${xsts.first}"
        val (mcToken, expires) = minecraftToken(identityToken)
        val entitled = entitlementConfirmed(mcToken)
        val profile = fetchProfile(mcToken)
        return MinecraftSession(mcToken, expires, profile, entitled)
    }

    // ---------------------------------------------------------------- steps

    private suspend fun tokenRequest(form: FormBody): MicrosoftTokens {
        val response = send("microsoft", Request.Builder().url(endpoints.msaToken).post(form).build(), allowErrorBody = true)
        if (response.code !in 200..299) {
            // The error body may not be JSON (for example an HTML gateway error), so parse defensively.
            val error = try {
                json.parseToJsonElement(response.body).jsonObject["error"]?.jsonPrimitive?.contentOrNull
            } catch (e: Exception) {
                null
            }
            if (error == "invalid_grant") throw AuthException(AuthError.InvalidGrant)
            throw AuthException(AuthError.Http("microsoft", response.code))
        }
        val body = parse("microsoft", response.body)
        return MicrosoftTokens(
            accessToken = body.string("access_token", "microsoft"),
            refreshToken = body["refresh_token"]?.jsonPrimitive?.contentOrNull,
            expiresInSeconds = body["expires_in"]?.jsonPrimitive?.long ?: 0L,
        )
    }

    /** Returns (token, userHash). */
    private suspend fun xboxUserToken(msaToken: String): Pair<String, String> {
        val payload = buildJsonObject {
            putJsonObject("Properties") {
                put("AuthMethod", "RPS")
                put("SiteName", "user.auth.xboxlive.com")
                put("RpsTicket", "d=$msaToken")
            }
            put("RelyingParty", "http://auth.xboxlive.com")
            put("TokenType", "JWT")
        }
        val response = send("xbox", jsonPost(endpoints.xboxUserAuth, payload.toString()))
        return tokenAndHash(parse("xbox", response.body), "xbox")
    }

    private suspend fun xstsToken(xblToken: String): Pair<String, String> {
        val payload = buildJsonObject {
            putJsonObject("Properties") {
                put("SandboxId", "RETAIL")
                put("UserTokens", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(xblToken)) })
            }
            put("RelyingParty", "rp://api.minecraftservices.com/")
            put("TokenType", "JWT")
        }
        val response = send("xsts", jsonPost(endpoints.xsts, payload.toString()), allowErrorBody = true)
        val body = response.body
        if (response.code == 401) throw AuthException(mapXstsError(xErr(body)))
        if (response.code !in 200..299) throw AuthException(AuthError.Http("xsts", response.code))
        return tokenAndHash(parse("xsts", body), "xsts")
    }

    private suspend fun minecraftToken(identityToken: String): Pair<String, Long> {
        val payload = buildJsonObject { put("identityToken", identityToken) }
        val response = send("minecraft", jsonPost(endpoints.minecraftLogin, payload.toString()))
        val body = parse("minecraft", response.body)
        return body.string("access_token", "minecraft") to (body["expires_in"]?.jsonPrimitive?.long ?: 0L)
    }

    private suspend fun entitlementConfirmed(mcToken: String): Boolean {
        val response = send(
            "entitlements",
            Request.Builder().url(endpoints.entitlements).header("Authorization", "Bearer $mcToken").build(),
            allowErrorBody = true,
        )
        if (response.code !in 200..299) return false
        val items = try {
            parse("entitlements", response.body)["items"]?.jsonArray ?: JsonArray(emptyList())
        } catch (e: AuthException) {
            return false
        }
        return items.any {
            val name = (it as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull
            name == "game_minecraft" || name == "product_minecraft"
        }
    }

    private suspend fun fetchProfile(mcToken: String): MinecraftProfile {
        val response = send(
            "profile",
            Request.Builder().url(endpoints.profile).header("Authorization", "Bearer $mcToken").build(),
            allowErrorBody = true,
        )
        if (response.code == 404) throw AuthException(AuthError.NoMinecraftProfile)
        if (response.code !in 200..299) throw AuthException(AuthError.Http("profile", response.code))
        val body = parse("profile", response.body)
        val skins = body["skins"]?.jsonArray?.mapNotNull { element ->
            val skin = element as? JsonObject ?: return@mapNotNull null
            val url = skin["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            SkinInfo(url, skin["variant"]?.jsonPrimitive?.contentOrNull, skin["state"]?.jsonPrimitive?.contentOrNull == "ACTIVE")
        }.orEmpty()
        return MinecraftProfile(body.string("id", "profile"), body.string("name", "profile"), skins)
    }

    // -------------------------------------------------------------- helpers

    private class Reply(val code: Int, val body: String)

    private fun jsonPost(url: String, payload: String): Request =
        Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

    /** Never puts a response body or token into an exception message. */
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

    private fun parse(step: String, text: String): JsonObject = try {
        json.parseToJsonElement(text).jsonObject
    } catch (e: Exception) {
        throw AuthException(AuthError.Malformed(step))
    }

    private fun JsonObject.string(key: String, step: String): String =
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() } ?: throw AuthException(AuthError.Malformed(step))

    private fun tokenAndHash(body: JsonObject, step: String): Pair<String, String> {
        val hash = body["DisplayClaims"]?.jsonObject?.get("xui")?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("uhs")?.jsonPrimitive?.contentOrNull
            ?: throw AuthException(AuthError.Malformed(step))
        return body.string("Token", step) to hash
    }

    private fun xErr(body: String): Long? = try {
        json.parseToJsonElement(body).jsonObject["XErr"]?.jsonPrimitive?.long
    } catch (e: Exception) {
        null
    }

    companion object {
        /**
         * XSTS error codes as widely documented by the community. Recalled, not read from a
         * primary source in this project yet: verify against current documentation before release.
         */
        fun mapXstsError(code: Long?): AuthError = when (code) {
            2148916233L -> AuthError.NoXboxAccount
            2148916235L -> AuthError.XboxUnavailableInCountry
            2148916236L, 2148916237L -> AuthError.AdultVerificationRequired
            2148916238L -> AuthError.ChildAccount
            else -> AuthError.XstsFailed(code)
        }
    }
}

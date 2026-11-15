package com.flintlauncher.android.auth

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class PkceTest {
    @Test
    fun matchesTheRfc7636ExampleVector() {
        // Test vector from RFC 7636, appendix B.
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            Pkce.challengeFor("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"),
        )
    }

    @Test
    fun generatedPairsAreLongUrlSafeAndConsistent() {
        val pair = Pkce.generate()
        assertEquals(64, pair.verifier.length)
        assertTrue(pair.verifier.all { it.isLetterOrDigit() || it == '-' || it == '_' })
        assertEquals(Pkce.challengeFor(pair.verifier), pair.challenge)
        assertNotEquals(pair.verifier, Pkce.generate().verifier)
    }
}

class OfflineAccountTest {
    @Test
    fun uuidIsDeterministicNameBasedAndPerName() {
        val a = OfflineAccount.uuid("Steve")
        assertEquals(a, OfflineAccount.uuid("Steve"))
        assertNotEquals(a, OfflineAccount.uuid("Alex"))
        assertEquals(3, a.version())
    }
}

class MicrosoftAuthClientTest {
    private val server = MockWebServer()
    private val requests = CopyOnWriteArrayList<RecordedRequest>()
    private val replies = java.util.concurrent.ConcurrentHashMap<String, MockResponse>()

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests.add(request)
                return replies[request.path] ?: MockResponse().setResponseCode(404)
            }
        }
        server.start()
        reply("/msa/token", """{"access_token":"MSA_TOKEN","refresh_token":"REFRESH","expires_in":3600}""")
        reply("/xbl", """{"Token":"XBL_TOKEN","DisplayClaims":{"xui":[{"uhs":"UHS123"}]}}""")
        reply("/xsts", """{"Token":"XSTS_TOKEN","DisplayClaims":{"xui":[{"uhs":"UHS123"}]}}""")
        reply("/mc/login", """{"access_token":"MC_TOKEN","expires_in":86400}""")
        reply("/mc/entitlements", """{"items":[{"name":"product_minecraft"},{"name":"game_minecraft"}]}""")
        reply(
            "/mc/profile",
            """{"id":"069a79f444e94726a5befca90e38aaf5","name":"Notch",
               "skins":[{"id":"1","state":"ACTIVE","url":"https://textures.example.invalid/skin","variant":"CLASSIC"}]}""",
        )
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    private fun reply(path: String, body: String, code: Int = 200) {
        replies[path] = MockResponse().setResponseCode(code).setBody(body)
    }

    private fun client(): MicrosoftAuthClient {
        fun url(path: String) = server.url(path).toString()
        return MicrosoftAuthClient(
            http = OkHttpClient(),
            clientId = "test-client-id",
            redirectUri = "flint://auth",
            endpoints = AuthEndpoints(
                msaAuthorize = url("/msa/authorize"),
                msaToken = url("/msa/token"),
                msaDeviceCode = url("/msa/devicecode"),
                xboxUserAuth = url("/xbl"),
                xsts = url("/xsts"),
                minecraftLogin = url("/mc/login"),
                entitlements = url("/mc/entitlements"),
                profile = url("/mc/profile"),
            ),
        )
    }

    private fun error(block: suspend () -> Unit): AuthError {
        try {
            runBlocking { block() }
        } catch (e: AuthException) {
            return e.error
        }
        fail("expected AuthException")
        throw AssertionError()
    }

    @Test
    fun authorizationUrlCarriesPkceAndTheRequiredScope() {
        val url = client().authorizationUrl("CHALLENGE", "STATE")
        assertTrue(url.contains("client_id=test-client-id"))
        assertTrue(url.contains("response_type=code"))
        assertTrue(url.contains("code_challenge=CHALLENGE"))
        assertTrue(url.contains("code_challenge_method=S256"))
        assertTrue(url.contains("state=STATE"))
        assertTrue(url.contains("scope=XboxLive.signin"))
        assertFalse(url.contains("client_secret"))
    }

    @Test
    fun exchangingTheCodeSendsTheVerifierAndNoSecret() = runBlocking {
        val tokens = client().exchangeCode("THE_CODE", "THE_VERIFIER")

        assertEquals("MSA_TOKEN", tokens.accessToken)
        assertEquals("REFRESH", tokens.refreshToken)
        val body = requests.single().body.readUtf8()
        assertTrue(body.contains("code=THE_CODE"))
        assertTrue(body.contains("code_verifier=THE_VERIFIER"))
        assertTrue(body.contains("grant_type=authorization_code"))
        assertFalse(body.contains("secret"))
    }

    @Test
    fun fullChainProducesAVerifiedSession() = runBlocking {
        val session = client().loginMinecraft("MSA_TOKEN")

        assertEquals("Notch", session.profile.name)
        assertEquals("069a79f444e94726a5befca90e38aaf5", session.profile.id)
        assertEquals("MC_TOKEN", session.accessToken)
        assertTrue(session.entitlementConfirmed)
        assertEquals("CLASSIC", session.profile.activeSkin!!.variant)

        val byPath = requests.associateBy { it.path }
        assertTrue(byPath["/xbl"]!!.body.readUtf8().contains("d=MSA_TOKEN"))
        assertTrue(byPath["/xsts"]!!.body.readUtf8().contains("XBL_TOKEN"))
        assertTrue(byPath["/mc/login"]!!.body.readUtf8().contains("XBL3.0 x=UHS123;XSTS_TOKEN"))
        assertEquals("Bearer MC_TOKEN", byPath["/mc/entitlements"]!!.getHeader("Authorization"))
        assertEquals("Bearer MC_TOKEN", byPath["/mc/profile"]!!.getHeader("Authorization"))
    }

    @Test
    fun anAccountWithoutAMinecraftProfileIsReportedAsNotOwningTheGame() {
        reply("/mc/profile", """{"error":"NOT_FOUND"}""", code = 404)
        assertEquals(AuthError.NoMinecraftProfile, error { client().loginMinecraft("t") })
    }

    @Test
    fun entitlementsMayBeMissingButTheProfileStillProvesAccess() = runBlocking {
        reply("/mc/entitlements", """{"items":[]}""")
        val session = client().loginMinecraft("t")
        assertFalse(session.entitlementConfirmed)
        assertEquals("Notch", session.profile.name)
    }

    @Test
    fun xstsRefusalsMapToReadableErrors() {
        for ((code, expected) in listOf(
            2148916233L to AuthError.NoXboxAccount,
            2148916235L to AuthError.XboxUnavailableInCountry,
            2148916238L to AuthError.ChildAccount,
        )) {
            reply("/xsts", """{"Identity":"0","XErr":$code,"Message":""}""", code = 401)
            assertEquals(expected, error { client().loginMinecraft("t") })
        }
        reply("/xsts", """{"XErr":42}""", code = 401)
        assertEquals(AuthError.XstsFailed(42L), error { client().loginMinecraft("t") })
        reply("/xsts", "<html>nope</html>", code = 401)
        assertEquals(AuthError.XstsFailed(null), error { client().loginMinecraft("t") })
    }

    @Test
    fun anExpiredRefreshTokenAsksTheUserToSignInAgain() {
        reply("/msa/token", """{"error":"invalid_grant","error_description":"expired"}""", code = 400)
        assertEquals(AuthError.InvalidGrant, error { client().refresh("old") })
    }

    @Test
    fun aNonJsonGatewayErrorIsAnHttpErrorNotACrash() {
        reply("/msa/token", "<html>bad gateway</html>", code = 502)
        assertEquals(AuthError.Http("microsoft", 502), error { client().refresh("old") })
    }

    @Test
    fun malformedSuccessResponsesAreReported() {
        reply("/xbl", """{"Token":"x"}""") // no user hash
        assertEquals(AuthError.Malformed("xbox"), error { client().loginMinecraft("t") })
    }

    @Test
    fun networkFailuresAreReportedPerStep() {
        val client = client()
        server.shutdown()
        assertEquals(AuthError.Network("xbox"), error { client.loginMinecraft("t") })
    }

    @Test
    fun tokensNeverAppearInErrorsOrToString() {
        reply("/mc/profile", """{"error":"boom"}""", code = 500)
        val e = error { client().loginMinecraft("SECRET_MSA_TOKEN") }
        assertFalse(e.userMessage.contains("TOKEN"))

        val tokens = MicrosoftTokens("SECRET_ACCESS", "SECRET_REFRESH", 60)
        assertFalse(tokens.toString().contains("SECRET"))
        val session = MinecraftSession("SECRET_MC", 60, MinecraftProfile("id", "Notch", emptyList()), true)
        assertFalse(session.toString().contains("SECRET"))
        assertNull(MinecraftProfile("id", "n", emptyList()).activeSkin)
    }

    // ------------------------------------------------------ device-code flow

    private fun deviceInfo(expiresIn: Long = 900, interval: Long = 5) = DeviceCodeInfo(
        deviceCode = "DEVICE_SECRET",
        userCode = "ABCD1234",
        verificationUri = "https://microsoft.com/link",
        expiresInSeconds = expiresIn,
        intervalSeconds = interval,
    )

    @Test
    fun deviceCodeRequestSendsClientIdAndScopeAndParsesTheReply() = runBlocking {
        reply(
            "/msa/devicecode",
            """{"device_code":"DEVICE_SECRET","user_code":"ABCD1234","verification_uri":"https://microsoft.com/link","expires_in":900,"interval":5}""",
        )
        val info = client().requestDeviceCode()

        assertEquals("ABCD1234", info.userCode)
        assertEquals("https://microsoft.com/link", info.verificationUri)
        assertEquals(5L, info.intervalSeconds)
        val body = requests.single().body.readUtf8()
        assertTrue(body.contains("client_id=test-client-id"))
        assertTrue(body.contains("scope=XboxLive.signin"))
        assertFalse(info.toString().contains("DEVICE_SECRET"))
    }

    @Test
    fun pollingKeepsWaitingWhilePendingThenReturnsTokens() = runBlocking {
        val answers = java.util.concurrent.atomic.AtomicInteger(0)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests.add(request)
                if (request.path != "/msa/token") return MockResponse().setResponseCode(404)
                return if (answers.getAndIncrement() < 2) {
                    MockResponse().setResponseCode(400).setBody("""{"error":"authorization_pending"}""")
                } else {
                    MockResponse().setResponseCode(200)
                        .setBody("""{"access_token":"MSA_TOKEN","refresh_token":"REFRESH","expires_in":3600}""")
                }
            }
        }
        val pauses = mutableListOf<Long>()
        val tokens = client().awaitDeviceCode(deviceInfo()) { pauses += it }

        assertEquals("REFRESH", tokens.refreshToken)
        assertEquals(3, requests.size)
        assertEquals(listOf(5000L, 5000L, 5000L), pauses)
        val body = requests.first().body.readUtf8()
        assertTrue(body.contains("grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Adevice_code"))
        assertTrue(body.contains("device_code=DEVICE_SECRET"))
    }

    @Test
    fun slowDownIncreasesTheInterval() = runBlocking {
        val answers = java.util.concurrent.atomic.AtomicInteger(0)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests.add(request)
                return if (answers.getAndIncrement() == 0) {
                    MockResponse().setResponseCode(400).setBody("""{"error":"slow_down"}""")
                } else {
                    MockResponse().setResponseCode(200).setBody("""{"access_token":"A","refresh_token":"R","expires_in":1}""")
                }
            }
        }
        val pauses = mutableListOf<Long>()
        client().awaitDeviceCode(deviceInfo()) { pauses += it }
        assertEquals(listOf(5000L, 10000L), pauses)
    }

    @Test
    fun declinedAndExpiredCodesMapToReadableErrors() {
        reply("/msa/token", """{"error":"authorization_declined"}""", code = 400)
        assertEquals(AuthError.AuthorizationDeclined, error { client().awaitDeviceCode(deviceInfo()) { } })

        reply("/msa/token", """{"error":"expired_token"}""", code = 400)
        assertEquals(AuthError.DeviceCodeExpired, error { client().awaitDeviceCode(deviceInfo()) { } })
    }

    @Test
    fun pollingGivesUpWhenTheCodeLifetimeRunsOut() {
        reply("/msa/token", """{"error":"authorization_pending"}""", code = 400)
        assertEquals(
            AuthError.DeviceCodeExpired,
            error { client().awaitDeviceCode(deviceInfo(expiresIn = 10, interval = 5)) { } },
        )
    }

    @Test
    fun refreshTokenSignInProducesAFreshSessionAndKeepsTheRotatedToken() = runBlocking {
        val (tokens, session) = client().signInWithRefreshToken("OLD_REFRESH")

        assertEquals("REFRESH", tokens.refreshToken)
        assertEquals("MC_TOKEN", session.accessToken)
        assertEquals("Notch", session.profile.name)
        val first = requests.first().body.readUtf8()
        assertTrue(first.contains("grant_type=refresh_token"))
        assertTrue(first.contains("refresh_token=OLD_REFRESH"))
    }
}

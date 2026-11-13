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
}

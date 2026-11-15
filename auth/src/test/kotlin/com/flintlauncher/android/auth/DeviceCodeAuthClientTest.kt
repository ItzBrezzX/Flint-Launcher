package com.flintlauncher.android.auth

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DeviceCodeAuthClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: DeviceCodeAuthClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val base = server.url("/").toString().trimEnd('/')
        client = DeviceCodeAuthClient(
            http = okhttp3.OkHttpClient(),
            clientId = "test-client-id",
            endpoints = AuthEndpoints(
                deviceCode = "$base/devicecode",
                msaToken = "$base/token",
            ),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun requestDeviceCodeParsesResponse() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "device_code": "DEVICE_CODE_VALUE",
                  "user_code": "ABCD1234",
                  "verification_uri": "https://www.microsoft.com/link",
                  "verification_uri_complete": "https://www.microsoft.com/link?code=ABCD1234",
                  "expires_in": 900,
                  "interval": 5,
                  "message": "To sign in, go to ..."
                }
                """.trimIndent(),
            ),
        )
        val challenge = client.requestDeviceCode()
        assertEquals("DEVICE_CODE_VALUE", challenge.deviceCode)
        assertEquals("ABCD1234", challenge.userCode)
        assertEquals("https://www.microsoft.com/link", challenge.verificationUri)
        assertEquals(900L, challenge.expiresInSeconds)
        assertEquals(5L, challenge.intervalSeconds)
        assertEquals("ABCD-1234", challenge.userCodeDisplay)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        val body = request.body.readUtf8()
        assertTrue(body.contains("client_id=test-client-id"))
        assertTrue(body.contains("scope=XboxLive.signin+offline_access"))
    }

    @Test
    fun requestDeviceCodeHttpErrorThrows() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        try {
            client.requestDeviceCode()
            org.junit.Assert.fail("expected AuthException")
        } catch (e: AuthException) {
            assertTrue(e.error is AuthError.Http)
        }
    }
}

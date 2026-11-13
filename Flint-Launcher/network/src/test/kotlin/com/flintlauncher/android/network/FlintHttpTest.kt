package com.flintlauncher.android.network

import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class FlintHttpTest {
    private val server = MockWebServer()

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun everyRequestCarriesTheUserAgent() {
        server.enqueue(MockResponse().setBody("ok"))
        server.start()
        val client = FlintHttp.create("FlintLauncher/test (Android)")

        client.newCall(Request.Builder().url(server.url("/")).build()).execute().close()

        assertEquals("FlintLauncher/test (Android)", server.takeRequest().getHeader("User-Agent"))
    }
}

package com.cleanx.lcx.core.network

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class SessionExpiredInterceptorTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `401 emits session expired by default`() = runTest {
        val interceptor = SessionExpiredInterceptor()
        val client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()

        server.enqueue(MockResponse().setResponseCode(401))

        interceptor.sessionExpired.test {
            val response = client.newCall(
                Request.Builder()
                    .url(server.url("/api/tickets"))
                    .build(),
            ).execute()

            assertEquals(401, response.code)
            response.close()
            assertEquals(Unit, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `suppressed 401 stays local and strips control header`() = runTest {
        val interceptor = SessionExpiredInterceptor()
        val client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()

        server.enqueue(MockResponse().setResponseCode(401))

        interceptor.sessionExpired.test {
            val response = client.newCall(
                Request.Builder()
                    .url(server.url("/api/tickets"))
                    .header(
                        SessionExpiredInterceptor.SUPPRESS_SESSION_EXPIRED_HEADER,
                        SessionExpiredInterceptor.SUPPRESS_SESSION_EXPIRED_VALUE,
                    )
                    .build(),
            ).execute()

            assertEquals(401, response.code)
            response.close()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }

        val recorded = server.takeRequest()
        assertNull(recorded.getHeader(SessionExpiredInterceptor.SUPPRESS_SESSION_EXPIRED_HEADER))
    }
}

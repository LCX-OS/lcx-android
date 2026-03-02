package com.cleanx.lcx.core.network.contract

import com.cleanx.lcx.core.network.TicketApi
import com.cleanx.lcx.feature.tickets.data.TicketRepository
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Base class wiring a [MockWebServer] → OkHttp → Retrofit → real
 * [TicketApi] → real [TicketRepository] so that every assertion
 * exercises the full client-side network stack.
 */
abstract class ContractTestBase {

    protected lateinit var server: MockWebServer
    protected lateinit var api: TicketApi
    protected lateinit var repository: TicketRepository
    protected val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Before
    fun setUpServer() {
        server = MockWebServer()
        server.start()

        val contentType = "application/json".toMediaType()

        val client = OkHttpClient.Builder().build()

        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()

        api = retrofit.create(TicketApi::class.java)
        repository = TicketRepository(api, json)
    }

    @After
    fun tearDownServer() {
        server.shutdown()
    }

    // ----- helpers -----

    protected fun enqueue(body: String, code: Int = 200) {
        server.enqueue(
            MockResponse()
                .setResponseCode(code)
                .setHeader("Content-Type", "application/json")
                .setBody(body),
        )
    }
}

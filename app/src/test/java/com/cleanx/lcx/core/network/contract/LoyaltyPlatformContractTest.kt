package com.cleanx.lcx.core.network.contract

import com.cleanx.lcx.core.config.BuildConfigProvider
import com.cleanx.lcx.core.di.NetworkModule
import com.cleanx.lcx.core.network.CreateLoyaltyAccountRequest
import com.cleanx.lcx.core.network.EarnLoyaltyPointsRequest
import com.cleanx.lcx.core.network.LoyaltyApi
import com.cleanx.lcx.core.network.RedeemLoyaltyPointsRequest
import com.cleanx.lcx.core.network.WalletIssueRequest
import com.cleanx.lcx.core.network.WalletResyncRequest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class LoyaltyPlatformContractTest : ContractTestBase() {

    private lateinit var loyaltyApi: LoyaltyApi

    @Before
    fun setUpLoyaltyApi() {
        loyaltyApi = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(LoyaltyApi::class.java)
    }

    @Test
    fun `network module wires loyalty api to platform base url`() = runTest {
        val apiServer = MockWebServer()
        apiServer.start()
        try {
            apiServer.enqueue(MockResponse().setResponseCode(500))
            enqueue(
                """
                {
                  "data": { "rewards": [] },
                  "error": null,
                  "code": null,
                  "correlation_id": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
                }
                """.trimIndent(),
            )

            val retrofit = NetworkModule.providePlatformRetrofit(
                client = OkHttpClient.Builder().build(),
                json = json,
                config = fakeConfig(
                    apiBaseUrl = apiServer.url("/").toString().trimEnd('/'),
                    platformBaseUrl = server.url("/").toString().trimEnd('/'),
                ),
            )
            val api = NetworkModule.provideLoyaltyApi(retrofit)

            val response = api.getRewards()
            val request = server.takeRequest()

            assertTrue(response.isSuccessful)
            assertEquals("/v1/loyalty/rewards", request.path)
            assertEquals(0, apiServer.requestCount)
        } finally {
            apiServer.shutdown()
        }
    }

    @Test
    fun `create account posts to platform loyalty path`() = runTest {
        enqueue(
            """
            {
              "data": {
                "id": "1f289b45-5de3-4b6a-abed-6ca99e6ec40e",
                "customer_id": "5dd8dc16-7e94-46d7-b3ff-9dd8ec2c1c11",
                "loyalty_id": "CLNX-ABC12345",
                "display_name": "Ana Lopez",
                "points_balance": 0,
                "status": "active",
                "add_to_apple_wallet_url": "https://api.cleanx.mx/v1/loyalty/wallet/apple?account_id=1&token=t",
                "add_to_google_wallet_url": "https://api.cleanx.mx/v1/loyalty/wallet/google?account_id=1&token=t",
                "created_at": "2026-05-12T10:00:00Z",
                "updated_at": "2026-05-12T10:00:00Z"
              },
              "error": null,
              "code": null,
              "correlation_id": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
            }
            """.trimIndent(),
        )

        val response = loyaltyApi.createAccount(
            CreateLoyaltyAccountRequest(
                displayName = "Ana Lopez",
                customerId = "5dd8dc16-7e94-46d7-b3ff-9dd8ec2c1c11",
            ),
        )
        val request = server.takeRequest()

        assertEquals("POST", request.method)
        assertEquals("/v1/loyalty/accounts", request.path)
        assertTrue(response.isSuccessful)
        assertEquals("CLNX-ABC12345", response.body()?.data?.loyaltyId)
    }

    @Test
    fun `list accounts uses platform path and query parameters`() = runTest {
        enqueue(
            """
            {
              "data": {
                "accounts": [
                  {
                    "id": "1f289b45-5de3-4b6a-abed-6ca99e6ec40e",
                    "customer_id": "5dd8dc16-7e94-46d7-b3ff-9dd8ec2c1c11",
                    "loyalty_id": "CLNX-ABC12345",
                    "display_name": "Ana Lopez",
                    "points_balance": 15,
                    "status": "active",
                    "add_to_apple_wallet_url": "https://api.cleanx.mx/v1/loyalty/wallet/apple?account_id=1&token=t",
                    "add_to_google_wallet_url": "https://api.cleanx.mx/v1/loyalty/wallet/google?account_id=1&token=t",
                    "created_at": "2026-05-12T10:00:00Z",
                    "updated_at": "2026-05-12T10:00:00Z"
                  }
                ]
              },
              "error": null,
              "code": null,
              "correlation_id": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
            }
            """.trimIndent(),
        )

        val response = loyaltyApi.listAccounts(
            query = "Ana",
            customerId = "5dd8dc16-7e94-46d7-b3ff-9dd8ec2c1c11",
            limit = 20,
        )
        val request = server.takeRequest()

        assertEquals("GET", request.method)
        assertEquals("/v1/loyalty/accounts", request.requestUrl?.encodedPath)
        assertEquals("Ana", request.requestUrl?.queryParameter("query"))
        assertEquals("5dd8dc16-7e94-46d7-b3ff-9dd8ec2c1c11", request.requestUrl?.queryParameter("customer_id"))
        assertEquals("20", request.requestUrl?.queryParameter("limit"))
        assertTrue(response.isSuccessful)
        assertEquals(1, response.body()?.data?.accounts?.size)
    }

    @Test
    fun `account detail reads platform events field`() = runTest {
        enqueue(
            """
            {
              "data": {
                "account": {
                  "id": "1f289b45-5de3-4b6a-abed-6ca99e6ec40e",
                  "customer_id": null,
                  "loyalty_id": "CLNX-ABC12345",
                  "display_name": "Ana Lopez",
                  "points_balance": 15,
                  "status": "active",
                  "add_to_apple_wallet_url": "https://api.cleanx.mx/v1/loyalty/wallet/apple?account_id=1&token=t",
                  "add_to_google_wallet_url": "https://api.cleanx.mx/v1/loyalty/wallet/google?account_id=1&token=t",
                  "created_at": "2026-05-12T10:00:00Z",
                  "updated_at": "2026-05-12T10:00:00Z"
                },
                "events": [
                  {
                    "id": "9d2f52ed-a879-450b-ae15-915afde8749d",
                    "event_type": "earn",
                    "source_type": "ticket_paid",
                    "source_ref_id": "ticket:paid",
                    "points_delta": 15,
                    "ticket_id": "8bf41f0a-7614-4f63-96af-17c013ef6650",
                    "branch_id": "sucursal-centro",
                    "reward_id": null,
                    "requested_by": "android",
                    "metadata": { "service": "Lavado por kilo" },
                    "created_at": "2026-05-12T10:05:00Z"
                  }
                ]
              },
              "error": null,
              "code": null,
              "correlation_id": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
            }
            """.trimIndent(),
        )

        val response = loyaltyApi.getAccount("1f289b45-5de3-4b6a-abed-6ca99e6ec40e")
        val request = server.takeRequest()

        assertEquals("GET", request.method)
        assertEquals("/v1/loyalty/accounts/1f289b45-5de3-4b6a-abed-6ca99e6ec40e", request.path)
        assertTrue(response.isSuccessful)
        assertEquals(1, response.body()?.data?.events?.size)
        assertEquals(null, response.body()?.data?.events?.first()?.accountId)
        assertEquals("ticket_paid", response.body()?.data?.events?.first()?.sourceType)
    }

    @Test
    fun `earn posts canonical platform ticket payload`() = runTest {
        enqueue(
            """
            {
              "data": {
                "event_id": "9d2f52ed-a879-450b-ae15-915afde8749d",
                "account_id": "1f289b45-5de3-4b6a-abed-6ca99e6ec40e",
                "points_delta": 20,
                "new_points_balance": 35,
                "idempotent": false
              },
              "error": null,
              "code": null,
              "correlation_id": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
            }
            """.trimIndent(),
        )

        val response = loyaltyApi.earn(
            EarnLoyaltyPointsRequest(
                accountId = "1f289b45-5de3-4b6a-abed-6ca99e6ec40e",
                sourceType = "ticket_paid",
                sourceRefId = "ticket:8bf41f0a-7614-4f63-96af-17c013ef6650:paid",
                points = 20,
                ticketId = "8bf41f0a-7614-4f63-96af-17c013ef6650",
                requestedBy = "android",
                metadata = buildJsonObject {
                    put("service", JsonPrimitive("Lavado por kilo"))
                    put("weight", JsonPrimitive(10))
                },
            ),
        )
        val request = server.takeRequest()
        val body = request.body.readUtf8()

        assertEquals("POST", request.method)
        assertEquals("/v1/loyalty/events/earn", request.path)
        assertTrue(body.contains("\"account_id\""))
        assertTrue(body.contains("\"ticket_id\""))
        assertTrue(body.contains("\"metadata\""))
        assertFalse(body.contains("\"loyalty_id\""))
        assertTrue(response.isSuccessful)
        assertEquals(20, response.body()?.data?.pointsDelta)
    }

    @Test
    fun `redeem posts reward id to platform`() = runTest {
        enqueue(
            """
            {
              "data": {
                "event_id": "9d2f52ed-a879-450b-ae15-915afde8749d",
                "account_id": "1f289b45-5de3-4b6a-abed-6ca99e6ec40e",
                "points_delta": -35,
                "new_points_balance": 65,
                "idempotent": false
              },
              "error": null,
              "code": null,
              "correlation_id": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
            }
            """.trimIndent(),
        )

        val response = loyaltyApi.redeem(
            RedeemLoyaltyPointsRequest(
                accountId = "1f289b45-5de3-4b6a-abed-6ca99e6ec40e",
                rewardId = "b2894e20-b1aa-45fc-8cf4-cd8830e2852c",
                sourceRefId = "redeem:test",
            ),
        )
        val request = server.takeRequest()

        assertEquals("POST", request.method)
        assertEquals("/v1/loyalty/events/redeem", request.path)
        assertTrue(request.body.readUtf8().contains("\"reward_id\""))
        assertTrue(response.isSuccessful)
        assertEquals(-35, response.body()?.data?.pointsDelta)
    }

    @Test
    fun `wallet issue posts to platform wallet path`() = runTest {
        enqueue(
            """
            {
              "data": {
                "account_id": "1f289b45-5de3-4b6a-abed-6ca99e6ec40e",
                "add_to_apple_wallet_url": "https://api.cleanx.mx/v1/loyalty/wallet/apple?account_id=1&token=t",
                "add_to_google_wallet_url": "https://api.cleanx.mx/v1/loyalty/wallet/google?account_id=1&token=t"
              },
              "error": null,
              "code": null,
              "correlation_id": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
            }
            """.trimIndent(),
        )

        val response = loyaltyApi.issueWallet(
            WalletIssueRequest(accountId = "1f289b45-5de3-4b6a-abed-6ca99e6ec40e"),
        )
        val request = server.takeRequest()

        assertEquals("POST", request.method)
        assertEquals("/v1/loyalty/wallet/issue", request.path)
        assertTrue(response.isSuccessful)
        assertEquals("1f289b45-5de3-4b6a-abed-6ca99e6ec40e", response.body()?.data?.accountId)
    }

    @Test
    fun `wallet resync posts provider to platform wallet path`() = runTest {
        enqueue(
            """
            {
              "data": {
                "account_id": "1f289b45-5de3-4b6a-abed-6ca99e6ec40e",
                "status": "queued",
                "jobs": [
                  {
                    "id": "9d2f52ed-a879-450b-ae15-915afde8749d",
                    "provider": "google",
                    "operation": "sync",
                    "status": "queued"
                  }
                ]
              },
              "error": null,
              "code": null,
              "correlation_id": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
            }
            """.trimIndent(),
        )

        val response = loyaltyApi.resyncWallet(
            WalletResyncRequest(
                accountId = "1f289b45-5de3-4b6a-abed-6ca99e6ec40e",
                provider = "google",
            ),
        )
        val request = server.takeRequest()
        val body = request.body.readUtf8()

        assertEquals("POST", request.method)
        assertEquals("/v1/loyalty/wallet/resync", request.path)
        assertTrue(body.contains("\"provider\":\"google\""))
        assertTrue(response.isSuccessful)
        assertEquals("queued", response.body()?.data?.status)
    }

    @Test
    fun `rewards uses platform include inactive query`() = runTest {
        enqueue(
            """
            {
              "data": {
                "rewards": [
                  {
                    "id": "b2894e20-b1aa-45fc-8cf4-cd8830e2852c",
                    "name": "Lavado gratis",
                    "description": null,
                    "points_cost": 35,
                    "active": true,
                    "sort_order": 1,
                    "created_at": "2026-05-12T10:00:00Z",
                    "updated_at": "2026-05-12T10:00:00Z"
                  }
                ]
              },
              "error": null,
              "code": null,
              "correlation_id": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
            }
            """.trimIndent(),
        )

        val response = loyaltyApi.getRewards(includeInactive = true)
        val request = server.takeRequest()

        assertEquals("GET", request.method)
        assertEquals("/v1/loyalty/rewards", request.requestUrl?.encodedPath)
        assertEquals("true", request.requestUrl?.queryParameter("include_inactive"))
        assertTrue(response.isSuccessful)
        assertEquals(35, response.body()?.data?.rewards?.first()?.pointsCost)
    }

    private fun fakeConfig(
        apiBaseUrl: String,
        platformBaseUrl: String,
    ): BuildConfigProvider = object : BuildConfigProvider {
        override val applicationId: String = "com.cleanx.app"
        override val apiBaseUrl: String = apiBaseUrl
        override val platformBaseUrl: String = platformBaseUrl
        override val notificationsBaseUrl: String = platformBaseUrl
        override val supabaseUrl: String = "https://example.supabase.co"
        override val supabaseAnonKey: String = "anon"
        override val deviceAuthBootstrapToken: String = "local-device-auth"
        override val zettleClientId: String = "client-id"
        override val zettleRedirectUrl: String = "com.cleanx.app://oauth/callback"
        override val zettleApprovedApplicationId: String = "com.cleanx.app"
        override val isDebug: Boolean = true
        override val useRealZettle: Boolean = false
        override val useRealBrother: Boolean = false
    }
}

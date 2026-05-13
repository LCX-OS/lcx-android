package com.cleanx.lcx.core.network.contract

import com.cleanx.lcx.core.network.CreateLoyaltyAccountRequest
import com.cleanx.lcx.core.network.LoyaltyApi
import com.cleanx.lcx.core.network.RedeemLoyaltyPointsRequest
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assert.assertEquals
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
            .client(okhttp3.OkHttpClient.Builder().build())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(LoyaltyApi::class.java)
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
}

package com.cleanx.lcx.feature.loyalty.data

import com.cleanx.lcx.core.network.CreateLoyaltyAccountRequest
import com.cleanx.lcx.core.network.LoyaltyAccountDto
import com.cleanx.lcx.core.network.LoyaltyApi
import com.cleanx.lcx.core.network.LoyaltyCreateAccountData
import com.cleanx.lcx.core.network.LoyaltyCreateAccountResponse
import com.cleanx.lcx.core.network.LoyaltyPointsUpdateData
import com.cleanx.lcx.core.network.LoyaltyPointsUpdateResponse
import com.cleanx.lcx.core.network.LoyaltyEventDto
import com.cleanx.lcx.core.network.WalletLinksDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class LoyaltyRepositoryTest {

    private lateinit var api: LoyaltyApi
    private lateinit var repository: LoyaltyRepository
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private val sampleAccount = LoyaltyAccountDto(
        id = "acc_1",
        loyaltyId = "loy_1",
        customerId = "customer_1",
        displayName = "Juan Perez",
        pointsBalance = 5,
        status = "active",
        walletAppleSerial = null,
        walletGoogleObjectId = null,
        chainWalletAddress = null,
        createdAt = "2026-03-06T10:00:00Z",
        updatedAt = "2026-03-06T10:00:00Z",
    )

    @Before
    fun setUp() {
        api = mockk()
        repository = LoyaltyRepository(api, json)
    }

    @Test
    fun `createAccount success returns account data`() = runTest {
        coEvery { api.createAccount(any()) } returns Response.success(
            LoyaltyCreateAccountResponse(
                data = LoyaltyCreateAccountData(
                    account = sampleAccount,
                    wallet = WalletLinksDto(
                        addToAppleWalletUrl = "https://example.com/apple/acc_1",
                        addToGoogleWalletUrl = "https://example.com/google/acc_1",
                    ),
                ),
            ),
        )

        val result = repository.createAccount(displayName = "Juan Perez")

        assertTrue(result is LoyaltyApiResult.Success)
        val data = (result as LoyaltyApiResult.Success).data
        assertEquals("acc_1", data.account.id)
        assertEquals("Juan Perez", data.account.displayName)
    }

    @Test
    fun `createAccount sends expected request fields`() = runTest {
        val requestSlot = slot<CreateLoyaltyAccountRequest>()
        coEvery { api.createAccount(capture(requestSlot)) } returns Response.success(
            LoyaltyCreateAccountResponse(
                data = LoyaltyCreateAccountData(
                    account = sampleAccount,
                    wallet = WalletLinksDto(
                        addToAppleWalletUrl = "https://example.com/apple/acc_1",
                        addToGoogleWalletUrl = "https://example.com/google/acc_1",
                    ),
                ),
            ),
        )

        repository.createAccount(
            displayName = "Maria Lopez",
            customerId = "customer_2",
            chainWalletAddress = "0xabc",
        )

        val captured = requestSlot.captured
        assertEquals("Maria Lopez", captured.displayName)
        assertEquals("customer_2", captured.customerId)
        assertEquals("0xabc", captured.chainWalletAddress)
    }

    @Test
    fun `earnPoints maps sourceType and parses response`() = runTest {
        coEvery { api.earn(any()) } returns Response.success(
            LoyaltyPointsUpdateResponse(
                data = LoyaltyPointsUpdateData(
                    account = sampleAccount.copy(pointsBalance = 7),
                    event = LoyaltyEventDto(
                        id = "evt_1",
                        accountId = "acc_1",
                        eventType = "earn",
                        sourceType = "washer_cycle",
                        sourceRefId = "pos-001",
                        pointsDelta = 2,
                        chainTxHash = "0x123",
                        createdAt = "2026-03-06T10:01:00Z",
                    ),
                ),
            ),
        )

        val result = repository.earnPoints(
            accountId = "acc_1",
            sourceType = LoyaltySourceType.WASHER_CYCLE,
            sourceRefId = "pos-001",
            quantity = 2.0,
        )

        assertTrue(result is LoyaltyApiResult.Success)
        val data = (result as LoyaltyApiResult.Success).data
        assertEquals(7, data.account.pointsBalance)
        assertEquals("washer_cycle", data.event.sourceType)
        coVerify(exactly = 1) { api.earn(match { it.sourceType == "washer_cycle" }) }
    }

    @Test
    fun `redeemPoints parses API error body`() = runTest {
        val errorJson = """{"error":"Puntos insuficientes para canje.","code":"LOYALTY_INSUFFICIENT_POINTS","details":"balance<points"}"""
        val errorBody = errorJson.toResponseBody("application/json".toMediaType())

        coEvery { api.redeem(any()) } returns Response.error(409, errorBody)

        val result = repository.redeemPoints(
            accountId = "acc_1",
            sourceRefId = "redeem-001",
            points = 10,
        )

        assertTrue(result is LoyaltyApiResult.Error)
        val error = result as LoyaltyApiResult.Error
        assertEquals(409, error.httpStatus)
        assertEquals("LOYALTY_INSUFFICIENT_POINTS", error.code)
        assertEquals("Puntos insuficientes para canje.", error.message)
    }
}

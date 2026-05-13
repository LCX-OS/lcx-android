package com.cleanx.lcx.feature.loyalty.data

import com.cleanx.lcx.core.network.CreateLoyaltyAccountRequest
import com.cleanx.lcx.core.network.EarnLoyaltyPointsRequest
import com.cleanx.lcx.core.network.LoyaltyAccountDto
import com.cleanx.lcx.core.network.LoyaltyAccountsListData
import com.cleanx.lcx.core.network.LoyaltyAccountsListResponse
import com.cleanx.lcx.core.network.LoyaltyApi
import com.cleanx.lcx.core.network.LoyaltyCreateAccountResponse
import com.cleanx.lcx.core.network.LoyaltyPointsUpdateData
import com.cleanx.lcx.core.network.LoyaltyPointsUpdateResponse
import com.cleanx.lcx.core.network.LoyaltyRewardDto
import com.cleanx.lcx.core.network.LoyaltyRewardsData
import com.cleanx.lcx.core.network.LoyaltyRewardsResponse
import com.cleanx.lcx.core.network.WalletResyncData
import com.cleanx.lcx.core.network.WalletResyncRequest
import com.cleanx.lcx.core.network.WalletResyncResponse
import com.cleanx.lcx.core.network.WalletSyncJobDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
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
        addToAppleWalletUrl = "https://example.com/apple/acc_1",
        addToGoogleWalletUrl = "https://example.com/google/acc_1",
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
                data = sampleAccount,
            ),
        )

        val result = repository.createAccount(
            displayName = "Juan Perez",
            customerId = "customer_1",
        )

        assertTrue(result is LoyaltyApiResult.Success)
        val data = (result as LoyaltyApiResult.Success).data
        assertEquals("acc_1", data.account.id)
        assertEquals("Juan Perez", data.account.displayName)
    }

    @Test
    fun `createAccount rejects missing platform identifier`() = runTest {
        val result = repository.createAccount(displayName = "Juan Perez")

        assertTrue(result is LoyaltyApiResult.Error)
        val error = result as LoyaltyApiResult.Error
        assertEquals("LOYALTY_ACCOUNT_IDENTIFIER_REQUIRED", error.code)
        coVerify(exactly = 0) { api.createAccount(any()) }
    }

    @Test
    fun `createAccount sends expected request fields`() = runTest {
        val requestSlot = slot<CreateLoyaltyAccountRequest>()
        coEvery { api.createAccount(capture(requestSlot)) } returns Response.success(
            LoyaltyCreateAccountResponse(
                data = sampleAccount,
            ),
        )

        repository.createAccount(
            displayName = "Maria Lopez",
            customerId = "customer_2",
            loyaltyId = "CLNX-MARIA",
        )

        val captured = requestSlot.captured
        assertEquals("Maria Lopez", captured.displayName)
        assertEquals("customer_2", captured.customerId)
        assertEquals("CLNX-MARIA", captured.loyaltyId)
    }

    @Test
    fun `listAccounts forwards platform query parameters`() = runTest {
        coEvery { api.listAccounts(any(), any(), any()) } returns Response.success(
            LoyaltyAccountsListResponse(
                data = LoyaltyAccountsListData(accounts = listOf(sampleAccount)),
            ),
        )

        val result = repository.listAccounts(
            query = "Juan",
            customerId = "customer_1",
            limit = 10,
        )

        assertTrue(result is LoyaltyApiResult.Success)
        assertEquals(1, (result as LoyaltyApiResult.Success).data.accounts.size)
        coVerify(exactly = 1) {
            api.listAccounts(
                query = "Juan",
                customerId = "customer_1",
                limit = 10,
            )
        }
    }

    @Test
    fun `earnPoints maps canonical platform fields and parses response`() = runTest {
        val requestSlot = slot<EarnLoyaltyPointsRequest>()
        coEvery { api.earn(capture(requestSlot)) } returns Response.success(
            LoyaltyPointsUpdateResponse(
                data = LoyaltyPointsUpdateData(
                    eventId = "evt_1",
                    accountId = "acc_1",
                    pointsDelta = 2,
                    newPointsBalance = 7,
                    idempotent = false,
                ),
            ),
        )

        val result = repository.earnPoints(
            accountId = "acc_1",
            sourceType = LoyaltySourceType.TICKET_PAID,
            sourceRefId = "ticket:paid",
            quantity = null,
            points = 12,
            ticketId = "ticket_1",
            branchId = "branch_1",
            requestedBy = "android",
            metadata = buildJsonObject {
                put("service", JsonPrimitive("Lavado por kilo"))
                put("weight", JsonPrimitive(10))
            },
        )

        assertTrue(result is LoyaltyApiResult.Success)
        val data = (result as LoyaltyApiResult.Success).data
        assertEquals(7, data.newPointsBalance)
        assertEquals(2, data.pointsDelta)
        val captured = requestSlot.captured
        assertEquals("acc_1", captured.accountId)
        assertEquals("ticket_paid", captured.sourceType)
        assertEquals("ticket:paid", captured.sourceRefId)
        assertEquals(null, captured.quantity)
        assertEquals(12, captured.points)
        assertEquals("ticket_1", captured.ticketId)
        assertEquals("branch_1", captured.branchId)
        assertEquals("android", captured.requestedBy)
        assertEquals("Lavado por kilo", captured.metadata?.get("service")?.toString()?.trim('"'))
    }

    @Test
    fun `redeemPoints parses API error body`() = runTest {
        val errorJson = """{"error":"Puntos insuficientes para canje.","code":"LOYALTY_INSUFFICIENT_POINTS","details":"balance<points"}"""
        val errorBody = errorJson.toResponseBody("application/json".toMediaType())

        coEvery { api.redeem(any()) } returns Response.error(409, errorBody)

        val result = repository.redeemPoints(
            accountId = "acc_1",
            rewardId = "reward_1",
            sourceRefId = "redeem-001",
        )

        assertTrue(result is LoyaltyApiResult.Error)
        val error = result as LoyaltyApiResult.Error
        assertEquals(409, error.httpStatus)
        assertEquals("LOYALTY_INSUFFICIENT_POINTS", error.code)
        assertEquals("Puntos insuficientes para canje.", error.message)
    }

    @Test
    fun `resyncWalletCard forwards provider filter`() = runTest {
        val requestSlot = slot<WalletResyncRequest>()
        coEvery { api.resyncWallet(capture(requestSlot)) } returns Response.success(
            WalletResyncResponse(
                data = WalletResyncData(
                    accountId = "acc_1",
                    status = "queued",
                    jobs = listOf(
                        WalletSyncJobDto(
                            id = "job_1",
                            provider = "google",
                            operation = "sync",
                            status = "queued",
                        ),
                    ),
                ),
            ),
        )

        val result = repository.resyncWalletCard(accountId = "acc_1", provider = "google")

        assertTrue(result is LoyaltyApiResult.Success)
        assertEquals("acc_1", requestSlot.captured.accountId)
        assertEquals("google", requestSlot.captured.provider)
    }

    @Test
    fun `getRewards forwards include inactive flag`() = runTest {
        coEvery { api.getRewards(any()) } returns Response.success(
            LoyaltyRewardsResponse(
                data = LoyaltyRewardsData(
                    rewards = listOf(
                        LoyaltyRewardDto(
                            id = "reward_1",
                            name = "Gratis",
                            description = null,
                            pointsCost = 35,
                            active = false,
                            sortOrder = 1,
                            createdAt = "2026-05-12T10:00:00Z",
                            updatedAt = "2026-05-12T10:00:00Z",
                        ),
                    ),
                ),
            ),
        )

        val result = repository.getRewards(includeInactive = true)

        assertTrue(result is LoyaltyApiResult.Success)
        assertEquals(1, (result as LoyaltyApiResult.Success).data.rewards.size)
        coVerify(exactly = 1) { api.getRewards(includeInactive = true) }
    }
}

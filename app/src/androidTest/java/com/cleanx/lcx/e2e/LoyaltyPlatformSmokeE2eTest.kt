package com.cleanx.lcx.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cleanx.lcx.BuildConfig
import com.cleanx.lcx.core.network.LoyaltyApi
import com.cleanx.lcx.feature.loyalty.data.LoyaltyApiResult
import com.cleanx.lcx.feature.loyalty.data.LoyaltyRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoyaltyPlatformSmokeE2eTest {
    @Test
    fun loyalty_repository_reaches_platform_wallet_endpoints_without_hardware() = runBlocking {
        assertEquals("com.cleanx.app", BuildConfig.APPLICATION_ID)
        assertEquals("http://127.0.0.1:8080", BuildConfig.PLATFORM_BASE_URL)
        assertFalse("No-hardware smoke must not enable real Zettle", BuildConfig.USE_REAL_ZETTLE)
        assertFalse("No-hardware smoke must not enable real Brother", BuildConfig.USE_REAL_BROTHER)

        val repository = createRepository()
        val rewards = repository.getRewards()
            .successOrThrow("getRewards")
        assertTrue("Expected at least one configured loyalty reward", rewards.rewards.isNotEmpty())

        val suffix = System.currentTimeMillis().toString().takeLast(8)
        val loyaltyId = "CLNX-ADB-$suffix"
        val created = repository.createAccount(
            displayName = "Android ADB Loyalty $suffix",
            loyaltyId = loyaltyId,
        ).successOrThrow("createAccount")

        assertEquals(loyaltyId, created.account.loyaltyId)
        assertTrue(created.wallet.addToGoogleWalletUrl.isNotBlank())
        assertTrue(created.wallet.addToAppleWalletUrl.isNotBlank())

        val detail = repository.getAccountDetail(created.account.id)
            .successOrThrow("getAccountDetail")
        assertEquals(created.account.id, detail.account.id)
        assertEquals(loyaltyId, detail.account.loyaltyId)

        val listed = repository.listAccounts(query = loyaltyId, limit = 1)
            .successOrThrow("listAccounts")
        assertTrue(
            "Expected created loyalty account to be returned by listAccounts",
            listed.accounts.any { it.id == created.account.id },
        )

        val wallet = repository.issueWalletCard(created.account.id)
            .successOrThrow("issueWalletCard")
        assertEquals(created.account.id, wallet.accountId)
        assertTrue(wallet.addToGoogleWalletUrl.isNotBlank())
        assertTrue(wallet.addToAppleWalletUrl.isNotBlank())
    }

    private fun <T> LoyaltyApiResult<T>.successOrThrow(operation: String): T {
        return when (this) {
            is LoyaltyApiResult.Success -> data
            is LoyaltyApiResult.Error -> error(
                "$operation failed: status=$httpStatus code=$code message=$message details=$details",
            )
        }
    }

    private fun createRepository(): LoyaltyRepository {
        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            encodeDefaults = true
        }
        val api = Retrofit.Builder()
            .baseUrl(BuildConfig.PLATFORM_BASE_URL.trimEnd('/') + "/")
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(LoyaltyApi::class.java)
        return LoyaltyRepository(api, json)
    }
}

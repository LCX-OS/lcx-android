package com.cleanx.lcx.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

@Serializable
data class CreateLoyaltyAccountRequest(
    @SerialName("display_name") val displayName: String,
    @SerialName("customer_id") val customerId: String? = null,
    @SerialName("chain_wallet_address") val chainWalletAddress: String? = null,
)

@Serializable
data class EarnLoyaltyPointsRequest(
    @SerialName("account_id") val accountId: String? = null,
    @SerialName("loyalty_id") val loyaltyId: String? = null,
    @SerialName("source_type") val sourceType: String,
    @SerialName("source_ref_id") val sourceRefId: String,
    val quantity: Double? = null,
    val points: Int? = null,
    @SerialName("branch_id") val branchId: String? = null,
)

@Serializable
data class RedeemLoyaltyPointsRequest(
    @SerialName("account_id") val accountId: String? = null,
    @SerialName("loyalty_id") val loyaltyId: String? = null,
    @SerialName("source_ref_id") val sourceRefId: String,
    val points: Int,
)

@Serializable
data class WalletIssueRequest(
    @SerialName("account_id") val accountId: String,
)

@Serializable
data class WalletResyncRequest(
    @SerialName("account_id") val accountId: String,
)

@Serializable
data class LoyaltyAccountDto(
    val id: String,
    @SerialName("loyalty_id") val loyaltyId: String,
    @SerialName("customer_id") val customerId: String? = null,
    @SerialName("display_name") val displayName: String,
    @SerialName("points_balance") val pointsBalance: Int,
    val status: String,
    @SerialName("wallet_apple_serial") val walletAppleSerial: String? = null,
    @SerialName("wallet_google_object_id") val walletGoogleObjectId: String? = null,
    @SerialName("chain_wallet_address") val chainWalletAddress: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class LoyaltyEventDto(
    val id: String,
    @SerialName("account_id") val accountId: String,
    @SerialName("event_type") val eventType: String,
    @SerialName("source_type") val sourceType: String,
    @SerialName("source_ref_id") val sourceRefId: String,
    @SerialName("points_delta") val pointsDelta: Int,
    @SerialName("chain_tx_hash") val chainTxHash: String? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class WalletLinksDto(
    @SerialName("add_to_apple_wallet_url") val addToAppleWalletUrl: String,
    @SerialName("add_to_google_wallet_url") val addToGoogleWalletUrl: String,
)

@Serializable
data class LoyaltyCreateAccountData(
    val account: LoyaltyAccountDto,
    val wallet: WalletLinksDto,
)

@Serializable
data class LoyaltyAccountDetailData(
    val account: LoyaltyAccountDto,
    @SerialName("recent_events") val recentEvents: List<LoyaltyEventDto>,
)

@Serializable
data class LoyaltyPointsUpdateData(
    val account: LoyaltyAccountDto,
    val event: LoyaltyEventDto,
)

@Serializable
data class WalletIssueData(
    @SerialName("account_id") val accountId: String,
    @SerialName("add_to_apple_wallet_url") val addToAppleWalletUrl: String,
    @SerialName("add_to_google_wallet_url") val addToGoogleWalletUrl: String,
)

@Serializable
data class WalletResyncData(
    @SerialName("account_id") val accountId: String,
    val status: String,
)

@Serializable
data class LoyaltyCreateAccountResponse(
    val data: LoyaltyCreateAccountData? = null,
    val error: String? = null,
    val code: String? = null,
    val details: String? = null,
    @SerialName("correlation_id") val correlationId: String? = null,
)

@Serializable
data class LoyaltyAccountDetailResponse(
    val data: LoyaltyAccountDetailData? = null,
    val error: String? = null,
    val code: String? = null,
    val details: String? = null,
    @SerialName("correlation_id") val correlationId: String? = null,
)

@Serializable
data class LoyaltyPointsUpdateResponse(
    val data: LoyaltyPointsUpdateData? = null,
    val error: String? = null,
    val code: String? = null,
    val details: String? = null,
    @SerialName("correlation_id") val correlationId: String? = null,
)

@Serializable
data class WalletIssueResponse(
    val data: WalletIssueData? = null,
    val error: String? = null,
    val code: String? = null,
    val details: String? = null,
    @SerialName("correlation_id") val correlationId: String? = null,
)

@Serializable
data class WalletResyncResponse(
    val data: WalletResyncData? = null,
    val error: String? = null,
    val code: String? = null,
    val details: String? = null,
    @SerialName("correlation_id") val correlationId: String? = null,
)

interface LoyaltyApi {
    @POST("api/loyalty/accounts")
    suspend fun createAccount(
        @Body request: CreateLoyaltyAccountRequest,
    ): Response<LoyaltyCreateAccountResponse>

    @GET("api/loyalty/accounts/{id}")
    suspend fun getAccount(
        @Path("id") accountId: String,
    ): Response<LoyaltyAccountDetailResponse>

    @POST("api/loyalty/events/earn")
    suspend fun earn(
        @Body request: EarnLoyaltyPointsRequest,
    ): Response<LoyaltyPointsUpdateResponse>

    @POST("api/loyalty/events/redeem")
    suspend fun redeem(
        @Body request: RedeemLoyaltyPointsRequest,
    ): Response<LoyaltyPointsUpdateResponse>

    @POST("api/loyalty/wallet/issue")
    suspend fun issueWallet(
        @Body request: WalletIssueRequest,
    ): Response<WalletIssueResponse>

    @POST("api/loyalty/wallet/resync")
    suspend fun resyncWallet(
        @Body request: WalletResyncRequest,
    ): Response<WalletResyncResponse>
}

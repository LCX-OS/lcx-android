package com.cleanx.lcx.feature.loyalty.data

import com.cleanx.lcx.core.network.ApiError
import com.cleanx.lcx.core.network.CreateLoyaltyAccountRequest
import com.cleanx.lcx.core.network.EarnLoyaltyPointsRequest
import com.cleanx.lcx.core.network.LoyaltyAccountDetailData
import com.cleanx.lcx.core.network.LoyaltyApi
import com.cleanx.lcx.core.network.LoyaltyCreateAccountData
import com.cleanx.lcx.core.network.LoyaltyPointsUpdateData
import com.cleanx.lcx.core.network.RedeemLoyaltyPointsRequest
import com.cleanx.lcx.core.network.WalletIssueData
import com.cleanx.lcx.core.network.WalletIssueRequest
import com.cleanx.lcx.core.network.WalletResyncData
import com.cleanx.lcx.core.network.WalletResyncRequest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.Response
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

enum class LoyaltySourceType {
    VISIT,
    WASHER_CYCLE,
    KG,
    MANUAL,
    ;

    fun toWireValue(): String = when (this) {
        VISIT -> "visit"
        WASHER_CYCLE -> "washer_cycle"
        KG -> "kg"
        MANUAL -> "manual"
    }
}

sealed interface LoyaltyApiResult<out T> {
    data class Success<T>(val data: T) : LoyaltyApiResult<T>
    data class Error(
        val code: String? = null,
        val message: String,
        val httpStatus: Int,
        val details: String? = null,
    ) : LoyaltyApiResult<Nothing>
}

@Singleton
class LoyaltyRepository @Inject constructor(
    private val api: LoyaltyApi,
    private val json: Json,
) {
    suspend fun createAccount(
        displayName: String,
        customerId: String? = null,
        chainWalletAddress: String? = null,
    ): LoyaltyApiResult<LoyaltyCreateAccountData> {
        Timber.tag("LOYALTY").d("Creating loyalty account for displayName=%s", displayName)

        return try {
            val response = api.createAccount(
                request = CreateLoyaltyAccountRequest(
                    displayName = displayName,
                    customerId = customerId,
                    chainWalletAddress = chainWalletAddress,
                ),
            )
            if (response.isSuccessful) {
                val data = response.body()?.data
                if (data != null) {
                    LoyaltyApiResult.Success(data)
                } else {
                    LoyaltyApiResult.Error(
                        message = "Respuesta vacia del servidor.",
                        httpStatus = response.code(),
                    )
                }
            } else {
                response.parseError()
            }
        } catch (e: Exception) {
            LoyaltyApiResult.Error(
                message = e.message ?: "Error de conexion.",
                httpStatus = 0,
            )
        }
    }

    suspend fun getAccountDetail(accountId: String): LoyaltyApiResult<LoyaltyAccountDetailData> {
        Timber.tag("LOYALTY").d("Loading loyalty account detail accountId=%s", accountId)

        return try {
            val response = api.getAccount(accountId = accountId)
            if (response.isSuccessful) {
                val data = response.body()?.data
                if (data != null) {
                    LoyaltyApiResult.Success(data)
                } else {
                    LoyaltyApiResult.Error(
                        message = "Respuesta vacia del servidor.",
                        httpStatus = response.code(),
                    )
                }
            } else {
                response.parseError()
            }
        } catch (e: Exception) {
            LoyaltyApiResult.Error(
                message = e.message ?: "Error de conexion.",
                httpStatus = 0,
            )
        }
    }

    suspend fun earnPoints(
        accountId: String? = null,
        loyaltyId: String? = null,
        sourceType: LoyaltySourceType,
        sourceRefId: String,
        quantity: Double? = 1.0,
        points: Int? = null,
        branchId: String? = null,
    ): LoyaltyApiResult<LoyaltyPointsUpdateData> {
        Timber.tag("LOYALTY").d(
            "Earning loyalty points accountId=%s loyaltyId=%s sourceType=%s sourceRefId=%s",
            accountId,
            loyaltyId,
            sourceType,
            sourceRefId,
        )

        return try {
            val response = api.earn(
                request = EarnLoyaltyPointsRequest(
                    accountId = accountId,
                    loyaltyId = loyaltyId,
                    sourceType = sourceType.toWireValue(),
                    sourceRefId = sourceRefId,
                    quantity = quantity,
                    points = points,
                    branchId = branchId,
                ),
            )
            if (response.isSuccessful) {
                val data = response.body()?.data
                if (data != null) {
                    LoyaltyApiResult.Success(data)
                } else {
                    LoyaltyApiResult.Error(
                        message = "Respuesta vacia del servidor.",
                        httpStatus = response.code(),
                    )
                }
            } else {
                response.parseError()
            }
        } catch (e: Exception) {
            LoyaltyApiResult.Error(
                message = e.message ?: "Error de conexion.",
                httpStatus = 0,
            )
        }
    }

    suspend fun redeemPoints(
        points: Int,
        sourceRefId: String,
        accountId: String? = null,
        loyaltyId: String? = null,
    ): LoyaltyApiResult<LoyaltyPointsUpdateData> {
        Timber.tag("LOYALTY").d(
            "Redeeming loyalty points accountId=%s loyaltyId=%s sourceRefId=%s points=%d",
            accountId,
            loyaltyId,
            sourceRefId,
            points,
        )

        return try {
            val response = api.redeem(
                request = RedeemLoyaltyPointsRequest(
                    accountId = accountId,
                    loyaltyId = loyaltyId,
                    sourceRefId = sourceRefId,
                    points = points,
                ),
            )
            if (response.isSuccessful) {
                val data = response.body()?.data
                if (data != null) {
                    LoyaltyApiResult.Success(data)
                } else {
                    LoyaltyApiResult.Error(
                        message = "Respuesta vacia del servidor.",
                        httpStatus = response.code(),
                    )
                }
            } else {
                response.parseError()
            }
        } catch (e: Exception) {
            LoyaltyApiResult.Error(
                message = e.message ?: "Error de conexion.",
                httpStatus = 0,
            )
        }
    }

    suspend fun issueWalletCard(accountId: String): LoyaltyApiResult<WalletIssueData> {
        Timber.tag("LOYALTY").d("Issuing wallet card accountId=%s", accountId)

        return try {
            val response = api.issueWallet(
                request = WalletIssueRequest(accountId = accountId),
            )
            if (response.isSuccessful) {
                val data = response.body()?.data
                if (data != null) {
                    LoyaltyApiResult.Success(data)
                } else {
                    LoyaltyApiResult.Error(
                        message = "Respuesta vacia del servidor.",
                        httpStatus = response.code(),
                    )
                }
            } else {
                response.parseError()
            }
        } catch (e: Exception) {
            LoyaltyApiResult.Error(
                message = e.message ?: "Error de conexion.",
                httpStatus = 0,
            )
        }
    }

    suspend fun resyncWalletCard(accountId: String): LoyaltyApiResult<WalletResyncData> {
        Timber.tag("LOYALTY").d("Resync wallet card accountId=%s", accountId)

        return try {
            val response = api.resyncWallet(
                request = WalletResyncRequest(accountId = accountId),
            )
            if (response.isSuccessful) {
                val data = response.body()?.data
                if (data != null) {
                    LoyaltyApiResult.Success(data)
                } else {
                    LoyaltyApiResult.Error(
                        message = "Respuesta vacia del servidor.",
                        httpStatus = response.code(),
                    )
                }
            } else {
                response.parseError()
            }
        } catch (e: Exception) {
            LoyaltyApiResult.Error(
                message = e.message ?: "Error de conexion.",
                httpStatus = 0,
            )
        }
    }

    private fun Response<*>.parseError(): LoyaltyApiResult.Error {
        val raw = errorBody()?.string()
        val apiError = try {
            if (raw.isNullOrBlank()) null
            else json.decodeFromString(ApiError.serializer(), raw)
        } catch (_: SerializationException) {
            null
        }

        return LoyaltyApiResult.Error(
            httpStatus = code(),
            message = apiError?.error ?: "No se pudo completar la operacion.",
            code = apiError?.code,
            details = apiError?.details,
        )
    }
}

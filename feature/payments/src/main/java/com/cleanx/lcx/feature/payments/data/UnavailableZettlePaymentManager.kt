package com.cleanx.lcx.feature.payments.data

import android.content.Context
import com.cleanx.lcx.core.config.BuildConfigProvider
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Placeholder bound when the build expects the real Zettle backend but the SDK
 * has not been wired into the app yet.
 */
@Singleton
class UnavailableZettlePaymentManager @Inject constructor(
    private val config: BuildConfigProvider,
) : PaymentManager {

    override suspend fun initialize(context: Context) {
        Timber.w("USE_REAL_ZETTLE=true but the real Zettle SDK is not integrated in this build")
        if (config.applicationId != config.zettleApprovedApplicationId) {
            Timber.w(
                "Zettle approved application id is %s but current build uses %s",
                config.zettleApprovedApplicationId,
                config.applicationId,
            )
        }
    }

    override fun isInitialized(): Boolean = false

    override fun capability(): PaymentCapability = PaymentCapability(
        backendType = PaymentBackendType.ZETTLE_REAL,
        backendLabel = "SDK real no integrado",
        canAcceptPayments = false,
        isInitialized = false,
        statusMessage = buildUnavailableStatus(config),
    )

    override suspend fun requestPayment(amount: Double, reference: String): PaymentResult {
        val capability = capability()
        Timber.w(
            "Rejecting payment request amount=%.2f ref=%s because real Zettle backend is unavailable",
            amount,
            reference,
        )
        return PaymentResult.Failed(
            errorCode = "ZETTLE_SDK_NOT_INTEGRATED",
            message = capability.statusMessage,
            reference = reference,
        )
    }
}

internal fun buildUnavailableStatus(config: BuildConfigProvider): String {
    val details = mutableListOf<String>()

    if (config.zettleClientId.isBlank()) {
        details += "Falta configurar `LCX_ZETTLE_CLIENT_ID`."
    } else {
        details += "Client ID cargado."
    }

    if (config.zettleRedirectUrl.isBlank()) {
        details += "Falta configurar `LCX_ZETTLE_REDIRECT_URL`."
    } else {
        details += "Redirect URL configurado: ${config.zettleRedirectUrl}."
    }

    if (config.applicationId != config.zettleApprovedApplicationId) {
        details +=
            "El APK actual usa `${config.applicationId}` pero Zettle aprobo `${config.zettleApprovedApplicationId}`."
    }

    details +=
        "Sigue faltando integrar las dependencias Android del SDK de Zettle y resolver el `GITHUB_TOKEN` para GitHub Packages."

    return details.joinToString(" ")
}

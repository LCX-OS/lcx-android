package com.cleanx.lcx.feature.payments.data

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ProcessLifecycleOwner
import com.cleanx.lcx.core.config.BuildConfigProvider
import com.zettle.sdk.ZettleSDK
import com.zettle.sdk.ZettleSDKLifecycle
import com.zettle.sdk.config
import com.zettle.sdk.feature.cardreader.payment.TransactionReference
import com.zettle.sdk.feature.cardreader.ui.CardReaderAction
import com.zettle.sdk.feature.cardreader.ui.CardReaderFeature
import com.zettle.sdk.feature.cardreader.ui.payment.CardPaymentResult
import com.zettle.sdk.features.charge
import com.zettle.sdk.ui.ZettleResult
import com.zettle.sdk.ui.zettleResult
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class ZettlePaymentManager @Inject constructor(
    private val buildConfigProvider: BuildConfigProvider,
    private val activityLauncherBridge: ZettleActivityLauncherBridge,
) : PaymentManager {
    @Volatile
    private var initialized = false

    @Volatile
    private var lifecycleAttached = false

    override suspend fun initialize(context: Context) {
        if (initialized) return

        val readiness = buildReadiness()
        if (!readiness.isConfigValid) {
            Timber.w("Skipping Zettle SDK initialization: %s", readiness.statusMessage)
            return
        }

        runCatching {
            val sdkConfig = config(context.applicationContext) {
                isDevMode = buildConfigProvider.isDebug && !buildConfigProvider.useRealZettle
                auth {
                    clientId = buildConfigProvider.zettleClientId
                    redirectUrl = buildConfigProvider.zettleRedirectUrl
                }
                logging {
                    allowWhileRoaming = false
                }
                addFeature(CardReaderFeature)
            }
            ZettleSDK.configure(sdkConfig)
            if (!lifecycleAttached) {
                ProcessLifecycleOwner.get().lifecycle.addObserver(ZettleSDKLifecycle())
                lifecycleAttached = true
            }
            initialized = true
            Timber.i("Zettle SDK initialized successfully")
        }.onFailure { throwable ->
            initialized = false
            Timber.e(throwable, "Zettle SDK initialization failed")
        }
    }

    override fun isInitialized(): Boolean = initialized

    override fun capability(): PaymentCapability {
        val readiness = buildReadiness()
        return PaymentCapability(
            backendType = PaymentBackendType.ZETTLE_REAL,
            backendLabel = if (readiness.canLaunchPayments) {
                "Zettle SDK real"
            } else {
                "Zettle SDK bloqueado"
            },
            canAcceptPayments = readiness.canLaunchPayments,
            isInitialized = initialized,
            statusMessage = readiness.statusMessage,
        )
    }

    override suspend fun requestPayment(amount: Double, reference: String): PaymentResult {
        if (!initialized) {
            return PaymentResult.Failed(
                errorCode = "ZETTLE_NOT_INITIALIZED",
                message = "El SDK de Zettle aun no esta inicializado en este build.",
                reference = reference,
            )
        }

        val readiness = buildReadiness()
        if (!readiness.canLaunchPayments) {
            return PaymentResult.Failed(
                errorCode = readiness.errorCode,
                message = readiness.statusMessage,
                reference = reference,
            )
        }

        val hostActivity = activityLauncherBridge.currentActivity()
            ?: return PaymentResult.Failed(
                errorCode = "ZETTLE_ACTIVITY_MISSING",
                message = "No hay una actividad activa para lanzar el flujo de Zettle.",
                reference = reference,
            )

        val amountMinorUnits = amount.toMinorUnits()
        if (amountMinorUnits <= 0L) {
            return PaymentResult.Failed(
                errorCode = "ZETTLE_INVALID_AMOUNT",
                message = "El monto a cobrar debe ser mayor a cero.",
                reference = reference,
            )
        }

        val transactionReference = TransactionReference.Builder(reference)
            .put("LCX_REFERENCE", reference)
            .build()

        return runCatching {
            val intent = CardReaderAction.Payment(
                reference = transactionReference,
                amount = amountMinorUnits,
            ).charge(hostActivity)

            val activityResult = activityLauncherBridge.launch(intent)
            if (activityResult.resultCode != Activity.RESULT_OK) {
                return@runCatching PaymentResult.Cancelled(reference = reference)
            }

            when (val result = activityResult.data?.zettleResult()) {
                is ZettleResult.Completed<*> -> {
                    val payment: CardPaymentResult.Completed = CardReaderAction.fromPaymentResult(result)
                    PaymentResult.Success(
                        transactionId = payment.payload.transactionId ?: reference,
                        amount = payment.payload.amount.toMajorUnits(),
                        reference = reference,
                    )
                }

                is ZettleResult.Cancelled -> PaymentResult.Cancelled(reference = reference)
                is ZettleResult.Failed -> PaymentResult.Failed(
                    errorCode = "ZETTLE_${result.reason.javaClass.simpleName.uppercase()}",
                    message = "Zettle rechazo el cobro: ${result.reason.javaClass.simpleName}",
                    reference = reference,
                )

                null -> PaymentResult.Failed(
                    errorCode = "ZETTLE_EMPTY_RESULT",
                    message = "Zettle no devolvio un resultado de cobro.",
                    reference = reference,
                )
            }
        }.getOrElse { throwable ->
            Timber.e(throwable, "Zettle payment request failed before completion")
            PaymentResult.Failed(
                errorCode = "ZETTLE_REQUEST_EXCEPTION",
                message = throwable.message ?: "No se pudo iniciar el cobro con Zettle.",
                reference = reference,
            )
        }
    }

    private fun buildReadiness(): ZettleReadiness {
        if (buildConfigProvider.zettleClientId.isBlank()) {
            return ZettleReadiness(
                isConfigValid = false,
                canLaunchPayments = false,
                errorCode = "ZETTLE_CLIENT_ID_MISSING",
                statusMessage = "Falta configurar `LCX_ZETTLE_CLIENT_ID` para usar Zettle.",
            )
        }

        if (buildConfigProvider.zettleRedirectUrl.isBlank()) {
            return ZettleReadiness(
                isConfigValid = false,
                canLaunchPayments = false,
                errorCode = "ZETTLE_REDIRECT_URL_MISSING",
                statusMessage = "Falta configurar `LCX_ZETTLE_REDIRECT_URL` para usar Zettle.",
            )
        }

        if (buildConfigProvider.applicationId != buildConfigProvider.zettleApprovedApplicationId) {
            return ZettleReadiness(
                isConfigValid = false,
                canLaunchPayments = false,
                errorCode = "ZETTLE_APPLICATION_ID_MISMATCH",
                statusMessage =
                    "El APK actual usa `${buildConfigProvider.applicationId}`, " +
                        "pero Zettle aprobo `${buildConfigProvider.zettleApprovedApplicationId}`.",
            )
        }

        if (activityLauncherBridge.currentActivity() == null) {
            return ZettleReadiness(
                isConfigValid = true,
                canLaunchPayments = false,
                errorCode = "ZETTLE_ACTIVITY_MISSING",
                statusMessage = "No hay una actividad activa para lanzar el flujo de Zettle.",
            )
        }

        return ZettleReadiness(
            isConfigValid = true,
            canLaunchPayments = true,
            errorCode = "ZETTLE_READY",
            statusMessage =
                "SDK real de Zettle listo. Verifica permisos de ubicacion y lector antes del smoke.",
        )
    }
}

private data class ZettleReadiness(
    val isConfigValid: Boolean,
    val canLaunchPayments: Boolean,
    val errorCode: String,
    val statusMessage: String,
)

private fun Double.toMinorUnits(): Long =
    BigDecimal.valueOf(this)
        .movePointRight(2)
        .setScale(0, RoundingMode.HALF_UP)
        .longValueExact()

private fun Long.toMajorUnits(): Double =
    BigDecimal.valueOf(this)
        .movePointLeft(2)
        .setScale(2, RoundingMode.HALF_UP)
        .toDouble()

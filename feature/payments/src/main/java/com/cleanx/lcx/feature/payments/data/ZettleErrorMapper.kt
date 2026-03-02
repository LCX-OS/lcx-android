package com.cleanx.lcx.feature.payments.data

/**
 * Maps Zettle SDK error codes to domain [PaymentResult.Failed] instances.
 *
 * Error codes and user-facing messages are in Spanish to match the target
 * operator audience.  When the real SDK is integrated, additional codes
 * should be added here as they are discovered.
 */
object ZettleErrorMapper {

    /** Well-known Zettle SDK error codes. */
    const val CARD_DECLINED = 1001
    const val READER_DISCONNECTED = 1002
    const val NETWORK_ERROR = 1003
    const val SESSION_EXPIRED = 1004
    const val AMOUNT_TOO_LOW = 1005
    const val AMOUNT_TOO_HIGH = 1006
    const val READER_BUSY = 1007

    /**
     * Convert a raw Zettle SDK error into a [PaymentResult.Failed].
     *
     * @param errorCode  Numeric error code from the SDK.
     * @param message    Raw message from the SDK (used as fallback).
     * @param reference  The payment reference / ticket ID.
     */
    fun mapSdkError(errorCode: Int, message: String, reference: String): PaymentResult.Failed {
        return when (errorCode) {
            CARD_DECLINED -> PaymentResult.Failed(
                errorCode = "CARD_DECLINED",
                message = "Tarjeta rechazada",
                reference = reference,
            )
            READER_DISCONNECTED -> PaymentResult.Failed(
                errorCode = "READER_DISCONNECTED",
                message = "Lector desconectado",
                reference = reference,
            )
            NETWORK_ERROR -> PaymentResult.Failed(
                errorCode = "NETWORK_ERROR",
                message = "Error de conexion",
                reference = reference,
            )
            SESSION_EXPIRED -> PaymentResult.Failed(
                errorCode = "SESSION_EXPIRED",
                message = "Sesion expirada, vuelva a iniciar sesion",
                reference = reference,
            )
            AMOUNT_TOO_LOW -> PaymentResult.Failed(
                errorCode = "AMOUNT_TOO_LOW",
                message = "El monto es inferior al minimo permitido",
                reference = reference,
            )
            AMOUNT_TOO_HIGH -> PaymentResult.Failed(
                errorCode = "AMOUNT_TOO_HIGH",
                message = "El monto excede el maximo permitido",
                reference = reference,
            )
            READER_BUSY -> PaymentResult.Failed(
                errorCode = "READER_BUSY",
                message = "El lector esta ocupado, intente de nuevo",
                reference = reference,
            )
            else -> PaymentResult.Failed(
                errorCode = "UNKNOWN_ZETTLE_ERROR",
                message = message,
                reference = reference,
            )
        }
    }
}

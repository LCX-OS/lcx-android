package com.cleanx.lcx.feature.sales.domain

import com.cleanx.lcx.core.model.PaymentMethod
import com.cleanx.lcx.core.network.CorrelationContext
import com.cleanx.lcx.feature.payments.data.PaymentManager
import com.cleanx.lcx.feature.payments.data.PaymentResult
import com.cleanx.lcx.feature.tickets.data.ApiResult
import com.cleanx.lcx.feature.tickets.data.ErrorMessages
import com.cleanx.lcx.feature.tickets.data.TicketRepository
import com.cleanx.lcx.feature.tickets.data.isCustomerDraftValid
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class SalesCheckoutUseCase @Inject constructor(
    private val ticketRepository: TicketRepository,
    private val paymentManager: PaymentManager,
) {

    suspend operator fun invoke(
        request: SalesCheckoutRequest,
        now: Instant = Instant.now(),
    ): SalesCheckoutResult {
        if (!isCustomerDraftValid(request.customer)) {
            return SalesCheckoutResult.ValidationFailure(
                "Selecciona, crea o usa Cliente anónimo para poder cobrar.",
            )
        }

        val total = calculateSalesCartTotal(
            cart = request.cart,
            equipmentServices = request.catalogs.equipmentServices,
            productAddOns = request.catalogs.productAddOns,
            inventoryItems = request.catalogs.inventoryItems,
        )
        if (total <= 0.0) {
            return SalesCheckoutResult.ValidationFailure(
                "Agrega al menos un equipo, producto o inventario vendible al carrito.",
            )
        }

        val drafts = buildSalesTicketDrafts(
            customer = request.customer,
            cart = request.cart,
            equipmentServices = request.catalogs.equipmentServices,
            productAddOns = request.catalogs.productAddOns,
            inventoryItems = request.catalogs.inventoryItems,
            paymentMethod = request.paymentMethod,
            chargedAt = now,
        )
        if (drafts.isEmpty()) {
            return SalesCheckoutResult.ValidationFailure(
                "No se pudo construir la venta con el carrito actual.",
            )
        }

        val correlationId = UUID.randomUUID().toString()

        return when (request.paymentMethod) {
            PaymentMethod.CARD -> submitCardSale(
                drafts = drafts,
                total = total,
                correlationId = correlationId,
            )

            PaymentMethod.CASH,
            PaymentMethod.TRANSFER,
            -> createVentaTickets(
                drafts = drafts,
                correlationId = correlationId,
                transactionId = null,
                createFailureBuilder = { message ->
                    SalesCheckoutResult.CreateFailed(message)
                },
            )
        }
    }

    private suspend fun submitCardSale(
        drafts: List<com.cleanx.lcx.core.network.TicketDraft>,
        total: Double,
        correlationId: String,
    ): SalesCheckoutResult {
        val capability = paymentManager.capability()
        if (!capability.canAcceptPayments) {
            return SalesCheckoutResult.PaymentFailed(capability.statusMessage)
        }
        if (!capability.isInitialized) {
            return SalesCheckoutResult.PaymentFailed(
                "El backend de pagos ${capability.backendLabel} aun no esta inicializado. " +
                    "Reabre la app e intenta de nuevo.",
            )
        }

        val paymentResult = runCatching {
            paymentManager.requestPayment(
                amount = total,
                reference = "venta-$correlationId",
            )
        }.getOrElse { throwable ->
            return SalesCheckoutResult.PaymentFailed(
                throwable.message ?: "No se pudo iniciar el cobro con terminal.",
            )
        }

        return when (paymentResult) {
            is PaymentResult.Cancelled -> SalesCheckoutResult.PaymentCancelled(
                "El cobro con terminal fue cancelado. No se guardó ninguna venta.",
            )

            is PaymentResult.Failed -> SalesCheckoutResult.PaymentFailed(
                paymentResult.message,
            )

            is PaymentResult.Success -> createVentaTickets(
                drafts = drafts,
                correlationId = correlationId,
                transactionId = paymentResult.transactionId,
                createFailureBuilder = { message ->
                    SalesCheckoutResult.CardCapturedCreateFailed(
                        failure = CardCapturedCreateFailure(
                            transactionId = paymentResult.transactionId,
                            amount = paymentResult.amount,
                            correlationId = correlationId,
                            message = message,
                        ),
                    )
                },
            )
        }
    }

    private suspend fun createVentaTickets(
        drafts: List<com.cleanx.lcx.core.network.TicketDraft>,
        correlationId: String,
        transactionId: String?,
        createFailureBuilder: (String) -> SalesCheckoutResult,
    ): SalesCheckoutResult {
        val result = withCorrelationId(correlationId) {
            ticketRepository.createTickets(
                source = "venta",
                tickets = drafts,
                suppressSessionExpiredOnUnauthorized = transactionId != null,
            )
        }

        return when (result) {
            is ApiResult.Success -> SalesCheckoutResult.Success(
                tickets = result.data,
                transactionId = transactionId,
                correlationId = correlationId,
            )

            is ApiResult.Error -> createFailureBuilder(
                ErrorMessages.forCode(result.code, result.message),
            )
        }
    }

    private suspend fun <T> withCorrelationId(
        correlationId: String,
        block: suspend () -> T,
    ): T {
        CorrelationContext.set(correlationId)
        return try {
            block()
        } finally {
            CorrelationContext.clear()
        }
    }
}

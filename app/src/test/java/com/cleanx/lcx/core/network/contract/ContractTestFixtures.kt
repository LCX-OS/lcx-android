package com.cleanx.lcx.core.network.contract

/**
 * Reusable JSON fixtures that match the API contract spec exactly.
 *
 * Every JSON string here mirrors a documented response shape from
 * docs/api-contract-spec.md so that contract tests break whenever
 * the specification or the client's serialization drifts.
 */
object ContractTestFixtures {

    // ---------------------------------------------------------------
    // Shared ticket UUIDs / constants
    // ---------------------------------------------------------------
    const val TICKET_ID = "550e8400-e29b-41d4-a716-446655440000"
    const val TICKET_ID_2 = "660e8400-e29b-41d4-a716-446655440001"
    const val USER_ID = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"

    // ---------------------------------------------------------------
    //  Full ticket JSON (matches contract spec Section 4)
    // ---------------------------------------------------------------
    fun ticketJson(
        id: String = TICKET_ID,
        ticketNumber: String = "T-20260302-0001",
        ticketDate: String = "2026-03-02",
        dailyFolio: Int = 1,
        source: String = "encargo",
        customerId: String? = null,
        customerName: String = "Juan García",
        customerPhone: String = "+56912345678",
        serviceType: String = "wash-fold",
        service: String = "Lavado y Planchado",
        weight: Double? = 5.5,
        status: String = "received",
        notes: String? = "Entregar mañana",
        totalAmount: Double? = 25000.0,
        paymentMethod: String? = "cash",
        paymentStatus: String = "pending",
        paidAmount: Double = 0.0,
        paidAt: String? = null,
        actualPickupDate: String? = null,
        createdAt: String = "2026-03-02T10:30:00Z",
        createdBy: String = USER_ID,
        updatedAt: String = "2026-03-02T10:30:00Z",
        updatedBy: String = USER_ID,
    ): String = """
    {
      "id": "$id",
      "ticket_number": "$ticketNumber",
      "ticket_date": "$ticketDate",
      "daily_folio": $dailyFolio,
      "source": "$source",
      "customer_id": ${customerId?.let { "\"$it\"" } ?: "null"},
      "customer_name": "$customerName",
      "customer_phone": "$customerPhone",
      "service_type": "$serviceType",
      "service": "$service",
      "weight": ${weight ?: "null"},
      "status": "$status",
      "notes": ${notes?.let { "\"$it\"" } ?: "null"},
      "total_amount": ${totalAmount ?: "null"},
      "payment_method": ${paymentMethod?.let { "\"$it\"" } ?: "null"},
      "payment_status": "$paymentStatus",
      "paid_amount": $paidAmount,
      "paid_at": ${paidAt?.let { "\"$it\"" } ?: "null"},
      "actual_pickup_date": ${actualPickupDate?.let { "\"$it\"" } ?: "null"},
      "created_at": "$createdAt",
      "created_by": "$createdBy",
      "updated_at": "$updatedAt",
      "updated_by": "$updatedBy"
    }
    """.trimIndent()

    // ---------------------------------------------------------------
    //  POST /api/tickets — success responses
    // ---------------------------------------------------------------

    /** Single encargo ticket creation success */
    val CREATE_TICKET_ENCARGO_SUCCESS = """{"data": [${ticketJson()}]}"""

    /** Single venta ticket with status defaulting to delivered */
    val CREATE_TICKET_VENTA_DELIVERED_SUCCESS = """{"data": [${ticketJson(
        source = "venta",
        status = "delivered",
        actualPickupDate = "2026-03-02",
    )}]}"""

    /** Batch creation returning two tickets */
    val CREATE_TICKETS_BATCH_SUCCESS = """{"data": [${ticketJson()}, ${ticketJson(
        id = TICKET_ID_2,
        ticketNumber = "T-20260302-0002",
        dailyFolio = 2,
        customerName = "Maria Lopez",
        customerPhone = "+56987654321",
    )}]}"""

    // ---------------------------------------------------------------
    //  POST /api/tickets — error responses
    // ---------------------------------------------------------------

    val NOT_AUTHENTICATED_RESPONSE = """
    {
      "error": "Missing or invalid authentication token",
      "code": "NOT_AUTHENTICATED"
    }
    """.trimIndent()

    val OPENING_CHECKLIST_BLOCKING_RESPONSE = """
    {
      "error": "Opening checklist must be completed before creating tickets",
      "code": "OPENING_CHECKLIST_BLOCKING_OPERATION"
    }
    """.trimIndent()

    val TICKET_NUMBER_CONFLICT_RESPONSE = """
    {
      "error": "Ticket number collision; server generated duplicate",
      "code": "TICKET_NUMBER_CONFLICT",
      "details": "Retry with exponential backoff"
    }
    """.trimIndent()

    val INVALID_CUSTOMER_NAME_RESPONSE = """
    {
      "error": "Validation failed",
      "code": "INVALID_CUSTOMER_NAME",
      "details": "customer_name is required"
    }
    """.trimIndent()

    val INVALID_CUSTOMER_PHONE_RESPONSE = """
    {
      "error": "Validation failed",
      "code": "INVALID_CUSTOMER_PHONE",
      "details": "customer_phone is required when customer_id is not provided"
    }
    """.trimIndent()

    val INVALID_SERVICE_TYPE_RESPONSE = """
    {
      "error": "Validation failed",
      "code": "INVALID_SERVICE_TYPE",
      "details": "service_type must be 'in-store' or 'wash-fold'"
    }
    """.trimIndent()

    val INVALID_PAYMENT_METHOD_RESPONSE = """
    {
      "error": "Validation failed",
      "code": "INVALID_PAYMENT_METHOD",
      "details": "payment_method must be 'cash', 'card', or 'transfer'"
    }
    """.trimIndent()

    val INVALID_PAYMENT_STATUS_RESPONSE = """
    {
      "error": "Validation failed",
      "code": "INVALID_PAYMENT_STATUS",
      "details": "payment_status must be 'pending', 'prepaid', or 'paid'"
    }
    """.trimIndent()

    val INTERNAL_SERVER_ERROR_RESPONSE = """
    {
      "error": "Unexpected server error",
      "code": "INTERNAL_SERVER_ERROR"
    }
    """.trimIndent()

    // ---------------------------------------------------------------
    //  PATCH /api/tickets/:id/status — success responses
    // ---------------------------------------------------------------

    /** Status updated to delivered — server auto-sets actual_pickup_date */
    val STATUS_UPDATE_DELIVERED_SUCCESS = """{"data": ${ticketJson(
        status = "delivered",
        actualPickupDate = "2026-03-02",
        paymentStatus = "paid",
        paymentMethod = "cash",
        paidAmount = 25000.0,
        paidAt = "2026-03-02T14:20:00Z",
        updatedAt = "2026-03-02T14:25:00Z",
    )}}"""

    val STATUS_UPDATE_PROCESSING_SUCCESS = """{"data": ${ticketJson(
        status = "processing",
        updatedAt = "2026-03-02T11:00:00Z",
    )}}"""

    // ---------------------------------------------------------------
    //  PATCH /api/tickets/:id/status — error responses
    // ---------------------------------------------------------------

    val INVALID_STATUS_TRANSITION_RESPONSE = """
    {
      "error": "Invalid status transition",
      "code": "INVALID_STATUS_TRANSITION",
      "details": "Cannot transition from 'ready' to 'processing'"
    }
    """.trimIndent()

    val INSUFFICIENT_PERMISSIONS_RESPONSE = """
    {
      "error": "Insufficient permissions to update ticket status",
      "code": "INSUFFICIENT_PERMISSIONS"
    }
    """.trimIndent()

    val NOT_FOUND_RESPONSE = """
    {
      "error": "Ticket not found",
      "code": "NOT_FOUND"
    }
    """.trimIndent()

    // ---------------------------------------------------------------
    //  PATCH /api/tickets/:id/payment — success responses
    // ---------------------------------------------------------------

    /** Payment marked as paid with card */
    val PAYMENT_UPDATE_PAID_CARD_SUCCESS = """{"data": ${ticketJson(
        status = "ready",
        paymentStatus = "paid",
        paymentMethod = "card",
        paidAmount = 25000.0,
        paidAt = "2026-03-02T14:20:00Z",
        updatedAt = "2026-03-02T14:25:00Z",
    )}}"""

    /** Payment reset to pending — paid_amount goes to 0, paid_at null */
    val PAYMENT_UPDATE_PENDING_SUCCESS = """{"data": ${ticketJson(
        status = "ready",
        paymentStatus = "pending",
        paymentMethod = null,
        paidAmount = 0.0,
        paidAt = null,
        updatedAt = "2026-03-02T14:25:00Z",
    )}}"""

    /** Prepaid auto-promotion: sent as prepaid but returned as paid */
    val PREPAID_AUTOPROMOTE_RESPONSE = """{"data": ${ticketJson(
        status = "ready",
        paymentStatus = "paid",
        paymentMethod = "cash",
        paidAmount = 25000.0,
        paidAt = "2026-03-02T14:20:00Z",
        totalAmount = 25000.0,
        updatedAt = "2026-03-02T14:25:00Z",
    )}}"""

    /** Prepaid without auto-promotion (paid_amount < total_amount) */
    val PREPAID_NO_PROMOTION_RESPONSE = """{"data": ${ticketJson(
        status = "ready",
        paymentStatus = "prepaid",
        paymentMethod = "cash",
        paidAmount = 10000.0,
        paidAt = "2026-03-02T14:20:00Z",
        totalAmount = 25000.0,
        updatedAt = "2026-03-02T14:25:00Z",
    )}}"""

    // ---------------------------------------------------------------
    //  PATCH /api/tickets/:id/payment — error responses
    // ---------------------------------------------------------------

    val INVALID_PAYMENT_STATUS_PATCH_RESPONSE = """
    {
      "error": "Invalid payment status",
      "code": "INVALID_PAYMENT_STATUS",
      "details": "payment_status must be 'pending', 'prepaid', or 'paid'"
    }
    """.trimIndent()

    val PAYMENT_INSUFFICIENT_PERMISSIONS_RESPONSE = """
    {
      "error": "Insufficient permissions to update ticket payment",
      "code": "INSUFFICIENT_PERMISSIONS"
    }
    """.trimIndent()

    val PAYMENT_NOT_FOUND_RESPONSE = """
    {
      "error": "Ticket not found",
      "code": "NOT_FOUND"
    }
    """.trimIndent()

    // ---------------------------------------------------------------
    //  Serialization test fixtures — minimal / edge-case shapes
    // ---------------------------------------------------------------

    /** Ticket with all optional fields set to null */
    val TICKET_ALL_NULLS_JSON = """
    {
      "id": "$TICKET_ID",
      "ticket_number": "T-20260302-0001",
      "ticket_date": "2026-03-02",
      "daily_folio": 1,
      "source": "encargo",
      "customer_id": null,
      "customer_name": "Juan García",
      "customer_phone": null,
      "service_type": "wash-fold",
      "service": null,
      "weight": null,
      "status": "received",
      "notes": null,
      "total_amount": null,
      "payment_method": null,
      "payment_status": "pending",
      "paid_amount": 0,
      "paid_at": null,
      "actual_pickup_date": null,
      "created_at": "2026-03-02T10:30:00Z",
      "created_by": "$USER_ID",
      "updated_at": "2026-03-02T10:30:00Z",
      "updated_by": "$USER_ID"
    }
    """.trimIndent()

    /** Ticket with extra unknown fields the client should ignore */
    val TICKET_WITH_UNKNOWN_FIELDS_JSON = """
    {
      "id": "$TICKET_ID",
      "ticket_number": "T-20260302-0001",
      "ticket_date": "2026-03-02",
      "daily_folio": 1,
      "source": "encargo",
      "customer_id": null,
      "customer_name": "Juan García",
      "customer_phone": "+56912345678",
      "service_type": "wash-fold",
      "service": "Lavado y Planchado",
      "weight": 5.5,
      "status": "received",
      "notes": null,
      "total_amount": 25000,
      "payment_method": "cash",
      "payment_status": "pending",
      "paid_amount": 0,
      "paid_at": null,
      "actual_pickup_date": null,
      "created_at": "2026-03-02T10:30:00Z",
      "created_by": "$USER_ID",
      "updated_at": "2026-03-02T10:30:00Z",
      "updated_by": "$USER_ID",
      "unknown_new_field": "should be ignored",
      "another_unknown": 42
    }
    """.trimIndent()
}

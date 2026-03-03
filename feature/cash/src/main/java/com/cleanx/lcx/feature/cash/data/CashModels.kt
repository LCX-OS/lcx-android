package com.cleanx.lcx.feature.cash.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Movement types matching the `cash_movement_type` DB enum */
@Serializable
enum class MovementType {
    @SerialName("opening") OPENING,
    @SerialName("closing") CLOSING,
    @SerialName("income") INCOME,
    @SerialName("expense") EXPENSE,
}

/** Denomination breakdown stored in movement metadata (MXN bills + coins) */
@Serializable
data class DenominationBreakdown(
    @SerialName("bills_1000") val bills1000: Int = 0,
    @SerialName("bills_500") val bills500: Int = 0,
    @SerialName("bills_200") val bills200: Int = 0,
    @SerialName("bills_100") val bills100: Int = 0,
    @SerialName("bills_50") val bills50: Int = 0,
    @SerialName("bills_20") val bills20: Int = 0,
    @SerialName("coins_20") val coins20: Int = 0,
    @SerialName("coins_10") val coins10: Int = 0,
    @SerialName("coins_5") val coins5: Int = 0,
    @SerialName("coins_2") val coins2: Int = 0,
    @SerialName("coins_1") val coins1: Int = 0,
    @SerialName("coins_50c") val coins50c: Int = 0,
) {
    fun total(): Double =
        bills1000 * 1000.0 + bills500 * 500.0 + bills200 * 200.0 +
        bills100 * 100.0 + bills50 * 50.0 + bills20 * 20.0 +
        coins20 * 20.0 + coins10 * 10.0 + coins5 * 5.0 +
        coins2 * 2.0 + coins1 * 1.0 + coins50c * 0.5
}

/** Metadata JSON embedded in cash_movements.metadata column */
@Serializable
data class CashMovementMetadata(
    val denominations: DenominationBreakdown? = null,
    val payments: String? = null,
    @SerialName("total_sales_for_day") val totalSalesForDay: Double? = null,
    @SerialName("expected_closing_amount") val expectedClosingAmount: Double? = null,
    @SerialName("discrepancy_type") val discrepancyType: String? = null,
)

/** Row model for cash_movements table (read). Matches PWA CashMovementWithProfile. */
@Serializable
data class CashMovementRow(
    val id: String? = null,
    val type: MovementType,
    val amount: Double,
    @SerialName("previous_balance") val previousBalance: Double = 0.0,
    val difference: Double = 0.0,
    val notes: String? = null,
    @SerialName("user_id") val userId: String? = null,
    val metadata: CashMovementMetadata? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    val profiles: UserProfile? = null,
) {
    @Serializable
    data class UserProfile(
        @SerialName("full_name") val fullName: String? = null,
    )
}

/** Insert model for cash_movements table (write). */
@Serializable
data class CashMovementInsert(
    val type: MovementType,
    val amount: Double,
    @SerialName("previous_balance") val previousBalance: Double = 0.0,
    val difference: Double = 0.0,
    val notes: String? = null,
    @SerialName("user_id") val userId: String? = null,
    val metadata: CashMovementMetadata? = null,
)

/** Aggregated daily summary, computed client-side from today's movements. */
data class CashSummary(
    val openingAmount: Double = 0.0,
    val currentAmount: Double = 0.0,
    val totalIncome: Double = 0.0,
    val totalExpenses: Double = 0.0,
    val netChange: Double = 0.0,
    val movementCount: Int = 0,
    val canClose: Boolean = false,
    val canCloseReason: String? = null,
)

/** Bill denominations for MXN */
data class Denomination(
    val value: Double,
    val label: String,
)

val BILL_DENOMINATIONS = listOf(
    Denomination(1000.0, "$1,000"),
    Denomination(500.0, "$500"),
    Denomination(200.0, "$200"),
    Denomination(100.0, "$100"),
    Denomination(50.0, "$50"),
    Denomination(20.0, "$20"),
)

val COIN_DENOMINATIONS = listOf(
    Denomination(20.0, "$20"),
    Denomination(10.0, "$10"),
    Denomination(5.0, "$5"),
    Denomination(2.0, "$2"),
    Denomination(1.0, "$1"),
    Denomination(0.5, "$0.50"),
)

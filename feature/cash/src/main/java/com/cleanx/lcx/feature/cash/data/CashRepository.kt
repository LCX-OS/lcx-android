package com.cleanx.lcx.feature.cash.data

import com.cleanx.lcx.core.network.SupabaseTableClient
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import timber.log.Timber
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for cash register (caja) operations.
 *
 * Uses [SupabaseTableClient] for direct table access to `cash_movements`.
 * Follows the same pattern as [WaterRepository].
 */
@Singleton
class CashRepository @Inject constructor(
    private val supabase: SupabaseTableClient,
) {
    private val table = "cash_movements"

    // -- TODAY'S SUMMARY ------------------------------------------------------

    /**
     * Get today's cash summary (all movements for today, aggregated client-side).
     *
     * Queries `cash_movements` where `created_at >= today start`, orders by
     * `created_at ASC`, then computes opening amount, income/expense totals,
     * current amount, movement count, and whether the register can be closed.
     */
    suspend fun getTodaySummary(): Result<CashSummary> {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        Timber.tag("CAJA").d("Fetching today's cash summary (date=%s)", today)

        return supabase.selectWithRequest<CashMovementRow>(
            table = table,
        ) {
            filter { gte("created_at", "${today}T00:00:00") }
            order("created_at", Order.ASCENDING)
        }.map { movements ->
            computeSummary(movements)
        }
    }

    // -- LATEST MOVEMENT ------------------------------------------------------

    /**
     * Get the latest cash movement (for `previous_balance` calculation).
     * Returns the most recent movement regardless of date.
     */
    suspend fun getLatestMovement(): Result<CashMovementRow?> {
        Timber.tag("CAJA").d("Fetching latest cash movement")

        return supabase.selectWithRequest<CashMovementRow>(
            table = table,
        ) {
            order("created_at", Order.DESCENDING)
            limit(1)
        }.map { it.firstOrNull() }
    }

    // -- RECORD MOVEMENT ------------------------------------------------------

    /**
     * Record a new cash movement.
     * Mirrors PWA's `recordCashMovement(type, amount, options)`.
     */
    suspend fun recordMovement(insert: CashMovementInsert): Result<CashMovementRow> {
        Timber.tag("CAJA").d(
            "Recording cash movement: type=%s, amount=%.2f",
            insert.type,
            insert.amount,
        )

        return supabase.insert(table, insert)
    }

    // -- MOVEMENT HISTORY -----------------------------------------------------

    /**
     * Get movement history with profile data (user full names).
     * Supports optional date-range filtering.
     *
     * Mirrors PWA's `getCashMovementHistory(startDate, endDate, limit)`.
     */
    suspend fun getMovementHistory(
        startDate: String? = null,
        endDate: String? = null,
        limit: Long = 50,
    ): Result<List<CashMovementRow>> {
        Timber.tag("CAJA").d(
            "Fetching movement history (start=%s, end=%s, limit=%d)",
            startDate,
            endDate,
            limit,
        )
        val columns = Columns.raw(
            "*, profiles!cash_movements_user_id_fkey(full_name)"
        )
        return supabase.selectWithRequest<CashMovementRow>(
            table = table,
            columns = columns,
        ) {
            filter {
                if (startDate != null) gte("created_at", "${startDate}T00:00:00")
                if (endDate != null) lte("created_at", "${endDate}T23:59:59")
            }
            order("created_at", Order.DESCENDING)
            limit(limit)
        }
    }

    // -- CAN CLOSE? -----------------------------------------------------------

    /**
     * Check if the cash register can be closed today.
     *
     * Returns a pair: `(canClose, reason)`.
     * - `canClose = true` when today has an opening but no closing yet.
     * - `reason` explains why closing is not possible (no opening, already closed).
     */
    suspend fun canCloseCashRegister(): Result<Pair<Boolean, String?>> {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        Timber.tag("CAJA").d("Checking if cash register can be closed (date=%s)", today)

        return supabase.selectWithRequest<CashMovementRow>(
            table = table,
        ) {
            filter { gte("created_at", "${today}T00:00:00") }
            order("created_at", Order.ASCENDING)
        }.map { movements ->
            val hasOpening = movements.any { it.type == MovementType.OPENING }
            val hasClosure = movements.any { it.type == MovementType.CLOSING }

            when {
                !hasOpening -> false to "No se ha abierto la caja hoy"
                hasClosure -> false to "La caja ya fue cerrada hoy"
                else -> true to null
            }
        }
    }

    // -- PRIVATE HELPERS ------------------------------------------------------

    /**
     * Compute an aggregated [CashSummary] from a list of today's movements.
     */
    private fun computeSummary(movements: List<CashMovementRow>): CashSummary {
        if (movements.isEmpty()) {
            return CashSummary()
        }

        val openingMovement = movements.firstOrNull { it.type == MovementType.OPENING }
        val closingMovement = movements.firstOrNull { it.type == MovementType.CLOSING }
        val openingAmount = openingMovement?.amount ?: 0.0

        val totalIncome = movements
            .filter { it.type == MovementType.INCOME }
            .sumOf { it.amount }
        val totalExpenses = movements
            .filter { it.type == MovementType.EXPENSE }
            .sumOf { it.amount }

        val netChange = totalIncome - totalExpenses
        val currentAmount = if (closingMovement != null) {
            closingMovement.amount
        } else {
            openingAmount + netChange
        }

        val hasOpening = openingMovement != null
        val hasClosure = closingMovement != null
        val canClose = hasOpening && !hasClosure
        val canCloseReason = when {
            !hasOpening -> "No se ha abierto la caja hoy"
            hasClosure -> "La caja ya fue cerrada hoy"
            else -> null
        }

        return CashSummary(
            openingAmount = openingAmount,
            currentAmount = currentAmount,
            totalIncome = totalIncome,
            totalExpenses = totalExpenses,
            netChange = netChange,
            movementCount = movements.size,
            canClose = canClose,
            canCloseReason = canCloseReason,
        )
    }
}

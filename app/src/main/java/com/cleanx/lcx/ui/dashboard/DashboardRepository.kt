package com.cleanx.lcx.ui.dashboard

import com.cleanx.lcx.core.network.SupabaseTableClient
import com.cleanx.lcx.core.operational.OperatorOperationalRepository
import com.cleanx.lcx.core.session.SessionProfileRepository
import com.cleanx.lcx.feature.tickets.data.ApiResult
import com.cleanx.lcx.feature.tickets.data.InventoryCatalogRecord
import com.cleanx.lcx.feature.tickets.data.TicketRepository
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DashboardRepository @Inject constructor(
    private val sessionProfileRepository: SessionProfileRepository,
    private val operationalRepository: OperatorOperationalRepository,
    private val ticketRepository: TicketRepository,
    private val supabase: SupabaseTableClient,
) {

    suspend fun loadSnapshot(now: Instant = Instant.now()): DashboardSnapshot = coroutineScope {
        val profile = sessionProfileRepository.getCurrentProfile()

        val operationalDeferred = async { operationalRepository.loadSnapshot(branch = profile.branch) }
        val ticketsDeferred = async { loadPendingTickets(now = now) }
        val suppliesDeferred = async { loadSupplyNeeds(branch = profile.branch) }
        val operationalSnapshot = operationalDeferred.await()

        DashboardSnapshot(
            operatorName = profile.fullName?.trim().takeUnless { it.isNullOrEmpty() } ?: "Operador",
            branchName = profile.branch?.trim()?.takeIf { it.isNotEmpty() },
            operationalSummary = operationalSnapshot.summary.toDashboardOperationalSummary(),
            routine = operationalSnapshot.routine.toDashboardRoutineSection(),
            pendingTickets = ticketsDeferred.await(),
            supplyNeeds = suppliesDeferred.await(),
        )
    }

    private suspend fun loadPendingTickets(now: Instant): DashboardPendingTicketsSection {
        return when (val result = ticketRepository.getTickets(limit = 200)) {
            is ApiResult.Success -> {
                val pendingTickets = result.data
                    .filter { isOperationalPendingTicket(it.status) }
                    .sortedByDescending { it.createdAt.orEmpty() }

                DashboardPendingTicketsSection(
                    totalCount = pendingTickets.size,
                    items = pendingTickets
                        .take(3)
                        .map { it.toDashboardTicketItem(now = now) },
                )
            }

            is ApiResult.Error -> DashboardPendingTicketsSection(
                error = result.message,
            )
        }
    }

    private suspend fun loadSupplyNeeds(branch: String?): DashboardSupplyNeedsSection {
        val inventory = supabase.selectWithRequest<InventoryCatalogRecord>("inventory") {
            order("quantity", Order.ASCENDING)
            limit(200)
        }.getOrElse { error ->
            return DashboardSupplyNeedsSection(
                error = error.message ?: "No se pudo cargar el inventario.",
            )
        }

        val scopedInventory = if (branch == null) {
            inventory
        } else {
            inventory.filter { item ->
                item.branch.isNullOrBlank() || item.branch.equals(branch, ignoreCase = true)
            }
        }

        val needs = scopedInventory
            .mapNotNull { it.toDashboardSupplyNeed() }
            .sortedWith(
                compareBy<DashboardSupplyNeed>(
                    { it.severity != DashboardSupplySeverity.CRITICAL },
                    { it.quantity },
                    { it.itemName.lowercase() },
                ),
            )

        return DashboardSupplyNeedsSection(
            totalCount = needs.size,
            items = needs.take(3),
        )
    }
}

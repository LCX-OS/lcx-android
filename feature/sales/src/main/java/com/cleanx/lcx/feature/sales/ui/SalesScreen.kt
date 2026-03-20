package com.cleanx.lcx.feature.sales.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.cleanx.lcx.core.ui.LcxTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cleanx.lcx.core.model.PaymentMethod
import com.cleanx.lcx.core.theme.LcxSpacing
import com.cleanx.lcx.core.theme.LcxSuccess
import com.cleanx.lcx.core.ui.ButtonVariant
import com.cleanx.lcx.core.ui.ErrorState
import com.cleanx.lcx.core.ui.LcxButton
import com.cleanx.lcx.core.ui.LcxCard
import com.cleanx.lcx.core.ui.LcxConfirmationDialog
import com.cleanx.lcx.core.ui.LcxTextField
import com.cleanx.lcx.feature.tickets.data.AddOnCatalogRecord
import com.cleanx.lcx.feature.tickets.data.CustomerDraft
import com.cleanx.lcx.feature.tickets.data.CustomerRecord
import com.cleanx.lcx.feature.tickets.data.InventoryCatalogRecord
import com.cleanx.lcx.feature.tickets.data.ServiceCatalogRecord
import com.cleanx.lcx.feature.tickets.data.normalizePhone
import com.cleanx.lcx.feature.tickets.domain.create.CustomerPickerUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesScreen(
    viewModel: SalesViewModel,
    onBack: () -> Unit = {},
    showTopBar: Boolean = true,
) {
    val state by viewModel.uiState.collectAsState()
    val visibleInventoryItems = remember(
        state.inventoryItems,
        state.cart,
        state.inventorySearchQuery,
    ) {
        buildVisibleInventoryItems(
            items = state.inventoryItems,
            selectedQuantities = state.cart,
            query = state.inventorySearchQuery,
        )
    }

    if (state.customer.pendingCreateInput != null && state.customer.duplicatePhoneMatches.isNotEmpty()) {
        LcxConfirmationDialog(
            title = "Telefono duplicado",
            message = duplicatePhoneMessage(state.customer.duplicatePhoneMatches),
            confirmLabel = "Crear de todos modos",
            cancelLabel = "Revisar",
            onConfirm = viewModel::confirmCreateCustomerDespiteDuplicate,
            onDismiss = viewModel::dismissDuplicateCustomerDialog,
        )
    }

    Scaffold(
        topBar = {
            if (showTopBar) {
                LcxTopAppBar(
                    title = {
                        Text(
                            text = "Autoservicio / Ventas",
                            modifier = Modifier.semantics { heading() },
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Volver",
                            )
                        }
                    },
                )
            }
        },
        bottomBar = {
            LcxCard(
                modifier = Modifier.navigationBarsPadding(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(LcxSpacing.xs)) {
                        Text(
                            text = "Total a cobrar",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = formatCurrency(state.totalPrice),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    LcxButton(
                        text = if (state.paymentMethod == PaymentMethod.CARD) {
                            "Cobrar tarjeta"
                        } else {
                            "Cobrar"
                        },
                        onClick = viewModel::submit,
                        isLoading = state.isSubmitting,
                        enabled = !state.isLoadingCatalogs &&
                            !state.customer.isSaving &&
                            state.totalPrice > 0.0 &&
                            state.criticalFailure == null,
                    )
                }
            }
        },
    ) { padding ->
        when {
            state.isLoadingCatalogs && state.equipmentServices.isEmpty() &&
                state.productAddOns.isEmpty() &&
                state.inventoryItems.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            state.catalogError != null &&
                state.equipmentServices.isEmpty() &&
                state.productAddOns.isEmpty() &&
                state.inventoryItems.isEmpty() -> {
                ErrorState(
                    message = state.catalogError.orEmpty(),
                    modifier = Modifier.padding(padding),
                    onRetry = viewModel::retryCatalogs,
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = LcxSpacing.md)
                        .imePadding(),
                    verticalArrangement = Arrangement.spacedBy(LcxSpacing.sm),
                ) {
                    item { Spacer(modifier = Modifier.height(LcxSpacing.xs)) }

                    if (state.catalogError != null) {
                        item {
                            LcxCard {
                                Text(
                                    text = state.catalogError.orEmpty(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                Spacer(modifier = Modifier.height(LcxSpacing.sm))
                                LcxButton(
                                    text = "Reintentar catalogos",
                                    onClick = viewModel::retryCatalogs,
                                    variant = ButtonVariant.Secondary,
                                )
                            }
                        }
                    }

                    state.successMessage?.let { message ->
                        item {
                            LcxCard {
                                Text(
                                    text = message,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = LcxSuccess,
                                )
                            }
                        }
                    }

                    state.criticalFailure?.let { failure ->
                        item {
                            LcxCard(title = "Cobro capturado, venta pendiente") {
                                Text(
                                    text = failure.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                Spacer(modifier = Modifier.height(LcxSpacing.sm))
                                Text(
                                    text = "Transaccion terminal: ${failure.transactionId}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    text = "Monto: ${formatCurrency(failure.amount)}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    text = "Correlation ID: ${failure.correlationId}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Spacer(modifier = Modifier.height(LcxSpacing.sm))
                                Text(
                                    text = "Verifica manualmente en caja/reportes antes de volver a cobrar. No se reintenta en automático para evitar duplicados.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.height(LcxSpacing.md))
                                LcxButton(
                                    text = "Iniciar nueva venta",
                                    onClick = viewModel::resetAfterCriticalFailure,
                                    variant = ButtonVariant.Secondary,
                                )
                            }
                        }
                    }

                    item {
                        CustomerSection(
                            state = state,
                            onSearchQueryChanged = viewModel::onCustomerSearchQueryChanged,
                            onOpenCreateForm = viewModel::openCreateCustomerForm,
                            onCancelCreateForm = viewModel::cancelCreateCustomerForm,
                            onUseAnonymousCustomer = viewModel::useAnonymousCustomer,
                            onClearSelectedCustomer = viewModel::clearSelectedCustomer,
                            onCustomerSelected = viewModel::selectCustomer,
                            onCustomerFullNameChanged = viewModel::onCustomerFullNameChanged,
                            onCustomerPhoneChanged = viewModel::onCustomerPhoneChanged,
                            onCustomerEmailChanged = viewModel::onCustomerEmailChanged,
                            onCustomerNotesChanged = viewModel::onCustomerNotesChanged,
                            onCreateCustomer = viewModel::createCustomer,
                        )
                    }

                    item {
                        PaymentSection(
                            paymentMethod = state.paymentMethod,
                            enabled = !state.isSubmitting && state.criticalFailure == null,
                            onMethodChanged = viewModel::onPaymentMethodChanged,
                        )
                    }

                    item {
                        QuantitySection(
                            title = "Equipos",
                            description = "Cada equipo genera su propio ticket en la venta.",
                            items = state.equipmentServices.map { service ->
                                QuantityItem(
                                    id = service.id,
                                    title = service.name,
                                    subtitle = service.description ?: service.category,
                                    price = service.price,
                                    quantity = state.cart[service.id] ?: 0,
                                )
                            },
                            emptyMessage = "No hay equipos activos en el catalogo.",
                            enabled = !state.isSubmitting,
                            onAdjustQuantity = viewModel::adjustQuantity,
                        )
                    }

                    item {
                        QuantitySection(
                            title = "Productos",
                            description = "Los productos y el inventario vendible se consolidan en un ticket de productos.",
                            items = state.productAddOns.map { addOn ->
                                QuantityItem(
                                    id = addOn.id,
                                    title = addOn.name,
                                    subtitle = addOn.description,
                                    price = addOn.price,
                                    quantity = state.cart[addOn.id] ?: 0,
                                )
                            },
                            emptyMessage = "No hay productos activos para autoservicio.",
                            enabled = !state.isSubmitting,
                            onAdjustQuantity = viewModel::adjustQuantity,
                        )
                    }

                    item {
                        InventorySection(
                            query = state.inventorySearchQuery,
                            items = visibleInventoryItems,
                            quantities = state.cart,
                            enabled = !state.isSubmitting,
                            onQueryChanged = viewModel::onInventorySearchQueryChanged,
                            onAdjustQuantity = viewModel::adjustQuantity,
                        )
                    }

                    item {
                        SummarySection(state = state)
                    }

                    state.error?.let { message ->
                        item {
                            LcxCard {
                                Text(
                                    text = message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(96.dp)) }
                }
            }
        }
    }
}

@Composable
private fun CustomerSection(
    state: SalesUiState,
    onSearchQueryChanged: (String) -> Unit,
    onOpenCreateForm: () -> Unit,
    onCancelCreateForm: () -> Unit,
    onUseAnonymousCustomer: () -> Unit,
    onClearSelectedCustomer: () -> Unit,
    onCustomerSelected: (CustomerRecord) -> Unit,
    onCustomerFullNameChanged: (String) -> Unit,
    onCustomerPhoneChanged: (String) -> Unit,
    onCustomerEmailChanged: (String) -> Unit,
    onCustomerNotesChanged: (String) -> Unit,
    onCreateCustomer: () -> Unit,
) {
    val customerState = state.customer

    LcxCard(title = "Cliente") {
        Text(
            text = "Busca por nombre, telefono o correo. Si no existe, puedes crearlo o cobrar como cliente anónimo.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (customerState.selectedCustomer.isReady() && !customerState.showCreateForm) {
            Spacer(modifier = Modifier.height(LcxSpacing.md))
            SelectedCustomerSummary(
                customer = customerState.selectedCustomer,
                enabled = !state.isSubmitting,
                onClear = onClearSelectedCustomer,
            )
        } else {
            Spacer(modifier = Modifier.height(LcxSpacing.md))
            LcxTextField(
                value = customerState.searchQuery,
                onValueChange = onSearchQueryChanged,
                label = "Buscar cliente",
                enabled = !state.isSubmitting && !customerState.isSaving,
            )

            when {
                customerState.isSearching -> {
                    Spacer(modifier = Modifier.height(LcxSpacing.sm))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(LcxSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator()
                        Text("Buscando clientes...")
                    }
                }

                customerState.searchError != null -> {
                    Spacer(modifier = Modifier.height(LcxSpacing.sm))
                    Text(
                        text = customerState.searchError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                customerState.searchResults.isNotEmpty() && !customerState.showCreateForm -> {
                    Spacer(modifier = Modifier.height(LcxSpacing.sm))
                    Column(verticalArrangement = Arrangement.spacedBy(LcxSpacing.xs)) {
                        customerState.searchResults.forEach { customer ->
                            SearchResultRow(
                                customer = customer,
                                enabled = !state.isSubmitting,
                                onClick = { onCustomerSelected(customer) },
                            )
                        }
                    }
                }

                customerState.searchQuery.trim().length >= 2 && !customerState.showCreateForm -> {
                    Spacer(modifier = Modifier.height(LcxSpacing.sm))
                    Text(
                        text = "Sin coincidencias. Puedes crear el cliente abajo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (!customerState.showCreateForm) {
                Spacer(modifier = Modifier.height(LcxSpacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(LcxSpacing.sm)) {
                    LcxButton(
                        text = "Crear cliente",
                        onClick = onOpenCreateForm,
                        enabled = !state.isSubmitting,
                        variant = ButtonVariant.Secondary,
                    )
                    LcxButton(
                        text = "Cliente anónimo",
                        onClick = onUseAnonymousCustomer,
                        enabled = !state.isSubmitting,
                        variant = ButtonVariant.Secondary,
                    )
                }
            }

            if (customerState.showCreateForm) {
                Spacer(modifier = Modifier.height(LcxSpacing.md))
                CreateCustomerForm(
                    customerState = customerState,
                    enabled = !state.isSubmitting,
                    onCustomerFullNameChanged = onCustomerFullNameChanged,
                    onCustomerPhoneChanged = onCustomerPhoneChanged,
                    onCustomerEmailChanged = onCustomerEmailChanged,
                    onCustomerNotesChanged = onCustomerNotesChanged,
                    onSubmit = onCreateCustomer,
                    onCancel = onCancelCreateForm,
                )
            }
        }
    }
}

@Composable
private fun SelectedCustomerSummary(
    customer: CustomerDraft,
    enabled: Boolean,
    onClear: () -> Unit,
) {
    LcxCard {
        Text(
            text = customer.fullName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(LcxSpacing.xs))
        Text(
            text = customer.phone,
            style = MaterialTheme.typography.bodyMedium,
        )
        customer.email.takeIf { it.isNotBlank() }?.let { email ->
            Spacer(modifier = Modifier.height(LcxSpacing.xs))
            Text(
                text = email,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(LcxSpacing.sm))
        TextButton(
            onClick = onClear,
            enabled = enabled,
        ) {
            Text("Cambiar cliente")
        }
    }
}

@Composable
private fun SearchResultRow(
    customer: CustomerRecord,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    LcxCard {
        Text(
            text = customer.fullName,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(LcxSpacing.xs))
        Text(
            text = customer.phone,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        customer.email?.takeIf { it.isNotBlank() }?.let { email ->
            Spacer(modifier = Modifier.height(LcxSpacing.xs))
            Text(
                text = email,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(LcxSpacing.sm))
        TextButton(onClick = onClick, enabled = enabled) {
            Text("Seleccionar")
        }
    }
}

@Composable
private fun CreateCustomerForm(
    customerState: CustomerPickerUiState,
    enabled: Boolean,
    onCustomerFullNameChanged: (String) -> Unit,
    onCustomerPhoneChanged: (String) -> Unit,
    onCustomerEmailChanged: (String) -> Unit,
    onCustomerNotesChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
) {
    val customer = customerState.selectedCustomer

    Text(
        text = "Nuevo cliente",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(modifier = Modifier.height(LcxSpacing.sm))

    LcxTextField(
        value = customer.fullName,
        onValueChange = onCustomerFullNameChanged,
        label = "Nombre completo *",
        error = customerState.validationErrors.fullName,
        enabled = enabled && !customerState.isSaving,
    )
    Spacer(modifier = Modifier.height(LcxSpacing.sm))

    LcxTextField(
        value = customer.phone,
        onValueChange = onCustomerPhoneChanged,
        label = "Telefono *",
        keyboardType = KeyboardType.Phone,
        error = customerState.validationErrors.phone,
        enabled = enabled && !customerState.isSaving,
    )
    Spacer(modifier = Modifier.height(LcxSpacing.sm))

    LcxTextField(
        value = customer.email,
        onValueChange = onCustomerEmailChanged,
        label = "Correo",
        error = customerState.validationErrors.email,
        enabled = enabled && !customerState.isSaving,
    )
    Spacer(modifier = Modifier.height(LcxSpacing.sm))

    LcxTextField(
        value = customerState.createNotes,
        onValueChange = onCustomerNotesChanged,
        label = "Notas del cliente",
        singleLine = false,
        maxLines = 3,
        enabled = enabled && !customerState.isSaving,
    )

    customerState.submitError?.let { error ->
        Spacer(modifier = Modifier.height(LcxSpacing.sm))
        Text(
            text = error,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }

    Spacer(modifier = Modifier.height(LcxSpacing.sm))
    Row(horizontalArrangement = Arrangement.spacedBy(LcxSpacing.sm)) {
        LcxButton(
            text = "Guardar cliente",
            onClick = onSubmit,
            isLoading = customerState.isSaving,
            enabled = enabled,
        )
        LcxButton(
            text = "Cancelar",
            onClick = onCancel,
            enabled = enabled && !customerState.isSaving,
            variant = ButtonVariant.Secondary,
        )
    }
}

@Composable
private fun PaymentSection(
    paymentMethod: PaymentMethod,
    enabled: Boolean,
    onMethodChanged: (PaymentMethod) -> Unit,
) {
    LcxCard(title = "Pago") {
        Text(
            text = "Efectivo y transferencia se persisten directo. Tarjeta usa la terminal nativa antes de crear los tickets.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(LcxSpacing.sm))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(LcxSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(LcxSpacing.xs),
        ) {
            PaymentMethod.entries.forEach { method ->
                FilterChip(
                    selected = paymentMethod == method,
                    onClick = { onMethodChanged(method) },
                    enabled = enabled,
                    label = { Text(paymentMethodLabel(method)) },
                )
            }
        }
    }
}

private data class QuantityItem(
    val id: String,
    val title: String,
    val subtitle: String?,
    val price: Double,
    val quantity: Int,
)

@Composable
private fun QuantitySection(
    title: String,
    description: String,
    items: List<QuantityItem>,
    emptyMessage: String,
    enabled: Boolean,
    onAdjustQuantity: (String, Int) -> Unit,
) {
    LcxCard(title = title) {
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(LcxSpacing.md))

        if (items.isEmpty()) {
            Text(
                text = emptyMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(LcxSpacing.sm)) {
                items.forEach { item ->
                    QuantityRow(
                        title = item.title,
                        subtitle = item.subtitle,
                        price = item.price,
                        quantity = item.quantity,
                        enabled = enabled,
                        onDecrease = { onAdjustQuantity(item.id, -1) },
                        onIncrease = { onAdjustQuantity(item.id, 1) },
                    )
                }
            }
        }
    }
}

@Composable
private fun InventorySection(
    query: String,
    items: List<InventoryCatalogRecord>,
    quantities: Map<String, Int>,
    enabled: Boolean,
    onQueryChanged: (String) -> Unit,
    onAdjustQuantity: (String, Int) -> Unit,
) {
    LcxCard(title = "Inventario general") {
        LcxTextField(
            value = query,
            onValueChange = onQueryChanged,
            label = "Buscar por nombre, SKU o codigo de barras",
            enabled = enabled,
        )
        Spacer(modifier = Modifier.height(LcxSpacing.xs))
        Text(
            text = "Compatible con scanner fisico que escribe directamente en el campo de búsqueda.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(LcxSpacing.md))

        if (items.isEmpty()) {
            Text(
                text = if (query.isBlank()) {
                    "Empieza a buscar para ver inventario vendible."
                } else {
                    "No hay artículos que coincidan con la búsqueda."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(LcxSpacing.sm)) {
                items.forEach { item ->
                    QuantityRow(
                        title = item.itemName,
                        subtitle = listOfNotNull(
                            item.sku?.takeIf { it.isNotBlank() }?.let { "SKU: $it" },
                            item.barcode?.takeIf { it.isNotBlank() }?.let { "Barcode: $it" },
                            "Stock: ${item.quantity} ${item.unit}",
                        ).joinToString(" · "),
                        price = item.price,
                        quantity = quantities[item.id] ?: 0,
                        enabled = enabled,
                        onDecrease = { onAdjustQuantity(item.id, -1) },
                        onIncrease = { onAdjustQuantity(item.id, 1) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SummarySection(state: SalesUiState) {
    val selectedItems = state.cart.values.sum()
    LcxCard(title = "Resumen") {
        SummaryRow(label = "Items seleccionados", value = selectedItems.toString())
        Spacer(modifier = Modifier.height(LcxSpacing.xs))
        SummaryRow(label = "Metodo de pago", value = paymentMethodLabel(state.paymentMethod))
        Spacer(modifier = Modifier.height(LcxSpacing.xs))
        SummaryRow(
            label = "Total",
            value = formatCurrency(state.totalPrice),
            emphasize = true,
        )
    }
}

@Composable
private fun QuantityRow(
    title: String,
    subtitle: String?,
    price: Double,
    quantity: Int,
    enabled: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    LcxCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(LcxSpacing.xs),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                subtitle?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = formatCurrency(price),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(LcxSpacing.xs),
            ) {
                TextButton(
                    onClick = onDecrease,
                    enabled = enabled && quantity > 0,
                ) {
                    Text("-")
                }
                Text(
                    text = quantity.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = LcxSpacing.xs),
                )
                TextButton(
                    onClick = onIncrease,
                    enabled = enabled,
                ) {
                    Text("+")
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(
    label: String,
    value: String,
    emphasize: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = if (emphasize) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.bodyMedium
            },
        )
        Text(
            text = value,
            style = if (emphasize) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.bodyMedium
            },
            fontWeight = if (emphasize) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

private fun CustomerDraft.isReady(): Boolean {
    return fullName.trim().isNotBlank() && normalizePhone(phone).length >= 7
}

private fun buildVisibleInventoryItems(
    items: List<InventoryCatalogRecord>,
    selectedQuantities: Map<String, Int>,
    query: String,
): List<InventoryCatalogRecord> {
    val normalizedQuery = query.trim().lowercase()
    if (normalizedQuery.isBlank()) {
        val selected = items.filter { (selectedQuantities[it.id] ?: 0) > 0 }
        val remaining = items.filterNot { (selectedQuantities[it.id] ?: 0) > 0 }
        return selected + remaining.take(8)
    }

    return items.filter { item ->
        listOf(item.itemName, item.sku.orEmpty(), item.barcode.orEmpty())
            .any { value -> value.lowercase().contains(normalizedQuery) }
    }
}

private fun duplicatePhoneMessage(matches: List<CustomerRecord>): String {
    val preview = matches.take(3).joinToString("\n") { match ->
        "- ${match.fullName} (${match.phone})"
    }
    return "Ya existe al menos un cliente con ese telefono:\n$preview\n\n¿Quieres crearlo de todos modos?"
}

private fun paymentMethodLabel(method: PaymentMethod): String {
    return when (method) {
        PaymentMethod.CASH -> "Efectivo"
        PaymentMethod.CARD -> "Tarjeta"
        PaymentMethod.TRANSFER -> "Transferencia"
    }
}

private fun formatCurrency(amount: Double): String {
    return "$" + String.format("%.2f", amount)
}

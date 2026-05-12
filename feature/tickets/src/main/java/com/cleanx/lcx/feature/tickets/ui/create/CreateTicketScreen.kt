package com.cleanx.lcx.feature.tickets.ui.create

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.cleanx.lcx.core.ui.LcxTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cleanx.lcx.core.model.PaymentMethod
import com.cleanx.lcx.core.model.Ticket
import com.cleanx.lcx.core.theme.LcxSpacing
import com.cleanx.lcx.core.theme.LcxSuccess
import com.cleanx.lcx.core.ui.ButtonVariant
import com.cleanx.lcx.core.ui.ErrorState
import com.cleanx.lcx.core.ui.LcxButton
import com.cleanx.lcx.core.ui.LcxCard
import com.cleanx.lcx.core.ui.LcxConfirmationDialog
import com.cleanx.lcx.core.ui.LcxQuantityStepper
import com.cleanx.lcx.core.ui.LcxStepHeader
import com.cleanx.lcx.core.ui.LcxStickyActionBar
import com.cleanx.lcx.core.ui.LcxStatusPill
import com.cleanx.lcx.core.ui.LcxTextField
import com.cleanx.lcx.feature.tickets.data.AddOnCatalogRecord
import com.cleanx.lcx.feature.tickets.data.CustomerRecord
import com.cleanx.lcx.feature.tickets.data.InventoryCatalogRecord
import com.cleanx.lcx.feature.tickets.data.ServiceCatalogRecord
import com.cleanx.lcx.feature.tickets.domain.create.CreateTicketUiState
import com.cleanx.lcx.feature.tickets.domain.create.CustomerPickerUiState
import com.cleanx.lcx.feature.tickets.domain.create.EncargoPaymentChoice
import com.cleanx.lcx.feature.tickets.domain.create.hasSelectedCustomer
import com.cleanx.lcx.feature.tickets.domain.create.parsedWeightOrZero
import com.cleanx.lcx.feature.tickets.ui.inventory.InventoryBarcodeScannerButton
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

private val PickupInputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
private val PickupDisplayFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

private enum class CreateTicketStep(
    val title: String,
    val summary: String,
) {
    CUSTOMER("Cliente", "Buscar o crear"),
    SERVICE("Servicio", "Peso y entrega"),
    ADD_ONS("Extras", "Ropa, productos y notas"),
    PAYMENT("Pago", "Resumen final");

    fun next(): CreateTicketStep {
        val steps = entries
        return steps[(ordinal + 1).coerceAtMost(steps.lastIndex)]
    }

    fun previous(): CreateTicketStep {
        val steps = entries
        return steps[(ordinal - 1).coerceAtLeast(0)]
    }
}

private data class PriceLookupItem(
    val title: String,
    val subtitle: String,
    val price: Double,
)

private data class SummaryLineItem(
    val label: String,
    val detail: String,
    val value: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTicketScreen(
    viewModel: CreateTicketViewModel,
    onBack: () -> Unit,
    onTicketCreated: (Ticket) -> Unit,
    onNavigateBack: () -> Unit = onBack,
) {
    val state by viewModel.uiState.collectAsState()
    var showSuccess by rememberSaveable { mutableStateOf(false) }
    var showExitDialog by rememberSaveable { mutableStateOf(false) }
    var currentStep by rememberSaveable { mutableStateOf(CreateTicketStep.CUSTOMER) }
    var priceLookupQuery by rememberSaveable { mutableStateOf("") }

    val visibleInventoryItems = remember(
        state.inventoryItems,
        state.inventoryQuantities,
        state.inventorySearchQuery,
    ) {
        buildVisibleInventoryItems(
            items = state.inventoryItems,
            selectedQuantities = state.inventoryQuantities,
            query = state.inventorySearchQuery,
        )
    }

    val handleBack = {
        if (showSuccess) {
            onNavigateBack()
        } else if (state.hasStartedForm && !state.isSubmitting && !state.customer.isSaving) {
            showExitDialog = true
        } else if (!state.isSubmitting) {
            onBack()
        }
    }

    BackHandler(enabled = true) {
        handleBack()
    }

    LaunchedEffect(state.createdTicket) {
        val ticket = state.createdTicket ?: return@LaunchedEffect
        showSuccess = true
        delay(1200)
        viewModel.clearCreated()
        onTicketCreated(ticket)
    }

    if (showExitDialog) {
        LcxConfirmationDialog(
            title = "Datos sin guardar",
            message = "Tienes cambios sin guardar. ¿Deseas salir del encargo?",
            confirmLabel = "Salir",
            cancelLabel = "Seguir editando",
            onConfirm = {
                showExitDialog = false
                onBack()
            },
            onDismiss = { showExitDialog = false },
            isDanger = true,
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
            LcxTopAppBar(
                title = {
                    Text(
                        text = "Nuevo encargo",
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = handleBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                        )
                    }
                },
            )
        },
        bottomBar = {
            AnimatedVisibility(visible = !showSuccess) {
                LcxStickyActionBar {
                    if (currentStep != CreateTicketStep.CUSTOMER) {
                        LcxButton(
                            text = "Atras",
                            onClick = { currentStep = currentStep.previous() },
                            enabled = !state.isSubmitting,
                            variant = ButtonVariant.Secondary,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(LcxSpacing.xs)) {
                        Text(
                            text = "Total",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = formatCurrency(state.pricing.total),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    LcxButton(
                        text = if (currentStep == CreateTicketStep.PAYMENT) {
                            "Confirmar encargo"
                        } else {
                            "Siguiente"
                        },
                        onClick = {
                            if (currentStep == CreateTicketStep.PAYMENT) {
                                val blockingStep = firstBlockingStep(state)
                                if (blockingStep != null) {
                                    currentStep = blockingStep
                                } else {
                                    viewModel.submit()
                                }
                            } else {
                                currentStep = currentStep.next()
                            }
                        },
                        isLoading = state.isSubmitting,
                        enabled = !state.isLoadingCatalogs && !state.customer.isSaving,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = !showSuccess,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                when {
                    state.isLoadingCatalogs && state.services.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    state.catalogError != null && state.services.isEmpty() -> {
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

                            item {
                                CatalogStatusSection(state = state)
                            }

                            item {
                                PriceLookupSection(
                                    state = state,
                                    query = priceLookupQuery,
                                    onQueryChanged = { priceLookupQuery = it },
                                )
                            }

                            item {
                                CreateTicketStepTabs(
                                    selectedStep = currentStep,
                                    onStepSelected = { currentStep = it },
                                )
                            }

                            when (currentStep) {
                                CreateTicketStep.CUSTOMER -> {
                                    item {
                                        LcxStepHeader(
                                            step = 1,
                                            title = "Cliente",
                                            summary = "Datos del encargo",
                                        )
                                    }

                                    item {
                                        CustomerSection(
                                            state = state,
                                            onSearchQueryChanged = viewModel::onCustomerSearchQueryChanged,
                                            onOpenCreateForm = viewModel::openCreateCustomerForm,
                                            onCancelCreateForm = viewModel::cancelCreateCustomerForm,
                                            onClearSelectedCustomer = viewModel::clearSelectedCustomer,
                                            onCustomerSelected = viewModel::selectCustomer,
                                            onCustomerFullNameChanged = viewModel::onCustomerFullNameChanged,
                                            onCustomerPhoneChanged = viewModel::onCustomerPhoneChanged,
                                            onCustomerEmailChanged = viewModel::onCustomerEmailChanged,
                                            onCustomerNotesChanged = viewModel::onCustomerNotesChanged,
                                            onCreateCustomer = viewModel::createCustomer,
                                        )
                                    }
                                }

                                CreateTicketStep.SERVICE -> {
                                    item {
                                        LcxStepHeader(
                                            step = 2,
                                            title = "Servicio",
                                            summary = "Peso y entrega",
                                        )
                                    }

                                    item {
                                        ServiceSection(
                                            state = state,
                                            onBaseServiceSelected = viewModel::onBaseServiceSelected,
                                            onWeightChanged = viewModel::onWeightChanged,
                                            onPickupEstimateChanged = viewModel::onPickupEstimateChanged,
                                        )
                                    }
                                }

                                CreateTicketStep.ADD_ONS -> {
                                    item {
                                        LcxStepHeader(
                                            step = 3,
                                            title = "Extras",
                                            summary = "Ropa, productos y manejo especial",
                                        )
                                    }

                                    item {
                                        BeddingSection(
                                            items = state.beddingItems,
                                            quantities = state.beddingQuantities,
                                            enabled = !state.isSubmitting,
                                            onAdjustQuantity = viewModel::adjustBeddingQuantity,
                                        )
                                    }

                                    item {
                                        ExtrasSection(
                                            items = state.extraItems,
                                            selectedIds = state.selectedExtraIds,
                                            enabled = !state.isSubmitting,
                                            onToggleExtra = viewModel::toggleExtra,
                                        )
                                    }

                                    item {
                                        InventorySection(
                                            query = state.inventorySearchQuery,
                                            items = visibleInventoryItems,
                                            quantities = state.inventoryQuantities,
                                            enabled = !state.isSubmitting,
                                            onQueryChanged = viewModel::onInventorySearchQueryChanged,
                                            onSearchSubmitted = viewModel::submitInventorySearch,
                                            onBarcodeScanned = viewModel::scanInventoryBarcode,
                                            onAdjustQuantity = viewModel::adjustInventoryQuantity,
                                        )
                                    }

                                    item {
                                        SpecialHandlingSection(
                                            state = state,
                                            onSharedMachinePoolChanged = viewModel::setSharedMachinePool,
                                            onToggleSpecialItem = viewModel::toggleSpecialItem,
                                            onSpecialNotesChanged = viewModel::onSpecialItemNotesChanged,
                                        )
                                    }
                                }

                                CreateTicketStep.PAYMENT -> {
                                    item {
                                        LcxStepHeader(
                                            step = 4,
                                            title = "Pago y resumen",
                                            summary = "Total y estado de pago",
                                        )
                                    }

                                    item {
                                        PaymentSection(
                                            state = state,
                                            onChoiceChanged = viewModel::onPaymentChoiceChanged,
                                            onMethodChanged = viewModel::onPaymentMethodChanged,
                                        )
                                    }

                                    item {
                                        SummarySection(state = state)
                                    }
                                }
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

                            item { Spacer(modifier = Modifier.height(112.dp)) }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = showSuccess,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center),
            ) {
                LcxCard {
                    Text(
                        text = "Encargo creado",
                        style = MaterialTheme.typography.titleLarge,
                        color = LcxSuccess,
                    )
                    Spacer(modifier = Modifier.height(LcxSpacing.xs))
                    Text(
                        text = "Abriendo detalle con quick actions.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun CatalogStatusSection(state: CreateTicketUiState) {
    LcxCard(title = "Catalogo vivo") {
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
                    text = "Precios desde DB real",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = buildCatalogStatusText(state),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LcxStatusPill(
                label = "Live",
                tint = LcxSuccess,
            )
        }
    }
}

@Composable
private fun PriceLookupSection(
    state: CreateTicketUiState,
    query: String,
    onQueryChanged: (String) -> Unit,
) {
    val results = remember(state.services, state.extraItems, state.inventoryItems, query) {
        buildPriceLookupItems(state, query)
    }

    LcxCard(title = "Consulta rapida de precios") {
        LcxTextField(
            value = query,
            onValueChange = onQueryChanged,
            label = "Buscar servicio, extra o producto",
            enabled = !state.isSubmitting,
        )
        Spacer(modifier = Modifier.height(LcxSpacing.sm))

        if (results.isEmpty()) {
            Text(
                text = if (query.isBlank()) {
                    "Busca por nombre, SKU o codigo para consultar precios sin modificar el ticket."
                } else {
                    "No hay precios que coincidan con la busqueda."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(LcxSpacing.xs)) {
                results.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = item.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = formatCurrency(item.price),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateTicketStepTabs(
    selectedStep: CreateTicketStep,
    onStepSelected: (CreateTicketStep) -> Unit,
) {
    LcxCard {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(LcxSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(LcxSpacing.xs),
        ) {
            CreateTicketStep.entries.forEachIndexed { index, step ->
                FilterChip(
                    selected = selectedStep == step,
                    onClick = { onStepSelected(step) },
                    label = {
                        Text("${index + 1}. ${step.title}")
                    },
                )
            }
        }
        Spacer(modifier = Modifier.height(LcxSpacing.xs))
        Text(
            text = selectedStep.summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CustomerSection(
    state: CreateTicketUiState,
    onSearchQueryChanged: (String) -> Unit,
    onOpenCreateForm: () -> Unit,
    onCancelCreateForm: () -> Unit,
    onClearSelectedCustomer: () -> Unit,
    onCustomerSelected: (CustomerRecord) -> Unit,
    onCustomerFullNameChanged: (String) -> Unit,
    onCustomerPhoneChanged: (String) -> Unit,
    onCustomerEmailChanged: (String) -> Unit,
    onCustomerNotesChanged: (String) -> Unit,
    onCreateCustomer: () -> Unit,
) {
    val customerState = state.customer
    val selectedCustomer = customerState.selectedCustomer
    val hasSelectedCustomer = selectedCustomer.customerId != null &&
        selectedCustomer.fullName.isNotBlank() &&
        selectedCustomer.phone.isNotBlank()

    LcxCard(title = "Cliente") {
        Text(
            text = "Busca por nombre, telefono o correo. Si no existe, crealo aqui.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (hasSelectedCustomer && !customerState.showCreateForm) {
            Spacer(modifier = Modifier.height(LcxSpacing.md))
            SelectedCustomerSummary(
                customer = selectedCustomer,
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
                LcxButton(
                    text = "Crear cliente nuevo",
                    onClick = onOpenCreateForm,
                    enabled = !state.isSubmitting,
                    variant = ButtonVariant.Secondary,
                )
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
    customer: com.cleanx.lcx.feature.tickets.data.CustomerDraft,
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
private fun ServiceSection(
    state: CreateTicketUiState,
    onBaseServiceSelected: (String) -> Unit,
    onWeightChanged: (String) -> Unit,
    onPickupEstimateChanged: (String) -> Unit,
) {
    val baseServices = remember(state.services) {
        state.services.filter { com.cleanx.lcx.feature.tickets.data.isBaseWashByKilo(it) }
            .ifEmpty { state.services }
    }

    LcxCard(title = "Servicio base") {
        Text(
            text = "El pricing replica la regla web: minimo de 3 kg y recalculo en vivo.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(LcxSpacing.sm))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(LcxSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(LcxSpacing.xs),
        ) {
            baseServices.forEach { service ->
                FilterChip(
                    selected = state.selectedBaseServiceId == service.id,
                    onClick = { onBaseServiceSelected(service.id) },
                    enabled = !state.isSubmitting,
                    label = {
                        Text("${service.name} · ${formatCurrency(service.price)}")
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(LcxSpacing.md))

        LcxTextField(
            value = state.weight,
            onValueChange = onWeightChanged,
            label = "Peso (kg) *",
            keyboardType = KeyboardType.Decimal,
            enabled = !state.isSubmitting,
        )
        Spacer(modifier = Modifier.height(LcxSpacing.xs))
        Text(
            text = "Si capturas menos de 3 kg, el subtotal se cobra con minimo de 3 kg.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(LcxSpacing.md))

        PickupDateTimePicker(
            value = state.pickupEstimate,
            enabled = !state.isSubmitting,
            onValueChanged = onPickupEstimateChanged,
        )
        Spacer(modifier = Modifier.height(LcxSpacing.xs))
        Text(
            text = "Sugerencia actual: ${state.pickupTargetHours}h desde ahora, igual que en la PWA.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PickupDateTimePicker(
    value: String,
    enabled: Boolean,
    onValueChanged: (String) -> Unit,
) {
    val context = LocalContext.current
    val selectedDateTime = remember(value) {
        parsePickupInput(value) ?: LocalDateTime.now()
    }

    LcxCard(title = "Fecha promesa") {
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
                    text = selectedDateTime.format(PickupDisplayFormatter),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "La seleccion se aplica inmediatamente al total y payload final.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(
                onClick = {
                    showPickupPicker(
                        context = context,
                        initialValue = selectedDateTime,
                        onValueChanged = onValueChanged,
                    )
                },
                enabled = enabled,
            ) {
                Text("Cambiar")
            }
        }
    }
}

private fun showPickupPicker(
    context: android.content.Context,
    initialValue: LocalDateTime,
    onValueChanged: (String) -> Unit,
) {
    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val selectedDate = LocalDateTime.of(
                year,
                month + 1,
                dayOfMonth,
                initialValue.hour,
                initialValue.minute,
            )

            TimePickerDialog(
                context,
                { _, hourOfDay, minute ->
                    val selectedDateTime = selectedDate
                        .withHour(hourOfDay)
                        .withMinute(minute)
                        .withSecond(0)
                        .withNano(0)
                    onValueChanged(selectedDateTime.format(PickupInputFormatter))
                },
                initialValue.hour,
                initialValue.minute,
                true,
            ).show()
        },
        initialValue.year,
        initialValue.monthValue - 1,
        initialValue.dayOfMonth,
    ).show()
}

@Composable
private fun BeddingSection(
    items: List<ServiceCatalogRecord>,
    quantities: Map<String, Int>,
    enabled: Boolean,
    onAdjustQuantity: (String, Int) -> Unit,
) {
    LcxCard(title = "Ropa de cama") {
        if (items.isEmpty()) {
            Text(
                text = "No hay complementos de ropa de cama activos en el catalogo.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(LcxSpacing.sm)) {
                items.forEach { item ->
                    QuantityRow(
                        title = item.name,
                        subtitle = item.description ?: item.category,
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
private fun ExtrasSection(
    items: List<AddOnCatalogRecord>,
    selectedIds: List<String>,
    enabled: Boolean,
    onToggleExtra: (String) -> Unit,
) {
    LcxCard(title = "Extras de lavado") {
        if (items.isEmpty()) {
            Text(
                text = "No hay extras activos para este encargo.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(LcxSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(LcxSpacing.xs),
            ) {
                items.forEach { item ->
                    FilterChip(
                        selected = selectedIds.contains(item.id),
                        onClick = { onToggleExtra(item.id) },
                        enabled = enabled,
                        label = {
                            Text("${item.name} · ${formatCurrency(item.price)}")
                        },
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
    onSearchSubmitted: () -> Unit,
    onBarcodeScanned: (String) -> Unit,
    onAdjustQuantity: (String, Int) -> Unit,
) {
    LcxCard(title = "Productos de inventario") {
        LcxTextField(
            value = query,
            onValueChange = onQueryChanged,
            label = "Buscar por nombre, SKU o codigo de barras",
            enabled = enabled,
            imeAction = ImeAction.Search,
            keyboardActions = KeyboardActions(
                onSearch = { onSearchSubmitted() },
                onDone = { onSearchSubmitted() },
            ),
            modifier = Modifier.onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp && event.key == Key.Enter) {
                    onSearchSubmitted()
                    true
                } else {
                    false
                }
            },
        )
        Spacer(modifier = Modifier.height(LcxSpacing.xs))
        Text(
            text = "Escanea con camara o usa un scanner fisico y presiona Enter para agregar una coincidencia exacta.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(LcxSpacing.sm))
        InventoryBarcodeScannerButton(
            enabled = enabled,
            onBarcodeScanned = onBarcodeScanned,
        )

        Spacer(modifier = Modifier.height(LcxSpacing.md))
        if (items.isEmpty()) {
            Text(
                text = if (query.isBlank()) {
                    "Empieza a buscar o usa un scanner para ver productos vendibles."
                } else {
                    "No hay productos vendibles que coincidan con la busqueda."
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
                        canIncrease = (quantities[item.id] ?: 0) < item.quantity,
                        onDecrease = { onAdjustQuantity(item.id, -1) },
                        onIncrease = { onAdjustQuantity(item.id, 1) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SpecialHandlingSection(
    state: CreateTicketUiState,
    onSharedMachinePoolChanged: (Boolean) -> Unit,
    onToggleSpecialItem: (String) -> Unit,
    onSpecialNotesChanged: (String) -> Unit,
) {
    LcxCard(title = "Manejo especial") {
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
                    text = "Pool de maquinas compartidas",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "Se persistira en `notes` como `shared_machine_pool=yes/no`.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = state.useSharedMachinePool,
                onCheckedChange = onSharedMachinePoolChanged,
                enabled = !state.isSubmitting,
            )
        }

        Spacer(modifier = Modifier.height(LcxSpacing.md))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(LcxSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(LcxSpacing.xs),
        ) {
            state.specialItemOptions.forEach { item ->
                FilterChip(
                    selected = state.selectedSpecialItemIds.contains(item.id),
                    onClick = { onToggleSpecialItem(item.id) },
                    enabled = !state.isSubmitting,
                    label = { Text(item.label) },
                )
            }
        }

        Spacer(modifier = Modifier.height(LcxSpacing.md))
        LcxTextField(
            value = state.specialItemNotes,
            onValueChange = onSpecialNotesChanged,
            label = "Notas especiales",
            singleLine = false,
            maxLines = 3,
            enabled = !state.isSubmitting,
        )
    }
}

@Composable
private fun PaymentSection(
    state: CreateTicketUiState,
    onChoiceChanged: (EncargoPaymentChoice) -> Unit,
    onMethodChanged: (PaymentMethod) -> Unit,
) {
    LcxCard(title = "Pago") {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(LcxSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(LcxSpacing.xs),
        ) {
            FilterChip(
                selected = state.paymentChoice == EncargoPaymentChoice.PENDING,
                onClick = { onChoiceChanged(EncargoPaymentChoice.PENDING) },
                enabled = !state.isSubmitting,
                label = { Text("Pendiente") },
            )
            FilterChip(
                selected = state.paymentChoice == EncargoPaymentChoice.PAID,
                onClick = { onChoiceChanged(EncargoPaymentChoice.PAID) },
                enabled = !state.isSubmitting,
                label = { Text("Pagado") },
            )
        }

        if (state.paymentChoice == EncargoPaymentChoice.PAID) {
            Spacer(modifier = Modifier.height(LcxSpacing.md))
            Text(
                text = "Metodo de pago",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(LcxSpacing.sm))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(LcxSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(LcxSpacing.xs),
            ) {
                PaymentMethod.entries.forEach { method ->
                    FilterChip(
                        selected = state.paymentMethod == method,
                        onClick = { onMethodChanged(method) },
                        enabled = !state.isSubmitting,
                        label = { Text(paymentMethodLabel(method)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SummarySection(state: CreateTicketUiState) {
    val lineItems = remember(
        state.selectedBaseServiceId,
        state.weight,
        state.services,
        state.beddingQuantities,
        state.extraItems,
        state.selectedExtraIds,
        state.inventoryItems,
        state.inventoryQuantities,
    ) {
        buildSummaryLineItems(state)
    }

    LcxCard(title = "Resumen") {
        if (lineItems.isEmpty()) {
            Text(
                text = "El resumen se actualiza al seleccionar servicio, extras o productos.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(LcxSpacing.sm))
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(LcxSpacing.xs)) {
                lineItems.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = item.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = item.value,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(LcxSpacing.md))
        }
        SummaryRow(label = "Subtotal", value = formatCurrency(state.pricing.subtotal))
        Spacer(modifier = Modifier.height(LcxSpacing.xs))
        SummaryRow(label = "Add-ons", value = formatCurrency(state.pricing.addOnsTotal))
        Spacer(modifier = Modifier.height(LcxSpacing.xs))
        SummaryRow(
            label = "Total",
            value = formatCurrency(state.pricing.total),
            emphasize = true,
        )
    }
}

@Composable
private fun SubmitSection(
    state: CreateTicketUiState,
    onSubmit: () -> Unit,
) {
    LcxCard {
        Text(
            text = "El payload final usa catalogos reales, pricing web-compatible y metadata de pickup/shared pool.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        state.error?.let { error ->
            Spacer(modifier = Modifier.height(LcxSpacing.sm))
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(modifier = Modifier.height(LcxSpacing.md))
        LcxButton(
            text = "Confirmar encargo",
            onClick = onSubmit,
            isLoading = state.isSubmitting,
            enabled = !state.isLoadingCatalogs && !state.customer.isSaving,
            modifier = Modifier.fillMaxWidth(),
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
    canIncrease: Boolean = true,
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

            LcxQuantityStepper(
                quantity = quantity,
                enabled = enabled,
                canIncrease = canIncrease,
                onDecrease = onDecrease,
                onIncrease = onIncrease,
            )
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

private fun buildCatalogStatusText(state: CreateTicketUiState): String {
    val loadedAt = state.catalogLoadedAtLabel ?: "recien cargado"
    val branchLabel = state.inventoryItems
        .mapNotNull { it.branch?.takeIf { branch -> branch.isNotBlank() } }
        .distinct()
        .takeIf { it.isNotEmpty() }
        ?.joinToString(", ")
        ?: "sucursal actual"

    return "Actualizado $loadedAt · $branchLabel · ${state.services.size} servicios, " +
        "${state.extraItems.size} extras, ${state.inventoryItems.size} productos vendibles"
}

private fun buildPriceLookupItems(
    state: CreateTicketUiState,
    query: String,
): List<PriceLookupItem> {
    val normalizedQuery = query.trim().lowercase()
    if (normalizedQuery.isBlank()) return emptyList()

    val serviceItems = state.services.map { service ->
        PriceLookupItem(
            title = service.name,
            subtitle = listOf("Servicio", service.category, service.unit)
                .filter { it.isNotBlank() }
                .joinToString(" · "),
            price = service.price,
        )
    }
    val extraItems = state.extraItems.map { item ->
        PriceLookupItem(
            title = item.name,
            subtitle = listOf("Extra", item.description.orEmpty())
                .filter { it.isNotBlank() }
                .joinToString(" · "),
            price = item.price,
        )
    }
    val inventoryItems = state.inventoryItems.map { item ->
        PriceLookupItem(
            title = item.itemName,
            subtitle = listOfNotNull(
                "Inventario",
                item.sku?.takeIf { it.isNotBlank() }?.let { "SKU: $it" },
                item.barcode?.takeIf { it.isNotBlank() }?.let { "Barcode: $it" },
                "Stock: ${item.quantity} ${item.unit}",
            ).joinToString(" · "),
            price = item.price,
        )
    }

    return (serviceItems + extraItems + inventoryItems)
        .filter { item ->
            listOf(item.title, item.subtitle)
                .any { it.lowercase().contains(normalizedQuery) }
        }
        .take(8)
}

private fun buildSummaryLineItems(state: CreateTicketUiState): List<SummaryLineItem> {
    val items = mutableListOf<SummaryLineItem>()
    val baseService = state.services.firstOrNull { it.id == state.selectedBaseServiceId }
    if (baseService != null) {
        val chargedWeight = if (state.parsedWeightOrZero() > 0.0) {
            state.parsedWeightOrZero().coerceAtLeast(3.0)
        } else {
            0.0
        }
        items += SummaryLineItem(
            label = baseService.name,
            detail = String.format(Locale.US, "%.2f kg x %s", chargedWeight, formatCurrency(baseService.price)),
            value = formatCurrency(baseService.price * chargedWeight),
        )
    }

    state.beddingQuantities
        .filterValues { it > 0 }
        .forEach { (id, quantity) ->
            val item = state.beddingItems.firstOrNull { it.id == id } ?: return@forEach
            items += SummaryLineItem(
                label = item.name,
                detail = "$quantity x ${formatCurrency(item.price)}",
                value = formatCurrency(item.price * quantity),
            )
        }

    state.selectedExtraIds.forEach { id ->
        val item = state.extraItems.firstOrNull { it.id == id } ?: return@forEach
        val isPercentage = isPercentageSurchargeLabel(item.name)
        items += SummaryLineItem(
            label = item.name,
            detail = if (isPercentage) "Recargo 15%" else "1 x ${formatCurrency(item.price)}",
            value = if (isPercentage) "15%" else formatCurrency(item.price),
        )
    }

    state.inventoryQuantities
        .filterValues { it > 0 }
        .forEach { (id, quantity) ->
            val item = state.inventoryItems.firstOrNull { it.id == id } ?: return@forEach
            items += SummaryLineItem(
                label = item.itemName,
                detail = "$quantity x ${formatCurrency(item.price)}",
                value = formatCurrency(item.price * quantity),
            )
        }

    return items
}

private fun isPercentageSurchargeLabel(name: String): Boolean {
    val normalizedName = name.lowercase()
    return normalizedName.contains("fragancia premium") ||
        normalizedName.contains("hipoalergénico") ||
        normalizedName.contains("hipoalergenico") ||
        normalizedName.contains("quitamanchas") ||
        normalizedName.contains("tratamiento de manchas")
}

private fun parsePickupInput(value: String): LocalDateTime? {
    return try {
        LocalDateTime.parse(value, PickupInputFormatter)
    } catch (_: DateTimeParseException) {
        null
    }
}

private fun firstBlockingStep(state: CreateTicketUiState): CreateTicketStep? {
    return when {
        !state.hasSelectedCustomer() -> CreateTicketStep.CUSTOMER
        state.selectedBaseServiceId == null ||
            state.parsedWeightOrZero() <= 0.0 ||
            parsePickupInput(state.pickupEstimate) == null -> CreateTicketStep.SERVICE
        else -> null
    }
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
        return (selected + remaining.take(8))
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

package com.cleanx.lcx.feature.tickets.ui.create

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cleanx.lcx.core.model.Ticket
import com.cleanx.lcx.core.theme.LcxSpacing
import com.cleanx.lcx.core.theme.LcxSuccess
import com.cleanx.lcx.core.ui.LcxButton
import com.cleanx.lcx.core.ui.LcxCard
import com.cleanx.lcx.core.ui.LcxConfirmationDialog
import com.cleanx.lcx.core.ui.LcxTextField
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTicketScreen(
    viewModel: CreateTicketViewModel,
    onBack: () -> Unit,
    onTicketCreated: (Ticket) -> Unit,
    onNavigateBack: () -> Unit = onBack,
) {
    val state by viewModel.uiState.collectAsState()
    val customerNameFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // Track whether we're showing the success state
    var showSuccess by remember { mutableStateOf(false) }

    // Track whether to show unsaved data confirmation dialog
    var showExitDialog by remember { mutableStateOf(false) }

    // Check if form has data entered
    val hasFormData = state.customerName.isNotBlank() ||
        state.customerPhone.isNotBlank() ||
        state.service.isNotBlank() ||
        state.weight.isNotBlank() ||
        state.notes.isNotBlank() ||
        state.totalAmount.isNotBlank()

    // Handle back navigation with confirmation
    val handleBack = {
        if (showSuccess) {
            // During success display, just navigate away
            onNavigateBack()
        } else if (hasFormData && !state.isSubmitting) {
            showExitDialog = true
        } else if (!state.isSubmitting) {
            onBack()
        }
    }

    BackHandler(enabled = true) {
        handleBack()
    }

    // On ticket created: show success, then auto-navigate after delay
    LaunchedEffect(state.createdTicket) {
        val ticket = state.createdTicket
        if (ticket != null) {
            onTicketCreated(ticket)
            showSuccess = true
            viewModel.clearCreated()
            delay(2000)
            onNavigateBack()
        }
    }

    // Auto-focus on first field
    LaunchedEffect(Unit) {
        customerNameFocusRequester.requestFocus()
    }

    // Exit confirmation dialog
    if (showExitDialog) {
        LcxConfirmationDialog(
            title = "Datos sin guardar",
            message = "Tienes datos sin guardar. ¿Deseas salir?",
            confirmLabel = "Salir",
            cancelLabel = "Continuar editando",
            onConfirm = {
                showExitDialog = false
                onBack()
            },
            onDismiss = { showExitDialog = false },
            isDanger = true,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Nuevo Ticket",
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { handleBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Main form content
            AnimatedVisibility(
                visible = !showSuccess,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = LcxSpacing.md)
                        .imePadding()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(LcxSpacing.sm),
                ) {
                    Spacer(modifier = Modifier.height(LcxSpacing.xs))

                    LcxCard(title = "Datos del cliente") {
                        LcxTextField(
                            value = state.customerName,
                            onValueChange = viewModel::onCustomerNameChanged,
                            label = "Nombre del cliente *",
                            modifier = Modifier.focusRequester(customerNameFocusRequester),
                            enabled = !state.isSubmitting,
                            imeAction = ImeAction.Next,
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) },
                            ),
                        )

                        Spacer(modifier = Modifier.height(LcxSpacing.sm))

                        LcxTextField(
                            value = state.customerPhone,
                            onValueChange = viewModel::onCustomerPhoneChanged,
                            label = "Telefono *",
                            keyboardType = KeyboardType.Phone,
                            enabled = !state.isSubmitting,
                            imeAction = ImeAction.Next,
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) },
                            ),
                        )
                    }

                    LcxCard(title = "Servicio") {
                        ServiceTypeDropdown(
                            selected = state.serviceType,
                            onSelected = viewModel::onServiceTypeChanged,
                            enabled = !state.isSubmitting,
                        )

                        Spacer(modifier = Modifier.height(LcxSpacing.sm))

                        LcxTextField(
                            value = state.service,
                            onValueChange = viewModel::onServiceChanged,
                            label = "Servicio *",
                            enabled = !state.isSubmitting,
                            imeAction = ImeAction.Next,
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) },
                            ),
                        )

                        Spacer(modifier = Modifier.height(LcxSpacing.sm))

                        LcxTextField(
                            value = state.weight,
                            onValueChange = viewModel::onWeightChanged,
                            label = "Peso (kg)",
                            keyboardType = KeyboardType.Decimal,
                            enabled = !state.isSubmitting,
                            imeAction = ImeAction.Next,
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) },
                            ),
                        )

                        Spacer(modifier = Modifier.height(LcxSpacing.sm))

                        LcxTextField(
                            value = state.notes,
                            onValueChange = viewModel::onNotesChanged,
                            label = "Notas",
                            singleLine = false,
                            maxLines = 3,
                            enabled = !state.isSubmitting,
                            imeAction = ImeAction.Next,
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) },
                            ),
                        )
                    }

                    LcxCard(title = "Pago") {
                        LcxTextField(
                            value = state.totalAmount,
                            onValueChange = viewModel::onTotalAmountChanged,
                            label = "Monto total",
                            keyboardType = KeyboardType.Decimal,
                            prefix = { Text("$") },
                            enabled = !state.isSubmitting,
                            error = state.error,
                            imeAction = ImeAction.Done,
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    keyboardController?.hide()
                                    viewModel.submit()
                                },
                            ),
                        )
                    }

                    LcxButton(
                        text = if (state.isSubmitting) "Creando..." else "Crear Ticket",
                        onClick = {
                            keyboardController?.hide()
                            viewModel.submit()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isSubmitting,
                        isLoading = state.isSubmitting,
                    )

                    Spacer(modifier = Modifier.height(LcxSpacing.md))
                }
            }

            // Success overlay
            AnimatedVisibility(
                visible = showSuccess,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "\u2713",
                        style = MaterialTheme.typography.displayLarge,
                        color = LcxSuccess,
                    )
                    Spacer(modifier = Modifier.height(LcxSpacing.md))
                    Text(
                        text = "Ticket creado exitosamente",
                        style = MaterialTheme.typography.headlineSmall,
                        color = LcxSuccess,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServiceTypeDropdown(
    selected: String,
    onSelected: (String) -> Unit,
    enabled: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf("wash-fold" to "Lavado y Doblado", "in-store" to "En tienda")
    val selectedLabel = options.firstOrNull { it.first == selected }?.second ?: selected

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded },
    ) {
        LcxTextField(
            value = selectedLabel,
            onValueChange = {},
            label = "Tipo de servicio",
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            enabled = enabled,
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSelected(value)
                        expanded = false
                    },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                )
            }
        }
    }
}

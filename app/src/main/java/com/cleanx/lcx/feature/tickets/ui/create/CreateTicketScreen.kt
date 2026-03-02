package com.cleanx.lcx.feature.tickets.ui.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cleanx.lcx.core.model.Ticket

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTicketScreen(
    viewModel: CreateTicketViewModel,
    onBack: () -> Unit,
    onTicketCreated: (Ticket) -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.createdTicket) {
        val ticket = state.createdTicket
        if (ticket != null) {
            onTicketCreated(ticket)
            viewModel.clearCreated()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nuevo Ticket") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            OutlinedTextField(
                value = state.customerName,
                onValueChange = viewModel::onCustomerNameChanged,
                label = { Text("Nombre del cliente *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.isSubmitting,
            )

            OutlinedTextField(
                value = state.customerPhone,
                onValueChange = viewModel::onCustomerPhoneChanged,
                label = { Text("Telefono *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                enabled = !state.isSubmitting,
            )

            ServiceTypeDropdown(
                selected = state.serviceType,
                onSelected = viewModel::onServiceTypeChanged,
                enabled = !state.isSubmitting,
            )

            OutlinedTextField(
                value = state.service,
                onValueChange = viewModel::onServiceChanged,
                label = { Text("Servicio *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.isSubmitting,
            )

            OutlinedTextField(
                value = state.weight,
                onValueChange = viewModel::onWeightChanged,
                label = { Text("Peso (kg)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                enabled = !state.isSubmitting,
            )

            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::onNotesChanged,
                label = { Text("Notas") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
                enabled = !state.isSubmitting,
            )

            OutlinedTextField(
                value = state.totalAmount,
                onValueChange = viewModel::onTotalAmountChanged,
                label = { Text("Monto total") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                prefix = { Text("$") },
                enabled = !state.isSubmitting,
            )

            state.error?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }

            Button(
                onClick = viewModel::submit,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isSubmitting,
            ) {
                if (state.isSubmitting) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Text("Creando...")
                    }
                } else {
                    Text("Crear Ticket")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
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
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("Tipo de servicio") },
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
                )
            }
        }
    }
}

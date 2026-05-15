package com.cleanx.lcx.feature.auth.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cleanx.lcx.core.theme.LcxSpacing
import com.cleanx.lcx.core.ui.LcxButton
import com.cleanx.lcx.core.ui.LcxTestTags
import com.cleanx.lcx.core.ui.LcxTextField
import com.cleanx.lcx.feature.auth.data.DeviceOperatorResponse

@Composable
fun LoginScreen(
    onAuthenticated: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value

    LaunchedEffect(state.phase) {
        if (state.phase == LoginPhase.Success) {
            onAuthenticated()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(LcxTestTags.LOGIN_ROOT)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.84f),
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.56f),
                        MaterialTheme.colorScheme.background,
                    ),
                ),
            )
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .padding(LcxSpacing.xl),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(LcxSpacing.cardPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Clean X",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(LcxSpacing.xs))
                Text(
                    text = "Acceso de dispositivo",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(LcxSpacing.sm))
                StatusStrip(state = state, onChangeBranch = viewModel::changeBranch)
                Spacer(Modifier.height(LcxSpacing.lg))

                when (state.phase) {
                    LoginPhase.Loading,
                    LoginPhase.Submitting -> LoadingPanel(state.phase)

                    LoginPhase.BranchSelection -> BranchSelection(
                        state = state,
                        onSelectBranch = viewModel::selectBranch,
                        onRetry = viewModel::retry,
                    )

                    LoginPhase.OperatorSelection -> OperatorSelection(
                        state = state,
                        onSelectOperator = viewModel::selectOperator,
                        onGuest = viewModel::showGuestEntry,
                    )

                    LoginPhase.PinEntry -> PinEntry(
                        state = state,
                        onPinChanged = viewModel::onPinChanged,
                        onSubmit = viewModel::submitPin,
                        onBack = viewModel::backToOperators,
                    )

                    LoginPhase.GuestEntry -> GuestEntry(
                        state = state,
                        onCodeChanged = viewModel::onGuestCodeChanged,
                        onSubmit = viewModel::submitGuestCode,
                        onBack = viewModel::backToOperators,
                    )

                    LoginPhase.Success -> LoadingPanel(LoginPhase.Success)
                }
            }
        }
    }
}

@Composable
private fun StatusStrip(
    state: LoginUiState,
    onChangeBranch: () -> Unit,
) {
    val branch = state.selectedBranch
    val operator = state.selectedOperator?.fullName
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = LcxSpacing.md, vertical = LcxSpacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = branch ?: "Selecciona sucursal",
                    style = MaterialTheme.typography.labelLarge,
                )
                if (operator != null) {
                    Text(
                        text = operator,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (branch != null) {
                TextButton(onClick = onChangeBranch) {
                    Text("Cambiar")
                }
            }
        }
    }
}

@Composable
private fun LoadingPanel(phase: LoginPhase) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(LcxSpacing.md),
    ) {
        CircularProgressIndicator()
        Text(
            text = if (phase == LoginPhase.Submitting) "Validando acceso..." else "Cargando configuracion...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun BranchSelection(
    state: LoginUiState,
    onSelectBranch: (String) -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(LcxSpacing.sm),
    ) {
        Text(
            text = "Escoge sucursal",
            style = MaterialTheme.typography.titleMedium,
        )
        state.branches.forEach { branch ->
            SelectionRow(
                title = branch,
                subtitle = "Usar este Pixel en esta sucursal",
                enabled = true,
                modifier = Modifier.testTag(LcxTestTags.loginBranch(branch)),
                onClick = { onSelectBranch(branch) },
            )
        }
        if (state.branches.isEmpty()) {
            Text(
                text = state.error ?: "No hay sucursales disponibles.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                Text("Reintentar")
            }
        }
        ErrorText(state.error)
    }
}

@Composable
private fun OperatorSelection(
    state: LoginUiState,
    onSelectOperator: (DeviceOperatorResponse) -> Unit,
    onGuest: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(LcxSpacing.sm),
    ) {
        Text(
            text = "Escoge usuario",
            style = MaterialTheme.typography.titleMedium,
        )
        state.operators.forEach { operator ->
            SelectionRow(
                title = operator.fullName,
                subtitle = if (operator.hasPin) "Entrar con PIN" else "PIN pendiente",
                enabled = operator.hasPin,
                modifier = Modifier.testTag(LcxTestTags.loginOperator(operator.fullName)),
                onClick = { onSelectOperator(operator) },
            )
        }
        OutlinedButton(
            onClick = onGuest,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(LcxTestTags.LOGIN_GUEST_BUTTON),
        ) {
            Text("Entrar como invitado")
        }
        ErrorText(state.error)
    }
}

@Composable
private fun PinEntry(
    state: LoginUiState,
    onPinChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(LcxSpacing.md),
    ) {
        Text(
            text = state.selectedOperator?.fullName ?: "Usuario",
            style = MaterialTheme.typography.titleMedium,
        )
        LcxTextField(
            value = state.pin,
            onValueChange = onPinChanged,
            label = "PIN",
            keyboardType = KeyboardType.NumberPassword,
            visualTransformation = PasswordVisualTransformation(),
            imeAction = ImeAction.Done,
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(LcxTestTags.LOGIN_PIN_FIELD),
        )
        ErrorText(state.error)
        LcxButton(
            text = "Entrar",
            onClick = onSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(LcxTestTags.LOGIN_SUBMIT_PIN),
            enabled = state.pin.length in 4..6,
        )
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Volver a usuarios")
        }
    }
}

@Composable
private fun GuestEntry(
    state: LoginUiState,
    onCodeChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(LcxSpacing.md),
    ) {
        Text(
            text = "Invitado",
            style = MaterialTheme.typography.titleMedium,
        )
        LcxTextField(
            value = state.guestCode,
            onValueChange = onCodeChanged,
            label = "Codigo de un solo uso",
            keyboardType = KeyboardType.NumberPassword,
            imeAction = ImeAction.Done,
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            modifier = Modifier.fillMaxWidth(),
        )
        ErrorText(state.error)
        LcxButton(
            text = "Entrar como invitado",
            onClick = onSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(LcxTestTags.LOGIN_SUBMIT_GUEST),
            enabled = state.guestCode.length == 6,
        )
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Volver a usuarios")
        }
    }
}

@Composable
private fun SelectionRow(
    title: String,
    subtitle: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = if (enabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(LcxSpacing.md),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ErrorText(error: String?) {
    if (error == null) return
    Text(
        text = error,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.fillMaxWidth(),
    )
}

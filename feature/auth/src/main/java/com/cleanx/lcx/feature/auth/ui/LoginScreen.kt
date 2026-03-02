package com.cleanx.lcx.feature.auth.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cleanx.lcx.core.theme.LcxSpacing
import com.cleanx.lcx.core.ui.LcxButton
import com.cleanx.lcx.core.ui.LcxTextField

@Composable
fun LoginScreen(
    onAuthenticated: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    val emailFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(state.phase) {
        if (state.phase == LoginPhase.Success) {
            onAuthenticated()
        }
    }

    // Auto-focus on email field when screen appears
    LaunchedEffect(Unit) {
        emailFocusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(LcxSpacing.xl)
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Branding
        Text(
            text = "LCX",
            style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(LcxSpacing.xs))
        Text(
            text = "La Clinica del Vestido",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(LcxSpacing.xxl))

        Text(
            text = "Iniciar sesion",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )

        Spacer(Modifier.height(LcxSpacing.lg))

        // Email field
        LcxTextField(
            value = state.email,
            onValueChange = viewModel::onEmailChanged,
            label = "Correo electronico",
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(emailFocusRequester),
            keyboardType = KeyboardType.Email,
            enabled = state.phase != LoginPhase.Loading,
            imeAction = ImeAction.Next,
            keyboardActions = KeyboardActions(
                onNext = { passwordFocusRequester.requestFocus() },
            ),
        )

        Spacer(Modifier.height(LcxSpacing.sm))

        // Password field
        LcxTextField(
            value = state.password,
            onValueChange = viewModel::onPasswordChanged,
            label = "Contrasena",
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(passwordFocusRequester),
            keyboardType = KeyboardType.Password,
            visualTransformation = PasswordVisualTransformation(),
            enabled = state.phase != LoginPhase.Loading,
            error = state.error,
            imeAction = ImeAction.Done,
            keyboardActions = KeyboardActions(
                onDone = {
                    keyboardController?.hide()
                    viewModel.signIn()
                },
            ),
        )

        Spacer(Modifier.height(LcxSpacing.md))

        // Login button
        LcxButton(
            text = if (state.phase == LoginPhase.Loading) "Ingresando..." else "Ingresar",
            onClick = {
                keyboardController?.hide()
                viewModel.signIn()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = state.phase != LoginPhase.Loading,
            isLoading = state.phase == LoginPhase.Loading,
        )
    }
}

package com.cleanx.lcx.feature.auth

import androidx.compose.runtime.Composable
import com.cleanx.lcx.feature.auth.ui.LoginScreen

/**
 * Backward-compatible wrapper that delegates to the new [LoginScreen].
 */
@Composable
fun AuthScreen(
    onAuthenticated: () -> Unit,
) {
    LoginScreen(onAuthenticated = onAuthenticated)
}

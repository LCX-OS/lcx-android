package com.cleanx.lcx.ui.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cleanx.lcx.core.model.UserRole
import com.cleanx.lcx.core.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Provides the current user's [UserRole] to [MainScaffold] so that
 * bottom navigation tabs and More hub items can be filtered by role.
 */
@HiltViewModel
class MainScaffoldViewModel @Inject constructor(
    private val sessionManager: SessionManager,
) : ViewModel() {

    val userRole: StateFlow<UserRole> = sessionManager.observeUserRole()
        .map { it ?: UserRole.EMPLOYEE }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = UserRole.EMPLOYEE,
        )

    val userEmail: StateFlow<String?> = sessionManager.observeUserEmail()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = null,
        )

    val userFullName: StateFlow<String?> = sessionManager.observeUserFullName()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = null,
        )

    val userBranch: StateFlow<String?> = sessionManager.observeUserBranch()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = null,
        )

    val userBadgeText: StateFlow<String> = combine(userFullName, userEmail, userRole) { fullName, email, role ->
        val displayName = fullName?.trim().orEmpty()
        val localPart = email?.substringBefore('@')?.trim().orEmpty()
        when {
            displayName.isNotBlank() -> displayName
            localPart.isNotBlank() -> localPart
            role == UserRole.SUPERADMIN -> "Superadmin"
            role == UserRole.MANAGER -> "Gerente"
            else -> "Operador"
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = "Operador",
    )

    val userStatusText: StateFlow<String> = combine(userBadgeText, userBranch) { user, branch ->
        if (!branch.isNullOrBlank()) {
            "$user · $branch"
        } else {
            user
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = "Operador",
    )
}

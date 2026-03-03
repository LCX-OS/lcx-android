package com.cleanx.lcx.ui.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cleanx.lcx.core.model.UserRole
import com.cleanx.lcx.core.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
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
}

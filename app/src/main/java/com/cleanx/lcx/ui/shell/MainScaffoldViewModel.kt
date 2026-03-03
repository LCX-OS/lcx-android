package com.cleanx.lcx.ui.shell

import androidx.lifecycle.ViewModel
import com.cleanx.lcx.core.model.UserRole
import com.cleanx.lcx.core.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Provides the current user's [UserRole] to [MainScaffold] so that
 * bottom navigation tabs and More hub items can be filtered by role.
 */
@HiltViewModel
class MainScaffoldViewModel @Inject constructor(
    private val sessionManager: SessionManager,
) : ViewModel() {

    val userRole: UserRole
        get() = sessionManager.getUserRole() ?: UserRole.EMPLOYEE
}

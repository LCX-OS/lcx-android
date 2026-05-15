package com.cleanx.lcx.feature.auth.ui

import com.cleanx.lcx.core.session.SessionManager
import com.cleanx.lcx.core.config.BuildConfigProvider
import com.cleanx.lcx.feature.auth.data.AuthApi
import com.cleanx.lcx.feature.auth.data.AuthRepository
import com.cleanx.lcx.feature.auth.data.DeviceAuthApi
import com.cleanx.lcx.feature.auth.data.DeviceBranchesResponse
import com.cleanx.lcx.feature.auth.data.DeviceOperatorResponse
import com.cleanx.lcx.feature.auth.data.DeviceOperatorsResponse
import io.github.jan.supabase.SupabaseClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private lateinit var authRepository: AuthRepository
    private lateinit var authApi: AuthApi
    private lateinit var deviceAuthApi: DeviceAuthApi
    private lateinit var sessionManager: SessionManager
    private lateinit var config: BuildConfigProvider
    private lateinit var supabaseClient: SupabaseClient

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        authApi = mockk()
        deviceAuthApi = mockk()
        sessionManager = mockk(relaxUnitFun = true)
        config = mockk()
        supabaseClient = mockk()
        authRepository = AuthRepository(
            authApi = authApi,
            deviceAuthApi = deviceAuthApi,
            sessionManager = sessionManager,
            supabaseClient = supabaseClient,
            config = config,
            json = Json { ignoreUnknownKeys = true },
        )
        every { sessionManager.getAccessToken() } returns null
        every { sessionManager.getRefreshToken() } returns null
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init restores selected branch and loads operators`() = runTest {
        coEvery { deviceAuthApi.branches() } returns Response.success(DeviceBranchesResponse(listOf("La Esperanza")))
        coEvery { deviceAuthApi.operators("La Esperanza") } returns Response.success(
            DeviceOperatorsResponse(
                listOf(DeviceOperatorResponse("operator-1", "Operador Uno", "La Esperanza", hasPin = true)),
            ),
        )
        every { sessionManager.getSelectedBranch() } returns "La Esperanza"

        val viewModel = LoginViewModel(authRepository, sessionManager)
        advanceUntilIdle()

        assertEquals(LoginPhase.OperatorSelection, viewModel.uiState.value.phase)
        assertEquals("La Esperanza", viewModel.uiState.value.selectedBranch)
        assertEquals(1, viewModel.uiState.value.operators.size)
    }

    @Test
    fun `change branch clears session and selected branch`() = runTest {
        coEvery { deviceAuthApi.branches() } returns Response.success(DeviceBranchesResponse(listOf("La Esperanza")))
        every { sessionManager.getSelectedBranch() } returns null

        val viewModel = LoginViewModel(authRepository, sessionManager)
        advanceUntilIdle()

        viewModel.changeBranch()
        advanceUntilIdle()

        coVerify { sessionManager.clearSession() }
        coVerify { sessionManager.clearSelectedBranch() }
        assertEquals(LoginPhase.BranchSelection, viewModel.uiState.value.phase)
    }
}

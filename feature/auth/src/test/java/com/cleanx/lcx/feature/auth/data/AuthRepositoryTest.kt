package com.cleanx.lcx.feature.auth.data

import com.cleanx.lcx.core.session.SessionManager
import io.github.jan.supabase.SupabaseClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class AuthRepositoryTest {

    private lateinit var authApi: AuthApi
    private lateinit var sessionManager: SessionManager
    private lateinit var supabaseClient: SupabaseClient
    private lateinit var repository: AuthRepository
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Before
    fun setUp() {
        authApi = mockk()
        sessionManager = mockk(relaxUnitFun = true)
        supabaseClient = mockk(relaxed = true)
        repository = AuthRepository(authApi, sessionManager, supabaseClient, json)
    }

    // -- Sign in success stores token --

    @Test
    fun `signIn success stores token and returns Success`() = runTest {
        coEvery { authApi.signIn(any()) } returns Response.success(
            SignInResponse(
                accessToken = "test-token-abc",
                userId = "user-123",
                user = AuthUser(id = "user-123", email = "test@example.com"),
            ),
        )

        val result = repository.signIn("test@example.com", "password123")

        assertTrue("Expected Success but was $result", result is AuthResult.Success)
        val success = result as AuthResult.Success
        assertEquals("user-123", success.userId)
        assertEquals("test-token-abc", success.accessToken)

        coVerify { sessionManager.saveAccessToken("test-token-abc") }
    }

    // -- Sign in success with userId fallback --

    @Test
    fun `signIn success uses userId when user object is null`() = runTest {
        coEvery { authApi.signIn(any()) } returns Response.success(
            SignInResponse(
                accessToken = "token-xyz",
                userId = "fallback-user-id",
                user = null,
            ),
        )

        val result = repository.signIn("test@example.com", "password123")

        assertTrue(result is AuthResult.Success)
        assertEquals("fallback-user-id", (result as AuthResult.Success).userId)
    }

    // -- Sign in failure returns Error --

    @Test
    fun `signIn failure returns Error with message`() = runTest {
        val errorJson = """{"error":"invalid_grant","error_description":"Credenciales invalidas"}"""
        val errorBody = errorJson.toResponseBody("application/json".toMediaType())

        coEvery { authApi.signIn(any()) } returns Response.error(400, errorBody)

        val result = repository.signIn("bad@example.com", "wrong")

        assertTrue("Expected Error but was $result", result is AuthResult.Error)
        assertEquals("Credenciales invalidas", (result as AuthResult.Error).message)

        // Must NOT store token on failure
        coVerify(exactly = 0) { sessionManager.saveAccessToken(any()) }
    }

    // -- Sign in network error --

    @Test
    fun `signIn network exception returns Error`() = runTest {
        coEvery { authApi.signIn(any()) } throws java.io.IOException("Network unreachable")

        val result = repository.signIn("test@example.com", "password123")

        assertTrue(result is AuthResult.Error)
        assertEquals("Network unreachable", (result as AuthResult.Error).message)
    }

    // -- Sign out clears token --

    @Test
    fun `signOut clears session`() = runTest {
        repository.signOut()

        coVerify { sessionManager.clearSession() }
    }

    // -- isAuthenticated returns correct state --

    @Test
    fun `isAuthenticated returns true when token exists`() {
        every { sessionManager.getAccessToken() } returns "some-token"

        assertTrue(repository.isAuthenticated())
    }

    @Test
    fun `isAuthenticated returns false when token is null`() {
        every { sessionManager.getAccessToken() } returns null

        assertFalse(repository.isAuthenticated())
    }

    @Test
    fun `isAuthenticated returns false when token is blank`() {
        every { sessionManager.getAccessToken() } returns ""

        assertFalse(repository.isAuthenticated())
    }

    // -- Error with unparseable body --

    @Test
    fun `signIn error with unparseable body returns fallback message`() = runTest {
        val errorBody = "Internal Server Error".toResponseBody("text/plain".toMediaType())

        coEvery { authApi.signIn(any()) } returns Response.error(500, errorBody)

        val result = repository.signIn("test@example.com", "password123")

        assertTrue(result is AuthResult.Error)
        assertEquals("Credenciales invalidas.", (result as AuthResult.Error).message)
    }
}

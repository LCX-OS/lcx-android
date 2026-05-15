package com.cleanx.lcx.feature.auth.data

import com.cleanx.lcx.core.config.BuildConfigProvider
import com.cleanx.lcx.core.model.UserRole
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
    private lateinit var deviceAuthApi: DeviceAuthApi
    private lateinit var sessionManager: SessionManager
    private lateinit var supabaseClient: SupabaseClient
    private lateinit var config: BuildConfigProvider
    private lateinit var repository: AuthRepository
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Before
    fun setUp() {
        authApi = mockk()
        deviceAuthApi = mockk()
        sessionManager = mockk(relaxUnitFun = true)
        supabaseClient = mockk(relaxed = true)
        config = mockk()
        every { config.supabaseUrl } returns "https://olheihdjfhzgrdpmylvh.supabase.co"
        every { config.deviceAuthBootstrapToken } returns "local-device-auth"
        repository = AuthRepository(authApi, deviceAuthApi, sessionManager, supabaseClient, config, json)
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

    @Test
    fun `signInWithPin success stores device session metadata`() = runTest {
        coEvery { deviceAuthApi.signInWithPin(any()) } returns Response.success(
            DeviceAuthSessionResponse(
                accessToken = "device-access-token",
                refreshToken = "device-refresh-token",
                expiresIn = 3600,
                expiresAt = 1_800_000_000,
                profile = DeviceAuthProfileResponse(
                    id = "operator-1",
                    fullName = "Operador Uno",
                    role = "employee",
                    branch = "La Esperanza",
                ),
            ),
        )

        val result = repository.signInWithPin("operator-1", "La Esperanza", "1234")

        assertTrue(result is AuthResult.Success)
        assertEquals("operator-1", (result as AuthResult.Success).userId)
        coVerify { sessionManager.saveAccessToken("device-access-token") }
        coVerify { sessionManager.saveRefreshToken("device-refresh-token") }
        coVerify { sessionManager.saveTokenExpiresAtEpochSeconds(1_800_000_000) }
        coVerify { sessionManager.saveUserId("operator-1") }
        coVerify { sessionManager.saveUserFullName("Operador Uno") }
        coVerify { sessionManager.saveUserBranch("La Esperanza") }
        coVerify { sessionManager.saveSelectedBranch("La Esperanza") }
        coVerify { sessionManager.saveUserRole(UserRole.EMPLOYEE) }
    }

    @Test
    fun `refreshSession success stores refreshed token`() = runTest {
        every { sessionManager.getRefreshToken() } returns "refresh-token"
        every { sessionManager.getUserId() } returns ""
        every { sessionManager.getUserEmail() } returns null
        coEvery { authApi.refreshSession(RefreshTokenRequest("refresh-token")) } returns Response.success(
            SignInResponse(
                accessToken = "new-access-token",
                refreshToken = "new-refresh-token",
                expiresIn = 3600,
                expiresAt = 1_800_000_500,
            ),
        )

        assertTrue(repository.refreshSession())

        coVerify { sessionManager.saveAccessToken("new-access-token") }
        coVerify { sessionManager.saveRefreshToken("new-refresh-token") }
        coVerify { sessionManager.saveTokenExpiresAtEpochSeconds(1_800_000_500) }
    }

    @Test
    fun `refreshSession failure clears session`() = runTest {
        every { sessionManager.getRefreshToken() } returns "refresh-token"
        coEvery { authApi.refreshSession(any()) } returns Response.error(
            401,
            """{"error":"invalid_grant"}""".toResponseBody("application/json".toMediaType()),
        )

        assertFalse(repository.refreshSession())

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

    @Test
    fun `isAuthenticated returns false when JWT issuer host mismatches configured supabase host`() {
        val mismatchedToken = buildJwtWithIssuer("https://evil.example.com/auth/v1")
        every { sessionManager.getAccessToken() } returns mismatchedToken

        assertFalse(repository.isAuthenticated())
    }

    @Test
    fun `isAuthenticated returns true when JWT issuer host matches configured supabase host`() {
        val matchingToken = buildJwtWithIssuer("https://olheihdjfhzgrdpmylvh.supabase.co/auth/v1")
        every { sessionManager.getAccessToken() } returns matchingToken

        assertTrue(repository.isAuthenticated())
    }

    @Test
    fun `isAuthenticated accepts loopback issuer aliases for local supabase`() {
        every { config.supabaseUrl } returns "http://10.0.2.2:54321"
        repository = AuthRepository(authApi, deviceAuthApi, sessionManager, supabaseClient, config, json)
        val loopbackToken = buildJwtWithIssuer("http://127.0.0.1:54321/auth/v1")
        every { sessionManager.getAccessToken() } returns loopbackToken

        assertTrue(repository.isAuthenticated())
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

    private fun buildJwtWithIssuer(iss: String): String {
        val header = """{"alg":"none","typ":"JWT"}"""
        val payload = """{"iss":"$iss"}"""
        return "${base64Url(header)}.${base64Url(payload)}."
    }

    private fun base64Url(raw: String): String {
        return java.util.Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(raw.toByteArray())
    }
}

package com.cleanx.lcx.core.network

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp interceptor that detects 401 responses and emits a global
 * session-expired event. This allows any screen to observe [sessionExpired]
 * and redirect the user to the login screen without dead-ends.
 *
 * This interceptor does NOT retry or modify the response — it only observes
 * and signals, keeping backward compatibility with existing per-call error
 * handling in repositories.
 */
@Singleton
class SessionExpiredInterceptor @Inject constructor() : Interceptor {

    private val _sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits whenever a 401 response is detected. Collect from Activity/NavHost. */
    val sessionExpired: SharedFlow<Unit> = _sessionExpired.asSharedFlow()

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val suppressSessionExpired = originalRequest.header(SUPPRESS_SESSION_EXPIRED_HEADER)
            ?.equals(SUPPRESS_SESSION_EXPIRED_VALUE, ignoreCase = true) == true
        val request = if (suppressSessionExpired) {
            originalRequest.newBuilder()
                .removeHeader(SUPPRESS_SESSION_EXPIRED_HEADER)
                .build()
        } else {
            originalRequest
        }

        val response = chain.proceed(request)

        if (response.code == HTTP_UNAUTHORIZED && !suppressSessionExpired) {
            Timber.tag("AUTH").w(
                "401 detected on %s %s — emitting session-expired event",
                request.method,
                request.url.encodedPath,
            )
            _sessionExpired.tryEmit(Unit)
        } else if (response.code == HTTP_UNAUTHORIZED) {
            Timber.tag("AUTH").w(
                "401 detected on %s %s — session-expired suppressed for local handling",
                request.method,
                request.url.encodedPath,
            )
        }

        return response
    }

    companion object {
        const val HTTP_UNAUTHORIZED = 401
        const val SUPPRESS_SESSION_EXPIRED_HEADER = "X-LCX-Suppress-Session-Expired"
        const val SUPPRESS_SESSION_EXPIRED_VALUE = "true"
    }
}

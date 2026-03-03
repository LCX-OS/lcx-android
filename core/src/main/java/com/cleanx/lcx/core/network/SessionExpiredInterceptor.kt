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
        val response = chain.proceed(chain.request())

        if (response.code == HTTP_UNAUTHORIZED) {
            Timber.tag("AUTH").w(
                "401 detected on %s %s — emitting session-expired event",
                chain.request().method,
                chain.request().url.encodedPath,
            )
            _sessionExpired.tryEmit(Unit)
        }

        return response
    }

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
    }
}

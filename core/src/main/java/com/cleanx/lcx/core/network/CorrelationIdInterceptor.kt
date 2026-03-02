package com.cleanx.lcx.core.network

import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp interceptor that attaches an `X-Correlation-Id` header to every
 * outgoing request.
 *
 * If a correlation ID is available in [CorrelationContext] (set by the
 * transaction flow), that value is used so all requests within a single
 * transaction share the same ID. Otherwise a fresh UUID is generated per
 * request.
 */
@Singleton
class CorrelationIdInterceptor @Inject constructor() : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val correlationId = CorrelationContext.get() ?: UUID.randomUUID().toString()
        val request = chain.request().newBuilder()
            .addHeader("X-Correlation-Id", correlationId)
            .build()
        Timber.tag("HTTP").d("[%s] %s %s", correlationId, request.method, request.url)
        val response = chain.proceed(request)
        Timber.tag("HTTP").d("[%s] %d %s", correlationId, response.code, request.url)
        return response
    }
}

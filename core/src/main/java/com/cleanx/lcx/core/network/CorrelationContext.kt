package com.cleanx.lcx.core.network

/**
 * Thread-local holder for the current correlation ID.
 *
 * When a transaction flow sets a correlation ID via [set], the
 * [CorrelationIdInterceptor] will prefer this value over generating
 * a fresh UUID. This ensures every HTTP request within a single
 * transaction shares the same correlation ID.
 *
 * Callers MUST call [clear] when the flow completes to avoid leaking
 * the ID into unrelated requests.
 */
object CorrelationContext {
    private val currentId = ThreadLocal<String>()

    fun set(id: String) {
        currentId.set(id)
    }

    fun get(): String? = currentId.get()

    fun clear() {
        currentId.remove()
    }
}

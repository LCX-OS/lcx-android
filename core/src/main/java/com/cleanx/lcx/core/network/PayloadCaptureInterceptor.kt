package com.cleanx.lcx.core.network

import android.content.Context
import com.cleanx.lcx.core.config.BuildConfigProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.RequestBody
import okhttp3.Response
import okio.Buffer
import timber.log.Timber
import java.io.File
import java.time.Instant
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Captures request/response payloads to a JSONL file for QA contract analysis.
 *
 * Enabled only when wired from debug OkHttp clients.
 */
class PayloadCaptureInterceptor(
    private val channel: String,
    private val writer: PayloadCaptureWriter,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val startedAtNanos = System.nanoTime()

        val requestBody = request.body.captureText()
        val requestHeaders = request.headers.toSafeMap()
        val correlationId = request.header("X-Correlation-Id")

        return try {
            val response = chain.proceed(request)
            val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos)

            writer.write(
                CapturedHttpPayload(
                    timestamp = Instant.now().toString(),
                    channel = channel,
                    method = request.method,
                    url = request.url.toString(),
                    path = request.url.encodedPath,
                    correlationId = correlationId,
                    requestHeaders = requestHeaders,
                    requestBody = requestBody,
                    responseCode = response.code,
                    responseHeaders = response.headers.toSafeMap(),
                    responseBody = response.captureText(),
                    elapsedMs = elapsedMs,
                    error = null,
                ),
            )
            response
        } catch (t: Throwable) {
            val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos)

            writer.write(
                CapturedHttpPayload(
                    timestamp = Instant.now().toString(),
                    channel = channel,
                    method = request.method,
                    url = request.url.toString(),
                    path = request.url.encodedPath,
                    correlationId = correlationId,
                    requestHeaders = requestHeaders,
                    requestBody = requestBody,
                    responseCode = null,
                    responseHeaders = emptyMap(),
                    responseBody = null,
                    elapsedMs = elapsedMs,
                    error = t.message ?: t::class.java.simpleName,
                ),
            )
            throw t
        }
    }
}

@Singleton
class PayloadCaptureWriter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val config: BuildConfigProvider,
    private val json: Json,
) {
    private val lock = Any()

    fun write(entry: CapturedHttpPayload) {
        if (!config.isDebug) return
        val file = payloadFile() ?: return

        try {
            val line = json.encodeToString(entry)
            synchronized(lock) {
                file.appendText(line + "\n")
            }
        } catch (t: Throwable) {
            Timber.tag("PAYLOAD").w(t, "Failed writing payload capture")
        }
    }

    fun captureFilePath(): String? = payloadFile()?.absolutePath

    private fun payloadFile(): File? {
        val baseDir = context.getExternalFilesDir("payload-capture") ?: context.filesDir
        if (!baseDir.exists() && !baseDir.mkdirs()) return null
        return File(baseDir, PAYLOAD_CAPTURE_FILE)
    }

    companion object {
        const val PAYLOAD_CAPTURE_FILE: String = "payload-capture.jsonl"
    }
}

@Serializable
data class CapturedHttpPayload(
    val timestamp: String,
    val channel: String,
    val method: String,
    val url: String,
    val path: String,
    val correlationId: String? = null,
    val requestHeaders: Map<String, String> = emptyMap(),
    val requestBody: String? = null,
    val responseCode: Int? = null,
    val responseHeaders: Map<String, String> = emptyMap(),
    val responseBody: String? = null,
    val elapsedMs: Long,
    val error: String? = null,
)

private fun Headers.toSafeMap(): Map<String, String> {
    return names()
        .sorted()
        .associateWith { name ->
            val raw = values(name).joinToString(", ")
            when (name.lowercase(Locale.US)) {
                "authorization",
                "apikey",
                "cookie",
                "set-cookie",
                "x-lcx-device-auth-token",
                "x-client-info" -> "<redacted>"
                else -> raw.trim().take(MAX_HEADER_CHARS)
            }
        }
}

private fun RequestBody?.captureText(): String? {
    this ?: return null
    if (isDuplex() || isOneShot()) return "<streamed body omitted>"
    val contentType = contentType()
    if (!contentType.isTextLike()) return "<${contentType ?: "binary"} body omitted>"

    return try {
        val buffer = Buffer()
        writeTo(buffer)
        buffer.readUtf8().trimToLimit(MAX_BODY_CHARS)
    } catch (_: Throwable) {
        "<request body unavailable>"
    }
}

private fun Response.captureText(): String? {
    val body = body ?: return null
    val contentType = body.contentType()
    if (!contentType.isTextLike()) return "<${contentType ?: "binary"} body omitted>"

    return try {
        peekBody(MAX_PEEK_BYTES.toLong())
            .string()
            .trimToLimit(MAX_BODY_CHARS)
    } catch (_: Throwable) {
        "<response body unavailable>"
    }
}

private fun MediaType?.isTextLike(): Boolean {
    this ?: return true
    val sub = subtype.lowercase(Locale.US)
    return type.equals("text", ignoreCase = true) ||
        sub.contains("json") ||
        sub.contains("xml") ||
        sub.contains("html") ||
        sub.contains("x-www-form-urlencoded")
}

private fun String.trimToLimit(maxChars: Int): String {
    if (length <= maxChars) return this
    return take(maxChars) + "... [truncated]"
}

private const val MAX_BODY_CHARS = 16_000
private const val MAX_PEEK_BYTES = 64_000
private const val MAX_HEADER_CHARS = 512

package com.cleanx.lcx.core.network

import com.cleanx.lcx.core.config.BuildConfigProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Minimal Supabase Storage REST client for evidence uploads.
 *
 * The existing Supabase Kotlin client in :core installs PostgREST only. Storage
 * uploads are simple authenticated HTTP calls, so this keeps the feature small
 * and avoids coupling table access to another SDK module.
 */
@Singleton
class SupabaseStorageClient @Inject constructor(
    private val config: BuildConfigProvider,
    private val tokenProvider: TokenProvider,
) {
    private val httpClient = OkHttpClient()

    suspend fun uploadObject(
        bucket: String,
        path: String,
        bytes: ByteArray,
        contentType: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val token = tokenProvider.getAccessToken()
                ?: throw SupabaseError.Unauthorized("No hay sesion valida para subir evidencia.")
            val url = storageObjectUrl(bucket = bucket, path = path)
            val body = bytes.toRequestBody(contentType.toMediaTypeOrNull())
            val request = Request.Builder()
                .url(url)
                .post(body)
                .header("apikey", config.supabaseAnonKey)
                .header("Authorization", "Bearer $token")
                .header("cache-control", "3600")
                .header("x-upsert", "false")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw mapStorageFailure(
                        operation = "upload",
                        bucket = bucket,
                        path = path,
                        statusCode = response.code,
                        responseBody = response.body?.string(),
                    )
                }
            }
        }.onFailure { error ->
            Timber.w(error, "Supabase storage upload failed: bucket=%s path=%s", bucket, path)
        }
    }

    suspend fun deleteObject(bucket: String, path: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val token = tokenProvider.getAccessToken()
                ?: throw SupabaseError.Unauthorized("No hay sesion valida para eliminar evidencia.")
            val body = """{"prefixes":["${path.escapeJson()}"]}"""
                .toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder()
                .url(storageBucketUrl(bucket = bucket))
                .delete(body)
                .header("apikey", config.supabaseAnonKey)
                .header("Authorization", "Bearer $token")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw mapStorageFailure(
                        operation = "delete",
                        bucket = bucket,
                        path = path,
                        statusCode = response.code,
                        responseBody = response.body?.string(),
                    )
                }
            }
        }.onFailure { error ->
            Timber.w(error, "Supabase storage delete failed: bucket=%s path=%s", bucket, path)
        }
    }

    private fun storageObjectUrl(bucket: String, path: String) =
        config.supabaseUrl
            .trimEnd('/')
            .toHttpUrl()
            .newBuilder()
            .addPathSegments("storage/v1/object")
            .addPathSegment(bucket)
            .addPathSegments(path.trimStart('/'))
            .build()

    private fun storageBucketUrl(bucket: String) =
        config.supabaseUrl
            .trimEnd('/')
            .toHttpUrl()
            .newBuilder()
            .addPathSegments("storage/v1/object")
            .addPathSegment(bucket)
            .build()

    private fun String.escapeJson(): String {
        return replace("\\", "\\\\").replace("\"", "\\\"")
    }

    private fun mapStorageFailure(
        operation: String,
        bucket: String,
        path: String,
        statusCode: Int,
        responseBody: String?,
    ): SupabaseError {
        val detail = responseBody
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { ": $it" }
            .orEmpty()
        val message = "Storage $operation failed for $bucket/$path ($statusCode)$detail"
        return when (statusCode) {
            401, 403 -> SupabaseError.Unauthorized(message)
            404 -> SupabaseError.NotFound(message)
            in 400..499 -> SupabaseError.BadRequest(message, statusCode)
            in 500..599 -> SupabaseError.ServerError(message, statusCode)
            else -> SupabaseError.Unknown(message)
        }
    }
}

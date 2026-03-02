package com.cleanx.lcx.feature.auth.di

import com.cleanx.lcx.core.config.BuildConfigProvider
import com.cleanx.lcx.feature.auth.data.AuthApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Qualifier for the unauthenticated OkHttpClient / Retrofit used for auth
 * endpoints. These must NOT include the [AuthInterceptor] to avoid a
 * circular dependency (auth endpoint provides the token the interceptor needs).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthRetrofit

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    @Provides
    @Singleton
    @AuthRetrofit
    fun provideAuthOkHttpClient(config: BuildConfigProvider): OkHttpClient {
        return OkHttpClient.Builder()
            .apply {
                if (config.isDebug) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.BODY
                        },
                    )
                }
            }
            .build()
    }

    @Provides
    @Singleton
    fun provideAuthApi(@AuthRetrofit client: OkHttpClient, json: Json, config: BuildConfigProvider): AuthApi {
        val retrofit = Retrofit.Builder()
            .baseUrl(config.supabaseUrl + "/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        return retrofit.create(AuthApi::class.java)
    }
}

package com.cleanx.lcx.core.di

import com.cleanx.lcx.core.config.BuildConfigProvider
import com.cleanx.lcx.core.network.AuthInterceptor
import com.cleanx.lcx.core.network.CorrelationIdInterceptor
import com.cleanx.lcx.core.network.SessionExpiredInterceptor
import com.cleanx.lcx.core.network.TicketApi
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
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        correlationIdInterceptor: CorrelationIdInterceptor,
        sessionExpiredInterceptor: SessionExpiredInterceptor,
        config: BuildConfigProvider,
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(correlationIdInterceptor)
            .addInterceptor(authInterceptor)
            .addInterceptor(sessionExpiredInterceptor)
            .apply {
                if (config.isDebug) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.BODY
                        }
                    )
                }
            }
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json, config: BuildConfigProvider): Retrofit {
        return Retrofit.Builder()
            .baseUrl(config.apiBaseUrl + "/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideTicketApi(retrofit: Retrofit): TicketApi {
        return retrofit.create(TicketApi::class.java)
    }
}

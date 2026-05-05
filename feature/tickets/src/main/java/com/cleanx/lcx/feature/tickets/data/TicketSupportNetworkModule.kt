package com.cleanx.lcx.feature.tickets.data

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TicketSupportNetworkModule {
    @Provides
    @Singleton
    fun provideTicketSupportApi(retrofit: Retrofit): TicketSupportApi {
        return retrofit.create(TicketSupportApi::class.java)
    }
}

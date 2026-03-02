package com.cleanx.lcx.core.di

import com.cleanx.lcx.core.network.TokenProvider
import com.cleanx.lcx.core.session.DataStoreSessionStore
import com.cleanx.lcx.core.session.SessionManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SessionModule {

    @Binds
    @Singleton
    abstract fun bindSessionManager(impl: DataStoreSessionStore): SessionManager

    @Binds
    @Singleton
    abstract fun bindTokenProvider(impl: DataStoreSessionStore): TokenProvider
}

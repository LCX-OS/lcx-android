package com.cleanx.lcx.di

import com.cleanx.lcx.BuildConfig
import com.cleanx.lcx.core.config.BuildConfigProvider
import com.cleanx.lcx.core.config.FeatureFlags
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppConfigModule {

    @Provides
    @Singleton
    fun provideBuildConfigProvider(): BuildConfigProvider = object : BuildConfigProvider {
        override val applicationId: String = BuildConfig.APPLICATION_ID
        override val apiBaseUrl: String = BuildConfig.API_BASE_URL
        override val notificationsBaseUrl: String = BuildConfig.NOTIFICATIONS_BASE_URL
        override val supabaseUrl: String = BuildConfig.SUPABASE_URL
        override val supabaseAnonKey: String = BuildConfig.SUPABASE_ANON_KEY
        override val zettleClientId: String = BuildConfig.ZETTLE_CLIENT_ID
        override val zettleRedirectUrl: String = BuildConfig.ZETTLE_REDIRECT_URL
        override val zettleApprovedApplicationId: String = BuildConfig.ZETTLE_APPROVED_APPLICATION_ID
        override val isDebug: Boolean = BuildConfig.DEBUG
        override val useRealZettle: Boolean = BuildConfig.USE_REAL_ZETTLE
        override val useRealBrother: Boolean = BuildConfig.USE_REAL_BROTHER
    }

    @Provides
    @Singleton
    fun provideFeatureFlags(config: BuildConfigProvider): FeatureFlags = object : FeatureFlags {
        override val useRealZettle: Boolean = config.useRealZettle
        override val useRealBrother: Boolean = config.useRealBrother
    }
}

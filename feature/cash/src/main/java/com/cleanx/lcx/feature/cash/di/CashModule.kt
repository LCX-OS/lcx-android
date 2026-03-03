package com.cleanx.lcx.feature.cash.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module for the caja (cash register) feature.
 *
 * [CashRepository] is constructor-injected with `@Inject` and `@Singleton`,
 * so Hilt resolves it automatically. This module is kept as a placeholder for
 * future bindings (e.g., interface abstractions or qualifiers).
 */
@Module
@InstallIn(SingletonComponent::class)
object CashModule

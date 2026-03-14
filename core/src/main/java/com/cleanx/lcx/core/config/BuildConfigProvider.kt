package com.cleanx.lcx.core.config

/**
 * Abstracts build-time configuration values so that library modules
 * do not depend on the :app module's generated BuildConfig class.
 *
 * Implemented in :app and bound via Hilt.
 */
interface BuildConfigProvider {
    val applicationId: String
    val apiBaseUrl: String
    val notificationsBaseUrl: String
    val supabaseUrl: String
    val supabaseAnonKey: String
    val zettleClientId: String
    val zettleRedirectUrl: String
    val zettleApprovedApplicationId: String
    val isDebug: Boolean
    val useRealZettle: Boolean
    val useRealBrother: Boolean
}

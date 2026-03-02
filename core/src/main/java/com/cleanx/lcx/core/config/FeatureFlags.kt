package com.cleanx.lcx.core.config

/**
 * Build-variant-aware feature flags that control which SDK implementations
 * are active at runtime.
 *
 * Implemented in the `:app` module where [BuildConfig] values are available,
 * and provided via Hilt so that library modules can read the flags without
 * depending on `:app`.
 */
interface FeatureFlags {

    /** `true` when the real PayPal Zettle SDK should be used for card payments. */
    val useRealZettle: Boolean

    /** `true` when the real Brother SDK should be used for label printing. */
    val useRealBrother: Boolean
}

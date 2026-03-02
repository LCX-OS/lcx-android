package com.cleanx.lcx.feature.payments.data

import android.content.Context

/**
 * Production implementation that delegates to the PayPal Zettle Android SDK.
 *
 * ## SDK wiring checklist (to be completed when the real dependency is added)
 *
 * 1. **Repository** — add to `settings.gradle.kts`:
 *    ```kotlin
 *    maven {
 *        url = uri("https://maven.pkg.github.com/iZettle/sdk-android")
 *        credentials(HttpHeaderCredentials::class) {
 *            name = "Authorization"
 *            value = "Bearer ${providers.gradleProperty("GITHUB_TOKEN").get()}"
 *        }
 *        authentication { create<HttpHeaderAuthentication>("header") }
 *    }
 *    ```
 *
 * 2. **Dependencies** — add to `app/build.gradle.kts`:
 *    ```kotlin
 *    implementation("com.zettle.sdk:core:<version>")
 *    implementation("com.zettle.sdk.feature.cardreader:ui:<version>")
 *    ```
 *
 * 3. **Manifest** — register the OAuth activity:
 *    ```xml
 *    <activity
 *        android:exported="true"
 *        android:name="com.izettle.android.auth.OAuthActivity"
 *        android:launchMode="singleTask"
 *        android:taskAffinity="@string/oauth_activity_task_affinity">
 *        <intent-filter>
 *            <data android:host="[host]" android:scheme="[scheme]" />
 *            <action android:name="android.intent.action.VIEW" />
 *            <category android:name="android.intent.category.DEFAULT" />
 *            <category android:name="android.intent.category.BROWSABLE" />
 *        </intent-filter>
 *    </activity>
 *    ```
 *
 * 4. **Permissions** — the SDK requires `ACCESS_FINE_LOCATION` at runtime.
 *
 * 5. **Initialization** (in [LcxApplication.onCreate]):
 *    ```kotlin
 *    val config = config(applicationContext) {
 *        isDevMode = BuildConfig.DEBUG
 *        auth {
 *            clientId = BuildConfig.ZETTLE_CLIENT_ID
 *            redirectUrl = BuildConfig.ZETTLE_REDIRECT_URL
 *        }
 *        addFeature(CardReaderFeature.Configuration)
 *    }
 *    ZettleSDK.configure(config)
 *    ProcessLifecycleOwner.get().lifecycle.addObserver(ZettleSDKLifecycle())
 *    ZettleSDK.instance.start()
 *    ```
 *
 * 6. **Payment launch** — uses `registerForActivityResult`:
 *    ```kotlin
 *    val reference = TransactionReference.Builder(ticketId)
 *        .put("TICKET_ID", ticketId)
 *        .build()
 *    val intent = CardReaderAction.Payment(
 *        amount = amountInMinorUnits, // Long, e.g. 15000 for $150.00
 *        reference = reference,
 *    ).charge(context)
 *    paymentLauncher.launch(intent)
 *    ```
 *
 * 7. **Result handling**:
 *    ```kotlin
 *    when (val result = data?.zettleResult()) {
 *        is ZettleResult.Completed<*> -> {
 *            val payment = CardReaderAction.fromPaymentResult(result)
 *            // payment.transactionId, payment.amount, ...
 *        }
 *        is ZettleResult.Cancelled -> { /* user dismissed */ }
 *        is ZettleResult.Failed    -> { /* result.reason */ }
 *    }
 *    ```
 *
 * Until the SDK dependency is available, [StubPaymentManager] is bound via Hilt.
 */
class ZettlePaymentManager : PaymentManager {

    override suspend fun initialize(context: Context) {
        // TODO: replace with real ZettleSDK.configure + start (see KDoc above)
        throw UnsupportedOperationException(
            "ZettlePaymentManager requires the real Zettle SDK dependency. " +
                "Use StubPaymentManager for development."
        )
    }

    override fun isInitialized(): Boolean = false

    override suspend fun requestPayment(amount: Double, reference: String): PaymentResult {
        // TODO: replace with real CardReaderAction.Payment flow (see KDoc above)
        throw UnsupportedOperationException(
            "ZettlePaymentManager requires the real Zettle SDK dependency. " +
                "Use StubPaymentManager for development."
        )
    }
}

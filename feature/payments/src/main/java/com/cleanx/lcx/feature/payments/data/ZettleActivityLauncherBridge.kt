package com.cleanx.lcx.feature.payments.data

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine

data class ExternalActivityResult(
    val resultCode: Int,
    val data: Intent?,
)

@Singleton
class ZettleActivityLauncherBridge @Inject constructor() {
    private val launchMutex = Mutex()
    private var activityRef: WeakReference<ComponentActivity>? = null
    private var launcher: ActivityResultLauncher<Intent>? = null
    private var pendingContinuation: CancellableContinuation<ExternalActivityResult>? = null

    fun register(
        activity: ComponentActivity,
        launcher: ActivityResultLauncher<Intent>,
    ) {
        activityRef = WeakReference(activity)
        this.launcher = launcher
    }

    fun unregister(activity: ComponentActivity) {
        if (activityRef?.get() !== activity) return
        activityRef = null
        launcher = null
        pendingContinuation?.let { continuation ->
            pendingContinuation = null
            continuation.resumeWithException(
                IllegalStateException("La actividad de Zettle se destruyo antes de completar el resultado."),
            )
        }
    }

    fun currentActivity(): ComponentActivity? = activityRef?.get()

    suspend fun launch(intent: Intent): ExternalActivityResult = launchMutex.withLock {
        val currentLauncher = launcher
            ?: throw IllegalStateException(
                "No hay ActivityResultLauncher de Zettle registrado en la actividad principal.",
            )

        suspendCancellableCoroutine { continuation ->
            pendingContinuation = continuation
            continuation.invokeOnCancellation {
                if (pendingContinuation === continuation) {
                    pendingContinuation = null
                }
            }
            currentLauncher.launch(intent)
        }
    }

    fun onActivityResult(
        resultCode: Int,
        data: Intent?,
    ) {
        pendingContinuation?.let { continuation ->
            pendingContinuation = null
            continuation.resume(
                ExternalActivityResult(
                    resultCode = resultCode,
                    data = data,
                ),
            )
        }
    }
}

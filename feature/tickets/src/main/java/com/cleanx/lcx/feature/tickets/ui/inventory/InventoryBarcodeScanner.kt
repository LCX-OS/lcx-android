package com.cleanx.lcx.feature.tickets.ui.inventory

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cleanx.lcx.core.theme.LcxSpacing
import com.cleanx.lcx.core.ui.ButtonVariant
import com.cleanx.lcx.core.ui.LcxButton
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun InventoryBarcodeScannerButton(
    enabled: Boolean,
    onBarcodeScanned: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showScanner by remember { mutableStateOf(false) }
    var permissionError by remember { mutableStateOf<String?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            permissionError = null
            showScanner = true
        } else {
            permissionError = "Activa el permiso de camara para escanear codigos de barras."
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(LcxSpacing.xs),
    ) {
        LcxButton(
            text = "Escanear con camara",
            onClick = {
                if (hasCameraPermission(context)) {
                    permissionError = null
                    showScanner = true
                } else {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            },
            enabled = enabled,
            variant = ButtonVariant.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
        permissionError?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }

    if (showScanner) {
        BarcodeScannerDialog(
            onDismiss = { showScanner = false },
            onBarcodeScanned = { value ->
                showScanner = false
                onBarcodeScanned(value)
            },
        )
    }
}

@Composable
private fun BarcodeScannerDialog(
    onDismiss: () -> Unit,
    onBarcodeScanned: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnBarcodeScanned by rememberUpdatedState(onBarcodeScanned)
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember { BarcodeScanning.getClient() }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var isStartingCamera by remember { mutableStateOf(true) }

    DisposableEffect(previewView, lifecycleOwner, scanner, cameraExecutor) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)
        var cameraProvider: ProcessCameraProvider? = null
        var disposed = false

        cameraProviderFuture.addListener(
            {
                runCatching {
                    val provider = cameraProviderFuture.get()
                    cameraProvider = provider
                    if (disposed) {
                        provider.unbindAll()
                        return@runCatching
                    }

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { imageAnalysis ->
                            imageAnalysis.setAnalyzer(
                                cameraExecutor,
                                BarcodeImageAnalyzer(
                                    scanner = scanner,
                                    mainExecutor = mainExecutor,
                                    onBarcodeDetected = { value -> currentOnBarcodeScanned(value) },
                                ),
                            )
                        }

                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    )
                    isStartingCamera = false
                    cameraError = null
                }.getOrElse { error ->
                    isStartingCamera = false
                    cameraError = error.message ?: "No se pudo iniciar la camara."
                }
            },
            mainExecutor,
        )

        onDispose {
            disposed = true
            runCatching { cameraProvider?.unbindAll() }
            scanner.close()
            cameraExecutor.shutdown()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(LcxSpacing.md),
                verticalArrangement = Arrangement.spacedBy(LcxSpacing.sm),
            ) {
                Text(
                    text = "Escanear codigo de barras",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Apunta la camara al codigo del producto.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AndroidView(
                        factory = { previewView },
                        modifier = Modifier.matchParentSize(),
                    )
                    if (isStartingCamera) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    }
                }

                cameraError?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Spacer(modifier = Modifier.height(LcxSpacing.xs))
                LcxButton(
                    text = "Cerrar",
                    onClick = onDismiss,
                    variant = ButtonVariant.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun hasCameraPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CAMERA,
    ) == PackageManager.PERMISSION_GRANTED
}

private class BarcodeImageAnalyzer(
    private val scanner: BarcodeScanner,
    private val mainExecutor: Executor,
    private val onBarcodeDetected: (String) -> Unit,
) : ImageAnalysis.Analyzer {
    private val isProcessing = AtomicBoolean(false)
    private val hasScanned = AtomicBoolean(false)

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (hasScanned.get() || !isProcessing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            isProcessing.set(false)
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val value = barcodes.firstNotNullOfOrNull { barcode ->
                    barcode.rawValue?.trim()?.takeIf { it.isNotBlank() }
                }
                if (value != null && hasScanned.compareAndSet(false, true)) {
                    mainExecutor.execute { onBarcodeDetected(value) }
                }
            }
            .addOnCompleteListener {
                isProcessing.set(false)
                imageProxy.close()
            }
    }
}

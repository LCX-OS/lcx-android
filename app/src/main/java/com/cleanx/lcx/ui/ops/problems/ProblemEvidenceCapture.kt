package com.cleanx.lcx.ui.ops.problems

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaRecorder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.cleanx.lcx.core.theme.LcxSpacing
import com.cleanx.lcx.core.ui.ButtonVariant
import com.cleanx.lcx.core.ui.LcxButton
import java.io.File
import java.util.UUID

private const val MAX_PHOTOS = 5

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PhotoEvidenceCapture(
    photos: List<CapturedEvidencePhoto>,
    onPhotoAdded: (CapturedEvidencePhoto) -> Unit,
    onPhotoRemoved: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val context = LocalContext.current
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var permissionError by remember { mutableStateOf<String?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        val uri = pendingUri
        pendingUri = null
        if (success && uri != null) {
            permissionError = null
            onPhotoAdded(
                CapturedEvidencePhoto(
                    id = UUID.randomUUID().toString(),
                    uri = uri.toString(),
                ),
            )
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            permissionError = null
            val uri = context.createEvidencePhotoUri()
            pendingUri = uri
            cameraLauncher.launch(uri)
        } else {
            permissionError = "Activa el permiso de camara para tomar fotos de evidencia."
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(LcxSpacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(LcxSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LcxButton(
                text = "Tomar foto",
                onClick = {
                    if (photos.size >= MAX_PHOTOS) {
                        permissionError = "Puedes adjuntar hasta $MAX_PHOTOS fotos."
                        return@LcxButton
                    }
                    if (context.hasPermission(Manifest.permission.CAMERA)) {
                        val uri = context.createEvidencePhotoUri()
                        pendingUri = uri
                        cameraLauncher.launch(uri)
                    } else {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                enabled = enabled && photos.size < MAX_PHOTOS,
                variant = ButtonVariant.Secondary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${photos.size}/$MAX_PHOTOS",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        permissionError?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (photos.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(LcxSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(LcxSpacing.sm),
            ) {
                photos.forEachIndexed { index, photo ->
                    Box(
                        modifier = Modifier
                            .size(104.dp)
                            .aspectRatio(1f)
                            .heightIn(min = 92.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        EvidenceThumbnail(
                            uri = photo.uri,
                            modifier = Modifier.matchParentSize(),
                        )
                        IconButton(
                            onClick = { onPhotoRemoved(photo.id) },
                            enabled = enabled,
                            modifier = Modifier.align(Alignment.TopEnd),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Quitar foto ${index + 1}",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AudioEvidenceCapture(
    audio: CapturedEvidenceAudio?,
    onAudioCaptured: (CapturedEvidenceAudio) -> Unit,
    onAudioRemoved: () -> Unit,
    onRecordingChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val context = LocalContext.current
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }
    var permissionError by remember { mutableStateOf<String?>(null) }
    var isRecording by remember { mutableStateOf(false) }

    fun stopRecording(commit: Boolean) {
        val activeRecorder = recorder ?: return
        val file = recordingFile
        runCatching {
            activeRecorder.stop()
        }.onFailure { error ->
            permissionError = error.localizedMessage ?: "No se pudo guardar el audio."
        }
        activeRecorder.release()
        recorder = null
        recordingFile = null
        isRecording = false
        onRecordingChanged(false)
        if (commit && file != null && file.isFile && file.length() > 0L) {
            permissionError = null
            onAudioCaptured(
                CapturedEvidenceAudio(
                    path = file.absolutePath,
                    fileName = file.name,
                ),
            )
        }
    }

    fun startRecording() {
        val file = context.createEvidenceAudioFile()
        val activeRecorder = MediaRecorder(context).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128_000)
            setAudioSamplingRate(44_100)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        recordingFile = file
        recorder = activeRecorder
        isRecording = true
        permissionError = null
        onRecordingChanged(true)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            runCatching { startRecording() }
                .onFailure { error ->
                    permissionError = error.localizedMessage ?: "No se pudo iniciar el microfono."
                    onRecordingChanged(false)
                }
        } else {
            permissionError = "Activa el permiso de microfono para grabar evidencia."
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            stopRecording(commit = false)
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(LcxSpacing.sm),
    ) {
        LcxButton(
            text = if (isRecording) "Detener grabacion" else "Grabar audio",
            onClick = {
                if (isRecording) {
                    stopRecording(commit = true)
                } else if (context.hasPermission(Manifest.permission.RECORD_AUDIO)) {
                    runCatching { startRecording() }
                        .onFailure { error ->
                            permissionError = error.localizedMessage ?: "No se pudo iniciar el microfono."
                            onRecordingChanged(false)
                        }
                } else {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            enabled = enabled,
            variant = if (isRecording) ButtonVariant.Danger else ButtonVariant.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )

        permissionError?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        audio?.let { captured ->
            AssistChip(
                onClick = {},
                label = { Text("Audio listo: ${captured.fileName}") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                trailingIcon = {
                    IconButton(onClick = onAudioRemoved, enabled = enabled) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Eliminar audio",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
            )
        }

        if (isRecording) {
            AssistChip(
                onClick = {},
                label = { Text("Grabando") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Stop,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
        }
    }
}

@Composable
private fun EvidenceThumbnail(uri: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(uri) {
        bitmap = context.contentResolver.openInputStream(Uri.parse(uri))?.use { input ->
            BitmapFactory.decodeStream(input)
        }
    }

    val currentBitmap = bitmap
    if (currentBitmap != null) {
        Image(
            bitmap = currentBitmap.asImageBitmap(),
            contentDescription = "Foto de evidencia",
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.CameraAlt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun Context.hasPermission(permission: String): Boolean {
    return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}

private fun Context.createEvidencePhotoUri(): Uri {
    val file = File.createTempFile(
        "evidence-photo-",
        ".jpg",
        evidenceDirectory("photos"),
    )
    return FileProvider.getUriForFile(
        this,
        "$packageName.fileprovider",
        file,
    )
}

private fun Context.createEvidenceAudioFile(): File {
    return File.createTempFile(
        "evidence-audio-",
        ".m4a",
        evidenceDirectory("audio"),
    )
}

private fun Context.evidenceDirectory(child: String): File {
    return File(cacheDir, "problem-evidence/$child").apply { mkdirs() }
}

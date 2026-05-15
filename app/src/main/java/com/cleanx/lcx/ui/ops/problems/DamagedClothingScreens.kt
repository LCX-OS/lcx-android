package com.cleanx.lcx.ui.ops.problems

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cleanx.lcx.core.theme.LcxSpacing
import com.cleanx.lcx.core.ui.ButtonVariant
import com.cleanx.lcx.core.ui.LcxButton
import com.cleanx.lcx.core.ui.LcxCard
import com.cleanx.lcx.core.ui.LcxTextField
import com.cleanx.lcx.core.ui.LcxTestTags

@Composable
fun DamagedClothingNewScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DamagedClothingNewViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    ProblemScaffold(
        title = "Ropa danada",
        onBack = onBack,
        modifier = modifier.testTag(LcxTestTags.DAMAGED_CLOTHING_NEW_ROOT),
    ) { contentModifier ->
        LazyColumn(
            modifier = contentModifier,
            verticalArrangement = Arrangement.spacedBy(LcxSpacing.md),
        ) {
            item {
                Spacer(modifier = Modifier.height(LcxSpacing.sm))
                LcxCard(title = "Documentar dano preexistente") {
                    Column(verticalArrangement = Arrangement.spacedBy(LcxSpacing.md)) {
                        LcxTextField(
                            value = state.ticketReference,
                            onValueChange = viewModel::updateTicketReference,
                            label = "Numero de ticket",
                            enabled = !state.isSubmitting,
                            imeAction = ImeAction.Next,
                        )
                        ProblemDropdown(
                            label = "Tipo de prenda",
                            value = state.garmentType,
                            options = garmentTypeOptions,
                            onValueChange = viewModel::updateGarmentType,
                            enabled = !state.isSubmitting,
                        )
                        ProblemDropdown(
                            label = "Tipo de dano",
                            value = state.damageType,
                            options = damageTypeOptions,
                            onValueChange = viewModel::updateDamageType,
                            enabled = !state.isSubmitting,
                        )
                        LcxTextField(
                            value = state.description,
                            onValueChange = viewModel::updateDescription,
                            label = "Descripcion del dano",
                            enabled = !state.isSubmitting,
                            singleLine = false,
                            maxLines = 5,
                        )
                    }
                }
            }

            item {
                LcxCard(title = "Evidencia fotografica") {
                    PhotoEvidenceCapture(
                        photos = state.photos,
                        onPhotoAdded = viewModel::addPhoto,
                        onPhotoRemoved = viewModel::removePhoto,
                        enabled = !state.isSubmitting,
                    )
                }
            }

            item {
                LcxCard(title = "Evidencia de audio") {
                    Column(verticalArrangement = Arrangement.spacedBy(LcxSpacing.md)) {
                        AudioEvidenceCapture(
                            audio = state.audio,
                            onAudioCaptured = viewModel::setAudio,
                            onAudioRemoved = viewModel::removeAudio,
                            onRecordingChanged = viewModel::setRecording,
                            enabled = !state.isSubmitting,
                        )
                        LcxTextField(
                            value = state.audioTranscript,
                            onValueChange = viewModel::updateAudioTranscript,
                            label = "Transcripcion o notas de audio",
                            enabled = !state.isSubmitting,
                            singleLine = false,
                            maxLines = 4,
                        )
                    }
                }
            }

            item {
                ProblemMessage(
                    message = state.message,
                    error = state.error,
                )
                Spacer(modifier = Modifier.height(LcxSpacing.sm))
                LcxButton(
                    text = "Guardar reporte",
                    onClick = viewModel::submit,
                    enabled = state.canSubmit,
                    isLoading = state.isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(96.dp))
            }
        }
    }
}

@Composable
fun DamagedClothingHistoryScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DamagedClothingHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    ProblemScaffold(
        title = "Historial ropa danada",
        onBack = onBack,
        onRefresh = viewModel::load,
        modifier = modifier.testTag(LcxTestTags.DAMAGED_CLOTHING_HISTORY_ROOT),
    ) { contentModifier ->
        LazyColumn(
            modifier = contentModifier,
            verticalArrangement = Arrangement.spacedBy(LcxSpacing.md),
        ) {
            item {
                Spacer(modifier = Modifier.height(LcxSpacing.sm))
                LcxTextField(
                    value = state.query,
                    onValueChange = viewModel::updateQuery,
                    label = "Buscar por ticket, cliente o descripcion",
                    enabled = !state.isLoading,
                )
            }

            when {
                state.isLoading -> item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                state.error != null -> item {
                    ProblemMessage(message = null, error = state.error)
                    LcxButton(
                        text = "Reintentar",
                        onClick = viewModel::load,
                        variant = ButtonVariant.Secondary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                state.filteredRecords.isEmpty() -> item {
                    EmptyDamageHistory()
                }

                else -> items(
                    items = state.filteredRecords,
                    key = { it.id },
                ) { record ->
                    DamagedClothingRecordCard(record = record)
                }
            }

            item { Spacer(modifier = Modifier.height(96.dp)) }
        }
    }
}

@Composable
private fun DamagedClothingRecordCard(record: DamagedClothingRecord) {
    val photoCount = evidencePhotoCount(record.evidence)
    val audioCount = evidenceAudioCount(record.evidence)
    LcxCard {
        Column(verticalArrangement = Arrangement.spacedBy(LcxSpacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "DMG-${record.id.take(8).uppercase()}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = formatProblemDate(record.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusPill(text = record.ticket?.ticketNumber ?: "Sin ticket")
            }
            record.ticket?.let { ticket ->
                Text(
                    text = "${ticket.customerName} - ${ticket.serviceType}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = record.damageDescription,
                style = MaterialTheme.typography.bodyMedium,
            )
            record.reportedByProfile?.fullName?.let { reporter ->
                Text(
                    text = "Reporto: $reporter",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            EvidenceCounts(photoCount = photoCount, audioCount = audioCount)
        }
    }
}

@Composable
private fun EmptyDamageHistory() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(LcxSpacing.sm),
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "No hay reportes de ropa danada",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

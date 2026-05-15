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
import androidx.compose.material.icons.filled.ReportProblem
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cleanx.lcx.core.theme.LcxSpacing
import com.cleanx.lcx.core.ui.ButtonVariant
import com.cleanx.lcx.core.ui.LcxButton
import com.cleanx.lcx.core.ui.LcxCard
import com.cleanx.lcx.core.ui.LcxTextField
import com.cleanx.lcx.core.ui.LcxTestTags

@Composable
fun IncidentsNewScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: IncidentNewViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    ProblemScaffold(
        title = "Nueva incidencia",
        onBack = onBack,
        modifier = modifier.testTag(LcxTestTags.INCIDENTS_NEW_ROOT),
    ) { contentModifier ->
        LazyColumn(
            modifier = contentModifier,
            verticalArrangement = Arrangement.spacedBy(LcxSpacing.md),
        ) {
            item {
                Spacer(modifier = Modifier.height(LcxSpacing.sm))
                LcxCard(title = "Reportar incidente") {
                    Column(verticalArrangement = Arrangement.spacedBy(LcxSpacing.md)) {
                        ProblemDropdown(
                            label = "Tipo de incidente",
                            value = state.incidentType,
                            options = incidentTypeOptions,
                            onValueChange = viewModel::updateIncidentType,
                            enabled = !state.isSubmitting,
                        )
                        ProblemDropdown(
                            label = "Severidad",
                            value = state.severity,
                            options = incidentSeverityOptions,
                            onValueChange = viewModel::updateSeverity,
                            enabled = !state.isSubmitting,
                        )
                        LcxTextField(
                            value = state.description,
                            onValueChange = viewModel::updateDescription,
                            label = "Descripcion del incidente",
                            enabled = !state.isSubmitting,
                            singleLine = false,
                            maxLines = 5,
                        )
                        LcxTextField(
                            value = state.peopleInvolved,
                            onValueChange = viewModel::updatePeopleInvolved,
                            label = "Personas involucradas",
                            enabled = !state.isSubmitting,
                        )
                        LcxTextField(
                            value = state.actionsTaken,
                            onValueChange = viewModel::updateActionsTaken,
                            label = "Acciones tomadas",
                            enabled = !state.isSubmitting,
                            singleLine = false,
                            maxLines = 4,
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
                    AudioEvidenceCapture(
                        audio = state.audio,
                        onAudioCaptured = viewModel::setAudio,
                        onAudioRemoved = viewModel::removeAudio,
                        onRecordingChanged = viewModel::setRecording,
                        enabled = !state.isSubmitting,
                    )
                }
            }

            item {
                ProblemMessage(
                    message = state.message,
                    error = state.error,
                )
                Spacer(modifier = Modifier.height(LcxSpacing.sm))
                LcxButton(
                    text = "Guardar incidencia",
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
fun IncidentsHistoryScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: IncidentHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    ProblemScaffold(
        title = "Historial incidencias",
        onBack = onBack,
        onRefresh = viewModel::load,
        modifier = modifier.testTag(LcxTestTags.INCIDENTS_HISTORY_ROOT),
    ) { contentModifier ->
        LazyColumn(
            modifier = contentModifier,
            verticalArrangement = Arrangement.spacedBy(LcxSpacing.md),
        ) {
            item {
                Spacer(modifier = Modifier.height(LcxSpacing.sm))
                LcxCard(title = "Filtros") {
                    Column(verticalArrangement = Arrangement.spacedBy(LcxSpacing.md)) {
                        LcxTextField(
                            value = state.query,
                            onValueChange = viewModel::updateQuery,
                            label = "Buscar por folio, descripcion o usuario",
                            enabled = !state.isLoading,
                        )
                        ProblemDropdown(
                            label = "Estado",
                            value = state.statusFilter,
                            options = incidentStatusOptions,
                            onValueChange = viewModel::updateStatusFilter,
                            enabled = !state.isLoading,
                        )
                        ProblemDropdown(
                            label = "Tipo",
                            value = state.typeFilter,
                            options = listOf(ProblemOption("all", "Todos")) + incidentTypeOptions,
                            onValueChange = viewModel::updateTypeFilter,
                            enabled = !state.isLoading,
                        )
                    }
                }
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
                    EmptyIncidentsHistory()
                }

                else -> items(
                    items = state.filteredRecords,
                    key = { it.id },
                ) { record ->
                    IncidentRecordCard(record = record)
                }
            }

            item { Spacer(modifier = Modifier.height(96.dp)) }
        }
    }
}

@Composable
private fun IncidentRecordCard(record: IncidentRecord) {
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
                        text = record.incidentNumber,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = formatProblemDate(record.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IncidentStatus(status = record.status)
            }
            Text(
                text = optionLabel(incidentTypeOptions, record.incidentType),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = record.description,
                style = MaterialTheme.typography.bodyMedium,
            )
            record.peopleInvolved?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = "Personas: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            record.actionsTaken?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = "Acciones: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            record.reporter?.fullName?.let { reporter ->
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
private fun IncidentStatus(status: String) {
    val label = when (status) {
        "pending" -> "Pendiente"
        "in_progress" -> "En proceso"
        "resolved" -> "Resuelto"
        else -> status
    }
    StatusPill(text = label)
}

@Composable
private fun EmptyIncidentsHistory() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(LcxSpacing.sm),
    ) {
        Icon(
            imageVector = Icons.Filled.ReportProblem,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "No hay incidencias registradas",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

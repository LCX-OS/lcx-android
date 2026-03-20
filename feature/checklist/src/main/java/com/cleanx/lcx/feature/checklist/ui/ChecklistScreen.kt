package com.cleanx.lcx.feature.checklist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import com.cleanx.lcx.core.ui.LcxTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cleanx.lcx.core.theme.LcxSpacing
import com.cleanx.lcx.core.ui.EmptyState
import com.cleanx.lcx.core.ui.ErrorState
import com.cleanx.lcx.core.ui.LcxCard
import com.cleanx.lcx.feature.checklist.data.Checklist
import com.cleanx.lcx.feature.checklist.data.ChecklistType
import com.cleanx.lcx.feature.checklist.data.resolvedType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TAB_TITLES = listOf("Entrada", "Salida", "Historial")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistScreen(
    viewModel: ChecklistViewModel,
    onNavigateToWater: (() -> Unit)? = null,
    onNavigateToCash: (() -> Unit)? = null,
    showTopBar: Boolean = true,
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        viewModel.refreshSelectedTab()
    }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshSelectedTab()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Show completed message as snackbar
    LaunchedEffect(state.completedMessage) {
        state.completedMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearCompletedMessage()
        }
    }

    Scaffold(
        topBar = {
            if (showTopBar) {
                LcxTopAppBar(
                    title = {
                        Text(
                            text = "Checklist operativo",
                            modifier = Modifier.semantics { heading() },
                        )
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Tabs
            TabRow(selectedTabIndex = state.selectedTab) {
                TAB_TITLES.forEachIndexed { index, title ->
                    Tab(
                        selected = state.selectedTab == index,
                        onClick = { viewModel.selectTab(index) },
                        text = { Text(text = title) },
                    )
                }
            }

            // Tab content
            when (state.selectedTab) {
                0 -> ChecklistContent(
                    type = ChecklistType.ENTRADA,
                    isLoading = state.isLoadingEntry,
                    checklist = state.entryChecklist,
                    items = state.entryItems,
                    progress = state.entryProgress,
                    requiredDone = state.entryRequiredDone,
                    requiredTotal = state.entryRequiredTotal,
                    canComplete = state.entryCanComplete,
                    notes = state.entryNotes,
                    error = state.entryError,
                    isCompleting = state.isCompleting,
                    onToggleItem = { viewModel.toggleItem(it, ChecklistType.ENTRADA) },
                    onNotesChanged = { viewModel.updateNotes(it, ChecklistType.ENTRADA) },
                    onComplete = { viewModel.completeChecklist(ChecklistType.ENTRADA) },
                    onRetry = { viewModel.loadEntryChecklist() },
                    onRefresh = { viewModel.loadEntryChecklist() },
                    onActionClick = { route ->
                        when (route) {
                            "water" -> onNavigateToWater?.invoke()
                            "cash" -> onNavigateToCash?.invoke()
                        }
                    },
                )

                1 -> ChecklistContent(
                    type = ChecklistType.SALIDA,
                    isLoading = state.isLoadingExit,
                    checklist = state.exitChecklist,
                    items = state.exitItems,
                    progress = state.exitProgress,
                    requiredDone = state.exitRequiredDone,
                    requiredTotal = state.exitRequiredTotal,
                    canComplete = state.exitCanComplete,
                    notes = state.exitNotes,
                    error = state.exitError,
                    isCompleting = state.isCompleting,
                    onToggleItem = { viewModel.toggleItem(it, ChecklistType.SALIDA) },
                    onNotesChanged = { viewModel.updateNotes(it, ChecklistType.SALIDA) },
                    onComplete = { viewModel.completeChecklist(ChecklistType.SALIDA) },
                    onRetry = { viewModel.loadExitChecklist() },
                    onRefresh = { viewModel.loadExitChecklist() },
                    onActionClick = { route ->
                        when (route) {
                            "water" -> onNavigateToWater?.invoke()
                            "cash" -> onNavigateToCash?.invoke()
                        }
                    },
                )

                2 -> HistoryContent(
                    isLoading = state.isLoadingHistory,
                    history = state.history,
                    error = state.historyError,
                    onRetry = { viewModel.loadHistory() },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// History tab
// ---------------------------------------------------------------------------

@Composable
private fun HistoryContent(
    isLoading: Boolean,
    history: List<Checklist>,
    error: String?,
    onRetry: () -> Unit,
) {
    when {
        isLoading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        error != null -> {
            ErrorState(
                message = error,
                onRetry = onRetry,
            )
        }

        history.isEmpty() -> {
            EmptyState(
                title = "Sin historial",
                description = "Aun no hay checklists completados",
            )
        }

        else -> {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = LcxSpacing.md),
                verticalArrangement = Arrangement.spacedBy(LcxSpacing.sm),
            ) {
                item { Spacer(modifier = Modifier.height(LcxSpacing.sm)) }
                items(history, key = { it.id ?: it.date }) { checklist ->
                    HistoryItem(checklist = checklist)
                }
                item { Spacer(modifier = Modifier.height(LcxSpacing.xl)) }
            }
        }
    }
}

@Composable
private fun HistoryItem(checklist: Checklist) {
    val resolvedType = checklist.resolvedType()
    val typeLabel = when (resolvedType) {
        ChecklistType.ENTRADA -> "Entrada"
        ChecklistType.SALIDA -> "Salida"
    }
    val typeColor = when (resolvedType) {
        ChecklistType.ENTRADA -> Color(0xFF1A73E8) // Blue
        ChecklistType.SALIDA -> Color(0xFFE8710A) // Orange
    }

    LcxCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Type badge + date
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(typeColor.copy(alpha = 0.15f))
                            .padding(horizontal = LcxSpacing.sm, vertical = 2.dp),
                    ) {
                        Text(
                            text = typeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = typeColor,
                        )
                    }
                    Spacer(modifier = Modifier.width(LcxSpacing.sm))
                    val dateText = try {
                        val date = LocalDate.parse(checklist.date)
                        date.format(
                            DateTimeFormatter.ofPattern(
                                "d MMM yyyy",
                                Locale.forLanguageTag("es"),
                            )
                        )
                    } catch (_: Exception) {
                        checklist.date
                    }
                    Text(
                        text = dateText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                // Completed info
                if (checklist.completedAt != null) {
                    Spacer(modifier = Modifier.height(LcxSpacing.xs))
                    val timeText = try {
                        val dt = java.time.OffsetDateTime.parse(checklist.completedAt)
                        "Completado a las ${dt.format(DateTimeFormatter.ofPattern("HH:mm"))}"
                    } catch (_: Exception) {
                        "Completado"
                    }
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Notes
                if (!checklist.completionNotes.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(LcxSpacing.xs))
                    Text(
                        text = checklist.completionNotes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
            }

            // Status
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF34A853).copy(alpha = 0.15f))
                    .padding(horizontal = LcxSpacing.sm, vertical = LcxSpacing.xs),
            ) {
                Text(
                    text = "Completado",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF34A853),
                )
            }
        }
    }
}

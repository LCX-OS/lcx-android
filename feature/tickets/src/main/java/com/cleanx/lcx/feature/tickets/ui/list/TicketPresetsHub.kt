package com.cleanx.lcx.feature.tickets.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.cleanx.lcx.core.ui.LcxTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.cleanx.lcx.core.theme.LcxSpacing

private data class PresetItem(
    val label: String,
    val description: String,
    val icon: ImageVector,
    val preset: String,
)

private val presetItems = listOf(
    PresetItem(
        label = "Activos",
        description = "Recibidos y en proceso",
        icon = Icons.Filled.PlayArrow,
        preset = "active",
    ),
    PresetItem(
        label = "Listos",
        description = "Listos para recoger",
        icon = Icons.Filled.CheckCircle,
        preset = "ready",
    ),
    PresetItem(
        label = "Completados",
        description = "Entregados",
        icon = Icons.Filled.Done,
        preset = "completed",
    ),
    PresetItem(
        label = "Todos",
        description = "Todos los tickets",
        icon = Icons.AutoMirrored.Filled.List,
        preset = "all",
    ),
)

/**
 * Hub / landing page for ticket preset navigation.
 * Shows 4 preset options that navigate to filtered ticket lists.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketPresetsHub(
    onPresetSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            LcxTopAppBar(
                title = {
                    Text(
                        "Filtros de Tickets",
                        modifier = Modifier.semantics { heading() },
                    )
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            verticalArrangement = Arrangement.spacedBy(LcxSpacing.xs),
        ) {
            items(presetItems, key = { it.preset }) { item ->
                ListItem(
                    headlineContent = {
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    },
                    supportingContent = {
                        Text(
                            text = item.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                        )
                    },
                    modifier = Modifier.clickable { onPresetSelected(item.preset) },
                )
            }
        }
    }
}

package com.cleanx.lcx.feature.water.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import com.cleanx.lcx.core.ui.LcxTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.cleanx.lcx.core.ui.ErrorState
import com.cleanx.lcx.core.ui.LoadingOverlay

private val TABS = listOf("Nivel Actual", "Historial")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaterScreen(
    viewModel: WaterViewModel,
    modifier: Modifier = Modifier,
    showTopBar: Boolean = true,
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            if (showTopBar) {
                LcxTopAppBar(
                    title = {
                        Text(
                            text = "Nivel de Agua",
                            modifier = Modifier.semantics { heading() },
                        )
                    },
                )
            }
        },
        modifier = modifier,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Tabs
            TabRow(selectedTabIndex = state.selectedTab) {
                TABS.forEachIndexed { index, title ->
                    Tab(
                        selected = state.selectedTab == index,
                        onClick = { viewModel.selectTab(index) },
                        text = {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        },
                    )
                }
            }

            // Content
            when {
                state.isLoading && state.selectedTab == 0 -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        LoadingOverlay(message = "Cargando nivel de agua...")
                    }
                }

                state.error != null && state.selectedTab == 0 -> {
                    ErrorState(
                        message = state.error!!,
                        onRetry = { viewModel.loadCurrentLevel() },
                    )
                }

                else -> {
                    when (state.selectedTab) {
                        0 -> WaterLevelTab(
                            state = state,
                            onSliderChange = viewModel::onSliderChange,
                            onPercentageTextChange = viewModel::onPercentageTextChange,
                            onLitersTextChange = viewModel::onLitersTextChange,
                            onSaveLevel = viewModel::saveLevel,
                            onSelectProvider = viewModel::selectProvider,
                            onOrderWater = viewModel::orderWater,
                        )

                        1 -> WaterHistoryTab(
                            state = state,
                            onRetry = viewModel::loadHistory,
                        )
                    }
                }
            }
        }
    }
}

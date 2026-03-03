package com.cleanx.lcx.feature.cash.ui

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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.cleanx.lcx.core.ui.ErrorState
import com.cleanx.lcx.core.ui.LoadingOverlay

private val TABS = listOf("Registrar", "Historial")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashScreen(
    viewModel: CashViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Caja",
                        modifier = Modifier.semantics { heading() },
                    )
                },
            )
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
                state.isLoadingSummary && state.selectedTab == 0 -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        LoadingOverlay(message = "Cargando resumen de caja...")
                    }
                }

                state.summaryError != null && state.selectedTab == 0 -> {
                    ErrorState(
                        message = state.summaryError!!,
                        onRetry = { viewModel.loadSummary() },
                    )
                }

                else -> {
                    when (state.selectedTab) {
                        0 -> CashRegisterTab(
                            state = state,
                            onSelectType = viewModel::selectType,
                            onBillCountChange = viewModel::onBillCountChange,
                            onCoinCountChange = viewModel::onCoinCountChange,
                            onNotesChange = viewModel::onNotesChange,
                            onTotalSalesForDayChange = viewModel::onTotalSalesForDayChange,
                            onExpenseAmountChange = viewModel::onExpenseAmountChange,
                            onClear = viewModel::clearForm,
                            onSubmit = viewModel::submit,
                            onClearSubmitSuccess = viewModel::clearSubmitSuccess,
                            onClearSubmitError = viewModel::clearSubmitError,
                        )

                        1 -> CashHistoryTab(
                            state = state,
                            onRetry = viewModel::loadHistory,
                        )
                    }
                }
            }
        }
    }
}

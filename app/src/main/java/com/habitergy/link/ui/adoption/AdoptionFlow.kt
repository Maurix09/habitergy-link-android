package com.habitergy.link.ui.adoption

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun AdoptionFlow(
    viewModel: AdoptionViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    when (state.currentStep) {
        1 -> Step1IdentifyScreen(
            state = state,
            onDeviceCodeChange = viewModel::onDeviceCodeChange,
            onDeviceCodeComplete = viewModel::onDeviceCodeComplete,
            onScanQrClick = viewModel::onScanQrClick,
            onProceedWithoutCode = viewModel::proceedWithoutKnownCode,
            onNext = viewModel::proceedToStep2,
            onBack = { /* primer paso: sin acción en demo */ },
        )
        2 -> Step2BleScanScreen(
            state = state,
            onCheckReadiness = viewModel::refreshBleReadiness,
            onRetry = viewModel::retryBleScan,
            onSelectDevice = viewModel::selectDevice,
            onBack = viewModel::goBackToStep1,
        )
        else -> Step1IdentifyScreen(
            state = state.copy(currentStep = 1),
            onDeviceCodeChange = viewModel::onDeviceCodeChange,
            onDeviceCodeComplete = viewModel::onDeviceCodeComplete,
            onScanQrClick = viewModel::onScanQrClick,
            onProceedWithoutCode = viewModel::proceedWithoutKnownCode,
            onNext = viewModel::proceedToStep2,
            onBack = {},
        )
    }
}

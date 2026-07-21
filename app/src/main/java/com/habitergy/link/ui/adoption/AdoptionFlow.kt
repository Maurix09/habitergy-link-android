package com.habitergy.link.ui.adoption

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.habitergy.link.domain.model.AdoptionSessionContext

@Composable
fun AdoptionFlow(
    viewModel: AdoptionViewModel = viewModel(),
    sessionContext: AdoptionSessionContext,
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
            siteName = sessionContext.site?.name,
        )
        2 -> Step2BleScanScreen(
            state = state,
            onCheckReadiness = viewModel::refreshBleReadiness,
            onRetry = viewModel::retryBleScan,
            onSelectDevice = viewModel::selectDevice,
            onNext = viewModel::proceedToStep3,
            onBack = viewModel::goBackToStep1,
        )
        3 -> Step3WifiScreen(
            state = state,
            onSsidChange = viewModel::onWifiSsidChange,
            onPasswordChange = viewModel::onWifiPasswordChange,
            onTogglePasswordVisibility = viewModel::toggleWifiPasswordVisibility,
            onOpenNetworkSheet = viewModel::openWifiNetworkSheet,
            onDismissNetworkSheet = viewModel::dismissWifiNetworkSheet,
            onSelectNetwork = viewModel::selectWifiNetwork,
            onRefreshCurrentSsid = viewModel::refreshCurrentWifiSsid,
            onRefreshWifiScan = viewModel::refreshWifiScanReadiness,
            onRetryWifiScan = viewModel::retryWifiScan,
            onContinue = viewModel::proceedFromStep3,
            onBack = viewModel::goBackToStep2,
        )
        4 -> Step4ConfigureScreen(
            state = state,
            onStartProvisioning = viewModel::startStep4Provisioning,
            onRetry = viewModel::retryStep4Provisioning,
            onBack = viewModel::goBackToStep3,
        )
        5 -> Step5WaitingScreen(
            state = state,
            onStartWaiting = viewModel::startStep5OnlineWait,
            onRetry = viewModel::retryStep5OnlineWait,
            onBack = viewModel::goBackToStep4,
        )
        6 -> Step6SuccessScreen(
            state = state,
            siteName = sessionContext.site?.name,
            onRetryReturn = viewModel::retryReturnToManager,
        )
        else -> Step1IdentifyScreen(
            state = state.copy(currentStep = 1),
            onDeviceCodeChange = viewModel::onDeviceCodeChange,
            onDeviceCodeComplete = viewModel::onDeviceCodeComplete,
            onScanQrClick = viewModel::onScanQrClick,
            onProceedWithoutCode = viewModel::proceedWithoutKnownCode,
            onNext = viewModel::proceedToStep2,
            onBack = {},
            siteName = sessionContext.site?.name,
        )
    }
}

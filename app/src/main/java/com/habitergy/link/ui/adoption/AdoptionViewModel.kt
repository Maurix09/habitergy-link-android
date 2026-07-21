package com.habitergy.link.ui.adoption

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.habitergy.link.data.AdoptionLookupResult
import com.habitergy.link.data.AdoptionOnlineResult
import com.habitergy.link.data.AdoptionProvisionResult
import com.habitergy.link.data.AdoptionRepository
import com.habitergy.link.data.AdoptionSessionCompleteResult
import com.habitergy.link.data.AdoptionSessionContextResult
import com.habitergy.link.data.ble.ShellyBleRpcClient
import com.habitergy.link.data.ble.ShellyBleRpcException
import com.habitergy.link.data.ble.ShellyDeviceProvisioner
import com.habitergy.link.data.ble.BleAdvertisement
import com.habitergy.link.data.ble.BlePermissions
import com.habitergy.link.data.ble.ShellyBleScanner
import com.habitergy.link.data.ble.buildShellyDisplayName
import com.habitergy.link.data.ble.formatMac
import com.habitergy.link.data.ble.normalizeMac
import com.habitergy.link.data.wifi.WifiNetworkHelper
import com.habitergy.link.data.wifi.WifiPermissions
import com.habitergy.link.data.wifi.WifiScanTemporarilyUnavailableException
import com.habitergy.link.domain.DeviceCode
import com.habitergy.link.domain.AdoptionLaunchRequest
import com.habitergy.link.domain.model.AdoptionEntryState
import com.habitergy.link.domain.model.AdoptionEvent
import com.habitergy.link.domain.model.AdoptionUiState
import com.habitergy.link.domain.model.BleScanPhase
import com.habitergy.link.domain.model.CompletionPhase
import com.habitergy.link.domain.model.DEVICE_CODE_LENGTH
import com.habitergy.link.domain.model.DeviceLookupState
import com.habitergy.link.domain.model.DiscoveredBleDevice
import com.habitergy.link.domain.model.IdentificationMode
import com.habitergy.link.domain.model.OnlineWaitPhase
import com.habitergy.link.domain.model.ProvisionPhase
import com.habitergy.link.domain.model.ResolvedDevice
import com.habitergy.link.domain.model.ScannedShellyDevice
import com.habitergy.link.domain.model.ShellyProvisionStep
import com.habitergy.link.domain.model.Step4Error
import com.habitergy.link.domain.model.Step4ProvisionException
import com.habitergy.link.domain.model.UNKNOWN_DEVICE_CODE
import com.habitergy.link.domain.model.WifiScanPhase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import java.time.Instant

class AdoptionViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val repository: AdoptionRepository = AdoptionRepository()
    private val bleScanner: ShellyBleScanner = ShellyBleScanner(application)
    private val wifiHelper: WifiNetworkHelper = WifiNetworkHelper(application)
    private val shellyRpcClient: ShellyBleRpcClient = ShellyBleRpcClient(application)
    private val shellyProvisioner: ShellyDeviceProvisioner = ShellyDeviceProvisioner(shellyRpcClient)

    private val _uiState = MutableStateFlow(AdoptionUiState())
    val uiState: StateFlow<AdoptionUiState> = _uiState.asStateFlow()
    private val _entryState = MutableStateFlow<AdoptionEntryState>(AdoptionEntryState.NoSession)
    val entryState: StateFlow<AdoptionEntryState> = _entryState.asStateFlow()
    private val eventChannel = Channel<AdoptionEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    private var sessionToken: String? = null
    private var launchGeneration: Long = 0
    private var launchInitialized: Boolean = false
    private var sessionLoadJob: Job? = null
    private var lookupJob: Job? = null
    private var activeLookupSuffix: String? = null
    private var scanJob: Job? = null
    private var wifiScanJob: Job? = null
    private var wifiRetryCooldownJob: Job? = null
    private var provisionJob: Job? = null
    private var onlinePollJob: Job? = null
    private var completionJob: Job? = null

    /**
     * Recibe el launch de la Activity. Un intent nuevo fuerza un wizard limpio;
     * una recreación de configuración conserva el ViewModel ya inicializado.
     */
    fun handleLaunch(request: AdoptionLaunchRequest, forceReset: Boolean) {
        if (launchInitialized && !forceReset) return
        launchInitialized = true
        launchGeneration += 1
        resetActiveWork()
        _uiState.value = AdoptionUiState()
        sessionToken = null

        when (request) {
            AdoptionLaunchRequest.NoSession -> {
                _entryState.value = AdoptionEntryState.NoSession
            }
            AdoptionLaunchRequest.Invalid -> {
                _entryState.value = AdoptionEntryState.Invalid
            }
            is AdoptionLaunchRequest.Session -> {
                sessionToken = request.token
                loadSessionContext(request.token, launchGeneration)
            }
        }
    }

    fun retrySessionContext() {
        val token = sessionToken ?: return
        loadSessionContext(token, launchGeneration)
    }

    private fun loadSessionContext(token: String, generation: Long) {
        sessionLoadJob?.cancel()
        _entryState.value = AdoptionEntryState.Loading
        sessionLoadJob = viewModelScope.launch {
            when (val result = repository.getSessionContext(token)) {
                is AdoptionSessionContextResult.Success -> {
                    if (!isCurrentSession(token, generation)) return@launch
                    _entryState.value = if (isExpired(result.context.expiresAt)) {
                        AdoptionEntryState.Expired
                    } else {
                        AdoptionEntryState.Ready(result.context)
                    }
                }
                AdoptionSessionContextResult.Invalid -> {
                    if (isCurrentSession(token, generation)) {
                        _entryState.value = AdoptionEntryState.Invalid
                    }
                }
                AdoptionSessionContextResult.Expired -> {
                    if (isCurrentSession(token, generation)) {
                        _entryState.value = AdoptionEntryState.Expired
                    }
                }
                AdoptionSessionContextResult.NetworkError -> {
                    if (isCurrentSession(token, generation)) {
                        _entryState.value = AdoptionEntryState.NetworkError
                    }
                }
            }
            sessionLoadJob = null
        }
    }

    private fun isCurrentSession(token: String, generation: Long): Boolean =
        sessionToken == token && launchGeneration == generation

    private fun isExpired(expiresAt: String): Boolean =
        runCatching { Instant.parse(expiresAt).isBefore(Instant.now()) }.getOrDefault(false)

    /**
     * Mantiene una única fuente de verdad para el código ingresado. Si el cambio
     * deja el sufijo completo (5 caracteres), vuelve a validar y relanza el
     * lookup aunque el usuario haya corregido un recuadro intermedio.
     */
    fun onDeviceCodeChange(value: String) {
        updateDeviceCode(value)
    }

    fun onDeviceCodeComplete(value: String) {
        updateDeviceCode(value)
    }

    fun onScanQrClick() {
        // Placeholder hasta integrar CameraX + lector QR (snackbar "Coming soon" en la UI).
    }

    fun proceedWithoutKnownCode() {
        cancelLookup()
        _uiState.update {
            it.copy(
                deviceCodeInput = UNKNOWN_DEVICE_CODE,
                identificationMode = IdentificationMode.NoCode,
                resolvedDevice = null,
                lookupState = DeviceLookupState.Idle,
            )
        }
        navigateToStep2()
    }

    fun proceedToStep2() {
        if (_uiState.value.canProceedFromStep1) {
            navigateToStep2()
        }
    }

    fun proceedToStep3() {
        if (!_uiState.value.canProceedFromStep2) return
        cancelScan()
        cancelWifiRetryCooldown()
        // Navegar primero: leer el SSID nunca debe bloquear ni tumbar el paso 3.
        _uiState.update {
            it.copy(
                currentStep = 3,
                wifiSsidTouched = false,
                showWifiNetworkSheet = false,
                wifiScanPhase = WifiScanPhase.Idle,
                nearbyWifiNetworks = emptyList(),
                wifiScanErrorMessage = null,
                wifiRetryEnabled = true,
            )
        }
        refreshCurrentWifiSsid()
    }

    /**
     * Intenta completar el SSID con la red actual del teléfono.
     * Fallos de permiso / SecurityException se ignoran (campo queda vacío).
     */
    fun refreshCurrentWifiSsid() {
        viewModelScope.launch {
            val ssid = runCatching { wifiHelper.getCurrentSsid() }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: return@launch
            _uiState.update { state ->
                if (state.currentStep != 3) return@update state
                // No pisar si el usuario ya empezó a escribir.
                if (state.wifiSsid.isNotBlank() && state.wifiSsidTouched) return@update state
                if (state.wifiSsid.isNotBlank()) return@update state
                state.copy(wifiSsid = ssid)
            }
        }
    }

    fun proceedFromStep3() {
        _uiState.update { it.copy(wifiSsidTouched = true) }
        val state = _uiState.value
        if (!state.canProceedFromStep3) return
        cancelOnlinePoll()
        shellyRpcClient.disconnect()
        _uiState.update {
            it.copy(
                currentStep = 4,
                provisionPhase = ProvisionPhase.Idle,
                shellyProvisionStep = null,
                provisionErrorCode = null,
                provisionErrorMessage = null,
                onlineWaitPhase = OnlineWaitPhase.Idle,
                onlineWaitErrorMessage = null,
                completionPhase = CompletionPhase.Idle,
                completionErrorMessage = null,
                returnNavigationErrorMessage = null,
            )
        }
    }

    fun startStep4Provisioning() {
        val state = _uiState.value
        if (state.currentStep != 4) return
        if (state.provisionPhase != ProvisionPhase.Idle &&
            state.provisionPhase != ProvisionPhase.Error
        ) {
            return
        }
        if (!state.canStartStep4) {
            failProvision(
                Step4Error.MISSING_PREREQUISITES,
                "Necesitamos un código de controlador válido y una conexión Bluetooth.",
            )
            return
        }
        runStep4Provisioning()
    }

    fun retryStep4Provisioning() {
        if (_uiState.value.currentStep != 4) return
        shellyRpcClient.disconnect()
        _uiState.update {
            it.copy(
                provisionPhase = ProvisionPhase.Idle,
                shellyProvisionStep = null,
                provisionErrorCode = null,
                provisionErrorMessage = null,
            )
        }
        runStep4Provisioning()
    }

    fun goBackToStep3() {
        cancelProvision()
        shellyRpcClient.disconnect()
        _uiState.update {
            it.copy(
                currentStep = 3,
                provisionPhase = ProvisionPhase.Idle,
                shellyProvisionStep = null,
                provisionErrorCode = null,
                provisionErrorMessage = null,
            )
        }
    }

    fun startStep5OnlineWait() {
        val state = _uiState.value
        if (state.currentStep != 5) return
        if (state.onlineWaitPhase == OnlineWaitPhase.Online) return
        if (state.onlineWaitPhase == OnlineWaitPhase.Waiting) return
        runOnlinePoll()
    }

    fun retryStep5OnlineWait() {
        if (_uiState.value.currentStep != 5) return
        if (_uiState.value.completionPhase == CompletionPhase.Error &&
            _uiState.value.onlineWaitPhase == OnlineWaitPhase.Online
        ) {
            completeAdoptionSession()
        } else {
            runOnlinePoll()
        }
    }

    fun goBackToStep4() {
        if (_uiState.value.completionPhase != CompletionPhase.Idle) return
        cancelOnlinePoll()
        _uiState.update {
            it.copy(
                currentStep = 4,
                provisionPhase = ProvisionPhase.Done,
                onlineWaitPhase = OnlineWaitPhase.Idle,
                onlineWaitErrorMessage = null,
                completionPhase = CompletionPhase.Idle,
                completionErrorMessage = null,
            )
        }
    }

    fun onReturnNavigationFailed() {
        _uiState.update {
            it.copy(
                returnNavigationErrorMessage =
                    "No encontramos una aplicación para volver a Habitergy Manager.",
            )
        }
    }

    fun retryReturnToManager() {
        val state = _entryState.value as? AdoptionEntryState.Ready ?: return
        if (_uiState.value.completionPhase != CompletionPhase.Completed) return
        _uiState.update { it.copy(returnNavigationErrorMessage = null) }
        eventChannel.trySend(AdoptionEvent.ReturnToManager(state.context.sessionId))
    }

    private fun runStep4Provisioning() {
        cancelProvision()
        val state = _uiState.value
        val resolved = state.resolvedDevice ?: return
        val selected = state.selectedDevice ?: return
        val shortCode = state.shortCode ?: return

        provisionJob = viewModelScope.launch {
            try {
                _uiState.update {
                    it.copy(
                        provisionPhase = ProvisionPhase.ProvisioningBroker,
                        provisionErrorCode = null,
                        provisionErrorMessage = null,
                        shellyProvisionStep = null,
                    )
                }

                when (val brokerResult = repository.provision(resolved.deviceCode)) {
                    is AdoptionProvisionResult.Success -> Unit
                    is AdoptionProvisionResult.NotFound -> {
                        failProvision(
                            Step4Error.BROKER_NOT_FOUND,
                            "No encontramos el controlador en el sistema.",
                        )
                        return@launch
                    }
                    is AdoptionProvisionResult.Conflict -> {
                        failProvision(
                            Step4Error.BROKER_CONFLICT,
                            "Este controlador ya no está disponible para adoptar.",
                        )
                        return@launch
                    }
                    is AdoptionProvisionResult.ApiError -> {
                        failProvision(Step4Error.BROKER_API, brokerResult.message)
                        return@launch
                    }
                    AdoptionProvisionResult.NetworkError -> {
                        failProvision(
                            Step4Error.BROKER_NETWORK,
                            "No pudimos contactar al servidor. Revisá tu conexión a internet.",
                        )
                        return@launch
                    }
                }

                _uiState.update { it.copy(provisionPhase = ProvisionPhase.ConnectingBle) }
                try {
                    shellyRpcClient.connect(selected.id)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    throw Step4ProvisionException(
                        error = Step4Error.BLE_CONNECT,
                        detail = error.message
                            ?: "No se pudo conectar por Bluetooth al controlador.",
                        cause = error,
                    )
                }

                _uiState.update { it.copy(provisionPhase = ProvisionPhase.ConfiguringShelly) }
                shellyProvisioner.configure(
                    shortCode = shortCode,
                    wifiSsid = state.wifiSsid.trim(),
                    wifiPassword = state.wifiPassword,
                    macAddress = selected.macAddress,
                ) { step ->
                    _uiState.update { it.copy(shellyProvisionStep = step) }
                }

                _uiState.update { it.copy(provisionPhase = ProvisionPhase.Rebooting) }
                shellyRpcClient.disconnect()

                _uiState.update {
                    it.copy(
                        provisionPhase = ProvisionPhase.Done,
                        currentStep = 5,
                        onlineWaitPhase = OnlineWaitPhase.Idle,
                    )
                }
                runOnlinePoll()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Step4ProvisionException) {
                failProvision(error.error, error.detail)
            } catch (error: ShellyBleRpcException) {
                failProvision(
                    Step4Error.UNKNOWN,
                    error.message ?: "Error de comunicación Bluetooth con el controlador.",
                )
            } catch (error: Exception) {
                failProvision(
                    Step4Error.UNKNOWN,
                    error.message ?: "No pudimos configurar el controlador. Intentá de nuevo.",
                )
            } finally {
                shellyRpcClient.disconnect()
            }
        }
    }

    private fun failProvision(error: Step4Error, detail: String) {
        _uiState.update {
            it.copy(
                provisionPhase = ProvisionPhase.Error,
                provisionErrorCode = error.code,
                provisionErrorMessage = error.formatMessage(detail),
            )
        }
    }

    private fun runOnlinePoll() {
        cancelOnlinePoll()
        val deviceCode = _uiState.value.resolvedDevice?.deviceCode ?: return

        _uiState.update {
            it.copy(
                onlineWaitPhase = OnlineWaitPhase.Waiting,
                onlineWaitErrorMessage = null,
            )
        }

        onlinePollJob = viewModelScope.launch {
            val deadline = System.currentTimeMillis() + ONLINE_WAIT_TIMEOUT_MS
            while (System.currentTimeMillis() < deadline) {
                when (val result = repository.getOnlineStatus(deviceCode)) {
                    is AdoptionOnlineResult.Status -> {
                        if (result.isOnline) {
                            _uiState.update {
                                it.copy(
                                    onlineWaitPhase = OnlineWaitPhase.Online,
                                    completionPhase = CompletionPhase.Completing,
                                    completionErrorMessage = null,
                                )
                            }
                            completeAdoptionSession()
                            return@launch
                        }
                    }
                    AdoptionOnlineResult.NotFound -> {
                        failOnlineWait("No encontramos el controlador en el sistema.")
                        return@launch
                    }
                    AdoptionOnlineResult.NetworkError -> {
                        // Seguir intentando hasta timeout.
                    }
                }
                delay(ONLINE_POLL_INTERVAL_MS)
            }
            _uiState.update {
                it.copy(onlineWaitPhase = OnlineWaitPhase.Timeout)
            }
        }
    }

    private fun completeAdoptionSession() {
        completionJob?.cancel()
        val token = sessionToken
        val deviceCode = _uiState.value.resolvedDevice?.deviceCode
        val context = (_entryState.value as? AdoptionEntryState.Ready)?.context
        if (token == null || deviceCode == null || context == null) {
            failCompletion("La sesión de Manager ya no está disponible. Volvé a iniciar la adopción.")
            return
        }

        _uiState.update {
            it.copy(
                completionPhase = CompletionPhase.Completing,
                completionErrorMessage = null,
                returnNavigationErrorMessage = null,
            )
        }
        val generation = launchGeneration
        completionJob = viewModelScope.launch {
            when (val result = repository.completeSession(token, deviceCode)) {
                is AdoptionSessionCompleteResult.Success -> {
                    if (!isCurrentSession(token, generation)) return@launch
                    if (result.sessionId != context.sessionId || result.deviceCode != deviceCode) {
                        failCompletion("El servidor devolvió una asignación que no coincide con esta sesión.")
                        return@launch
                    }
                    _uiState.update {
                        it.copy(
                            currentStep = 6,
                            completionPhase = CompletionPhase.Completed,
                            completionErrorMessage = null,
                        )
                    }
                    delay(SUCCESS_DISPLAY_MS)
                    if (isCurrentSession(token, generation)) {
                        eventChannel.send(AdoptionEvent.ReturnToManager(result.sessionId))
                    }
                }
                AdoptionSessionCompleteResult.Invalid ->
                    failCompletion("La sesión de adopción ya no es válida.")
                AdoptionSessionCompleteResult.Expired ->
                    failCompletion("La sesión de adopción expiró. Iniciá una nueva desde Manager.")
                is AdoptionSessionCompleteResult.ApiError ->
                    failCompletion(result.message)
                AdoptionSessionCompleteResult.NetworkError ->
                    failCompletion(
                        "No pudimos completar la asignación. Revisá tu conexión e intentá de nuevo.",
                    )
            }
            completionJob = null
        }
    }

    private fun failCompletion(message: String) {
        _uiState.update {
            it.copy(
                completionPhase = CompletionPhase.Error,
                completionErrorMessage = message,
            )
        }
    }

    private fun failOnlineWait(message: String) {
        _uiState.update {
            it.copy(
                onlineWaitPhase = OnlineWaitPhase.Error,
                onlineWaitErrorMessage = message,
            )
        }
    }

    private fun cancelProvision() {
        provisionJob?.cancel()
        provisionJob = null
    }

    private fun cancelOnlinePoll() {
        onlinePollJob?.cancel()
        onlinePollJob = null
    }

    private fun cancelCompletion() {
        completionJob?.cancel()
        completionJob = null
    }

    fun onWifiSsidChange(value: String) {
        _uiState.update {
            it.copy(
                wifiSsid = value,
                wifiSsidTouched = true,
            )
        }
    }

    fun onWifiPasswordChange(value: String) {
        _uiState.update { it.copy(wifiPassword = value) }
    }

    fun toggleWifiPasswordVisibility() {
        _uiState.update { it.copy(wifiPasswordVisible = !it.wifiPasswordVisible) }
    }

    fun openWifiNetworkSheet() {
        _uiState.update {
            it.copy(
                showWifiNetworkSheet = true,
                wifiScanPhase = WifiScanPhase.Idle,
                nearbyWifiNetworks = emptyList(),
                wifiScanErrorMessage = null,
            )
        }
        refreshWifiScanReadiness()
    }

    fun dismissWifiNetworkSheet() {
        cancelWifiScan()
        _uiState.update {
            it.copy(
                showWifiNetworkSheet = false,
                wifiScanPhase = WifiScanPhase.Idle,
                wifiScanErrorMessage = null,
            )
        }
    }

    fun selectWifiNetwork(ssid: String) {
        cancelWifiScan()
        _uiState.update {
            it.copy(
                wifiSsid = ssid,
                wifiSsidTouched = true,
                showWifiNetworkSheet = false,
                wifiScanPhase = WifiScanPhase.Idle,
                wifiScanErrorMessage = null,
            )
        }
    }

    fun refreshWifiScanReadiness() {
        if (!_uiState.value.showWifiNetworkSheet) return
        if (!_uiState.value.wifiRetryEnabled) {
            _uiState.update {
                it.copy(
                    wifiScanPhase = WifiScanPhase.Error,
                    wifiScanErrorMessage = WIFI_THROTTLED_MESSAGE,
                )
            }
            return
        }
        val app = getApplication<Application>()
        when {
            !WifiPermissions.allGranted(app) ->
                _uiState.update { it.copy(wifiScanPhase = WifiScanPhase.PermissionRequired) }
            WifiPermissions.isLocationRequiredForScan() &&
                !WifiPermissions.isLocationEnabled(app) ->
                _uiState.update { it.copy(wifiScanPhase = WifiScanPhase.LocationOff) }
            !wifiHelper.isWifiEnabled() ->
                _uiState.update { it.copy(wifiScanPhase = WifiScanPhase.WifiOff) }
            else -> startWifiScan()
        }
    }

    fun retryWifiScan() {
        if (!_uiState.value.wifiRetryEnabled) return
        refreshWifiScanReadiness()
    }

    private fun startWifiScan() {
        cancelWifiScan()
        _uiState.update {
            it.copy(
                wifiScanPhase = WifiScanPhase.Scanning,
                nearbyWifiNetworks = emptyList(),
                wifiScanErrorMessage = null,
            )
        }
        wifiScanJob = viewModelScope.launch {
            val result = wifiHelper.scanNearbyNetworks()
            result.fold(
                onSuccess = { networks ->
                    _uiState.update {
                        it.copy(
                            nearbyWifiNetworks = networks,
                            wifiScanPhase = if (networks.isEmpty()) {
                                WifiScanPhase.Empty
                            } else {
                                WifiScanPhase.Results
                            },
                        )
                    }
                },
                onFailure = { error ->
                    when {
                        error.message == "WIFI_OFF" ->
                            _uiState.update { it.copy(wifiScanPhase = WifiScanPhase.WifiOff) }
                        error is SecurityException ->
                            _uiState.update {
                                it.copy(
                                    wifiScanPhase = WifiScanPhase.PermissionRequired,
                                    wifiScanErrorMessage = null,
                                )
                            }
                        error is WifiScanTemporarilyUnavailableException -> {
                            startWifiRetryCooldown()
                            _uiState.update {
                                it.copy(
                                    wifiScanPhase = WifiScanPhase.Error,
                                    wifiScanErrorMessage = error.message
                                        ?: WIFI_THROTTLED_MESSAGE,
                                    wifiRetryEnabled = false,
                                )
                            }
                        }
                        else ->
                            _uiState.update {
                                it.copy(
                                    wifiScanPhase = WifiScanPhase.Error,
                                    wifiScanErrorMessage = error.message
                                        ?: "No pudimos buscar redes WiFi. Intentá de nuevo.",
                                )
                            }
                    }
                },
            )
        }
    }

    private fun updateDeviceCode(value: String) {
        val sanitized = DeviceCode.normalizeSuffix(value).take(DEVICE_CODE_LENGTH)
        cancelLookup()

        _uiState.update {
            it.copy(
                deviceCodeInput = sanitized,
                identificationMode = IdentificationMode.WithCode,
                resolvedDevice = null,
                lookupState = DeviceLookupState.Idle,
            )
        }

        if (sanitized.length == DEVICE_CODE_LENGTH && sanitized != UNKNOWN_DEVICE_CODE) {
            resolveDeviceCode(sanitized)
        }
    }

    private fun resolveDeviceCode(suffix: String) {
        if (!DeviceCode.isValidSuffix(suffix)) {
            _uiState.update {
                it.copy(lookupState = DeviceLookupState.Invalid, resolvedDevice = null)
            }
            return
        }

        val fullCode = DeviceCode.fullCode(suffix)
        activeLookupSuffix = suffix

        lookupJob = viewModelScope.launch {
            _uiState.update { it.copy(lookupState = DeviceLookupState.Looking, resolvedDevice = null) }

            val result = repository.lookup(fullCode)
            if (!shouldApplyLookupResult(suffix)) return@launch

            when (result) {
                is AdoptionLookupResult.Found -> {
                    when (result.status) {
                        STATUS_AVAILABLE -> _uiState.update {
                            it.copy(
                                lookupState = DeviceLookupState.Available,
                                resolvedDevice = ResolvedDevice(
                                    deviceCode = result.deviceCode,
                                    macAddress = result.macAddress,
                                    model = result.model,
                                ),
                            )
                        }
                        STATUS_ASSIGNED -> _uiState.update {
                            it.copy(lookupState = DeviceLookupState.Assigned, resolvedDevice = null)
                        }
                        else -> _uiState.update {
                            it.copy(lookupState = DeviceLookupState.Unavailable, resolvedDevice = null)
                        }
                    }
                }
                AdoptionLookupResult.NotFound -> _uiState.update {
                    it.copy(lookupState = DeviceLookupState.NotFound, resolvedDevice = null)
                }
                AdoptionLookupResult.NetworkError -> _uiState.update {
                    it.copy(lookupState = DeviceLookupState.NetworkError, resolvedDevice = null)
                }
            }

            if (activeLookupSuffix == suffix) {
                lookupJob = null
            }
        }
    }

    private fun navigateToStep2() {
        cancelLookup()
        cancelScan()
        cancelWifiScan()
        cancelWifiRetryCooldown()
        _uiState.update {
            it.copy(
                currentStep = 2,
                bleScanPhase = BleScanPhase.Idle,
                discoveredBleDevices = emptyList(),
                scannedDevices = emptyList(),
                matchedDevice = null,
                selectedDeviceId = null,
                bleErrorMessage = null,
                showWifiNetworkSheet = false,
                wifiRetryEnabled = true,
            )
        }
    }

    fun goBackToStep1() {
        cancelLookup()
        cancelScan()
        cancelWifiScan()
        cancelWifiRetryCooldown()
        _uiState.update {
            it.copy(
                currentStep = 1,
                bleScanPhase = BleScanPhase.Idle,
                discoveredBleDevices = emptyList(),
                scannedDevices = emptyList(),
                matchedDevice = null,
                selectedDeviceId = null,
                bleErrorMessage = null,
                deviceCodeInput = if (it.isUnknownDeviceCode) "" else it.deviceCodeInput,
                identificationMode = IdentificationMode.WithCode,
                showWifiNetworkSheet = false,
                wifiRetryEnabled = true,
            )
        }
    }

    fun goBackToStep2() {
        cancelWifiScan()
        cancelWifiRetryCooldown()
        _uiState.update {
            it.copy(
                currentStep = 2,
                showWifiNetworkSheet = false,
                wifiScanPhase = WifiScanPhase.Idle,
                wifiScanErrorMessage = null,
                wifiRetryEnabled = true,
                // No reiniciar el escaneo BLE: conservamos el match/selección.
            )
        }
    }

    /**
     * Verifica las precondiciones del escaneo BLE (soporte, permisos, adaptador
     * encendido, ubicación del sistema) y, si todo está listo, arranca el
     * escaneo. La UI la llama al entrar al paso 2 y tras conceder permisos,
     * encender el Bluetooth o activar la ubicación.
     */
    fun refreshBleReadiness() {
        val app = getApplication<Application>()
        when {
            !bleScanner.isSupported() -> updateBlePhase(
                BleScanPhase.Error,
                "Este dispositivo no es compatible con Bluetooth LE.",
            )
            !BlePermissions.allGranted(app) ->
                updateBlePhase(BleScanPhase.PermissionRequired)
            !bleScanner.isEnabled() ->
                updateBlePhase(BleScanPhase.BluetoothOff)
            BlePermissions.isLocationRequiredForScan() &&
                !BlePermissions.isLocationEnabled(app) ->
                updateBlePhase(BleScanPhase.LocationOff)
            else -> startBleScan()
        }
    }

    fun retryBleScan() {
        refreshBleReadiness()
    }

    fun selectDevice(deviceId: String) {
        _uiState.update { it.copy(selectedDeviceId = deviceId) }
    }

    private fun startBleScan() {
        cancelScan()
        val target = _uiState.value.targetMacAddress
            ?.let { normalizeMac(it) }
            ?.takeIf { it.length >= MIN_MAC_HEX }

        _uiState.update {
            it.copy(
                bleScanPhase = BleScanPhase.Scanning,
                discoveredBleDevices = emptyList(),
                scannedDevices = emptyList(),
                matchedDevice = null,
                selectedDeviceId = null,
                bleErrorMessage = null,
            )
        }

        scanJob = viewModelScope.launch {
            val found = LinkedHashMap<String, DiscoveredBleDevice>()
            var matched: ScannedShellyDevice? = null
            try {
                withTimeoutOrNull(SCAN_TIMEOUT_MS) {
                    bleScanner.scanAll().collect { advertisement ->
                        val device = upsertDiscoveredDevice(
                            found = found,
                            advertisement = advertisement,
                            targetMac = target,
                        )
                        found[device.id] = device

                        _uiState.update { state ->
                            state.copy(
                                discoveredBleDevices = found.values
                                    .sortedByDescending { it.rssi }
                                    .toList(),
                            )
                        }

                        if (target != null && device.isMatched && matched == null) {
                            matched = device.toScannedShellyDevice()
                            _uiState.update {
                                it.copy(
                                    bleScanPhase = BleScanPhase.Matched,
                                    matchedDevice = matched,
                                    scannedDevices = listOf(matched!!),
                                )
                            }
                            currentCoroutineContext()[Job]?.cancel()
                        }
                    }
                }

                val finalState = _uiState.value
                if (finalState.bleScanPhase == BleScanPhase.Matched) return@launch

                when {
                    target != null -> _uiState.update { it.copy(bleScanPhase = BleScanPhase.NotFound) }
                    found.isNotEmpty() -> _uiState.update {
                        it.copy(
                            bleScanPhase = BleScanPhase.DeviceList,
                            scannedDevices = found.values
                                .map { device -> device.toScannedShellyDevice() }
                                .sortedByDescending { device -> device.rssi },
                        )
                    }
                    else -> _uiState.update { it.copy(bleScanPhase = BleScanPhase.Empty) }
                }
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                _uiState.update {
                    it.copy(
                        bleScanPhase = BleScanPhase.Error,
                        bleErrorMessage = "No pudimos completar la búsqueda Bluetooth. Intentá de nuevo.",
                    )
                }
            }
        }
    }

    private fun upsertDiscoveredDevice(
        found: LinkedHashMap<String, DiscoveredBleDevice>,
        advertisement: BleAdvertisement,
        targetMac: String?,
    ): DiscoveredBleDevice {
        val bleMac = formatMac(advertisement.address)
        val displayName = advertisement.advertisedName ?: bleMac
        val isMatched = targetMac != null && macMatchesAdvertisement(advertisement, targetMac)
        val previous = found[advertisement.address]

        return DiscoveredBleDevice(
            id = advertisement.address,
            displayName = displayName,
            macAddress = bleMac,
            rssi = advertisement.rssi,
            shellyMacAddress = advertisement.shellyMacAddress,
            isMatched = isMatched || (previous?.isMatched == true),
        )
    }

    private fun macMatchesAdvertisement(advertisement: BleAdvertisement, target: String): Boolean {
        if (normalizeMac(advertisement.address) == target) return true
        advertisement.shellyMacAddress?.let { if (normalizeMac(it) == target) return true }
        return false
    }

    private fun DiscoveredBleDevice.toScannedShellyDevice(): ScannedShellyDevice {
        val mac = shellyMacAddress ?: macAddress
        val name = if (displayName != macAddress && !displayName.contains(':')) {
            displayName
        } else {
            buildShellyDisplayName(mac)
        }
        return ScannedShellyDevice(
            id = id,
            name = name,
            macAddress = mac,
            rssi = rssi,
        )
    }

    private fun updateBlePhase(phase: BleScanPhase, message: String? = null) {
        cancelScan()
        _uiState.update {
            it.copy(
                bleScanPhase = phase,
                bleErrorMessage = message,
                discoveredBleDevices = emptyList(),
                scannedDevices = emptyList(),
                matchedDevice = null,
            )
        }
    }

    private fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
    }

    private fun cancelWifiScan() {
        wifiScanJob?.cancel()
        wifiScanJob = null
    }

    private fun startWifiRetryCooldown() {
        cancelWifiRetryCooldown()
        wifiRetryCooldownJob = viewModelScope.launch {
            delay(WIFI_RETRY_COOLDOWN_MS)
            _uiState.update { it.copy(wifiRetryEnabled = true) }
            wifiRetryCooldownJob = null
        }
    }

    private fun cancelWifiRetryCooldown() {
        wifiRetryCooldownJob?.cancel()
        wifiRetryCooldownJob = null
    }

    override fun onCleared() {
        resetActiveWork()
        sessionToken = null
        eventChannel.close()
        super.onCleared()
    }

    private fun resetActiveWork() {
        sessionLoadJob?.cancel()
        sessionLoadJob = null
        cancelLookup()
        cancelScan()
        cancelWifiScan()
        cancelWifiRetryCooldown()
        cancelProvision()
        cancelOnlinePoll()
        cancelCompletion()
        shellyRpcClient.disconnect()
    }

    private fun cancelLookup() {
        lookupJob?.cancel()
        lookupJob = null
        activeLookupSuffix = null
    }

    private fun shouldApplyLookupResult(suffix: String): Boolean {
        val state = _uiState.value
        return activeLookupSuffix == suffix &&
            state.identificationMode == IdentificationMode.WithCode &&
            state.deviceCodeInput == suffix
    }

    private companion object {
        private const val STATUS_AVAILABLE = "available"
        private const val STATUS_ASSIGNED = "assigned"
        private const val SCAN_TIMEOUT_MS = 15_000L
        private const val MIN_MAC_HEX = 6
        private const val WIFI_RETRY_COOLDOWN_MS = 30_000L
        private const val WIFI_THROTTLED_MESSAGE =
            "Android no pudo iniciar una búsqueda nueva. Esperá unos segundos antes de reintentar."
        private const val ONLINE_POLL_INTERVAL_MS = 3_000L
        private const val ONLINE_WAIT_TIMEOUT_MS = 180_000L
        private const val SUCCESS_DISPLAY_MS = 900L
    }
}

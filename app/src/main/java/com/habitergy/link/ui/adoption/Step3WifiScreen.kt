package com.habitergy.link.ui.adoption

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.habitergy.link.data.RuntimePermissions
import com.habitergy.link.data.findActivity
import com.habitergy.link.data.wifi.WifiPermissions
import com.habitergy.link.domain.model.AdoptionUiState
import com.habitergy.link.domain.model.WifiNetwork
import com.habitergy.link.domain.model.WifiScanPhase
import com.habitergy.link.ui.components.AdoptionScreenScaffold
import com.habitergy.link.ui.components.HabitergyPrimaryButton
import com.habitergy.link.ui.components.HabitergySecondaryButton
import com.habitergy.link.ui.components.ScreenTitle
import com.habitergy.link.ui.theme.HabitergyColors
import com.habitergy.link.ui.theme.HabitergyShapes
import kotlinx.coroutines.launch

/**
 * Paso 3 — Conectalo a la red WiFi.
 *
 * SSID prellenado con la red del teléfono, editable (redes ocultas), con ícono
 * para elegir otra señal. Contraseña opcional (red abierta).
 *
 * Permisos: se piden **antes** de abrir el bottom sheet (el diálogo del sistema
 * no se muestra bien desde un ModalBottomSheet). Si están denegados de forma
 * permanente, se abre Ajustes de la app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Step3WifiScreen(
    state: AdoptionUiState,
    onSsidChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onOpenNetworkSheet: () -> Unit,
    onDismissNetworkSheet: () -> Unit,
    onSelectNetwork: (String) -> Unit,
    onRefreshWifiScan: () -> Unit,
    onRetryWifiScan: () -> Unit,
    onContinue: () -> Boolean,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var permissionRequestedOnce by rememberSaveable { mutableStateOf(false) }
    var openSettingsInstead by rememberSaveable { mutableStateOf(false) }

    fun refreshPermissionStrategy() {
        openSettingsInstead = activity != null &&
            RuntimePermissions.shouldOpenAppSettings(
                activity = activity,
                permissions = WifiPermissions.required,
                alreadyRequested = permissionRequestedOnce,
            )
    }

    val appSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        if (WifiPermissions.allGranted(context)) {
            onOpenNetworkSheet()
        } else {
            refreshPermissionStrategy()
            scope.launch {
                snackbarHostState.showSnackbar(
                    "Sin el permiso de WiFi no podemos listar las redes cercanas.",
                )
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        permissionRequestedOnce = true
        val granted = WifiPermissions.allGranted(context) ||
            (results.isNotEmpty() && results.values.all { granted -> granted })
        val needsSettings = activity != null &&
            RuntimePermissions.shouldOpenAppSettings(
                activity = activity,
                permissions = WifiPermissions.required,
                alreadyRequested = true,
            )
        openSettingsInstead = needsSettings
        if (granted) {
            onOpenNetworkSheet()
        } else if (needsSettings) {
            // Android ya no muestra el diálogo → ficha de la app en Ajustes.
            appSettingsLauncher.launch(RuntimePermissions.appDetailsSettingsIntent(context))
        } else {
            scope.launch {
                snackbarHostState.showSnackbar(
                    "Necesitamos permiso para ver las redes WiFi. Tocá el ícono de nuevo.",
                )
            }
        }
    }

    val locationSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { onRefreshWifiScan() }

    val wifiSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { onRefreshWifiScan() }

    fun requestWifiPermissionOrOpenSettings() {
        refreshPermissionStrategy()
        if (openSettingsInstead) {
            appSettingsLauncher.launch(RuntimePermissions.appDetailsSettingsIntent(context))
        } else {
            val missing = WifiPermissions.missing(context)
            if (missing.isEmpty()) {
                onOpenNetworkSheet()
            } else {
                permissionLauncher.launch(missing)
            }
        }
    }

    fun onSearchNetworksClick() {
        if (WifiPermissions.allGranted(context)) {
            onOpenNetworkSheet()
        } else {
            // Pedir permiso ANTES de abrir el sheet (diálogo del sistema).
            requestWifiPermissionOrOpenSettings()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
    ) { _ ->
        AdoptionScreenScaffold(
            currentStep = state.currentStep,
            totalSteps = state.totalSteps,
            onBack = onBack,
            content = {
                ScreenTitle(
                    title = "Conectalo a la red WiFi",
                    subtitle = "Usá la misma red 2,4 GHz a la que está conectado este teléfono. " +
                        "Esa será la red del controlador para hablar con Habitergy.",
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = state.wifiSsid,
                    onValueChange = onSsidChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nombre de la red (SSID)") },
                    singleLine = true,
                    isError = state.wifiSsidError,
                    supportingText = {
                        if (state.wifiSsidError) {
                            Text("Ingresá el nombre de la red WiFi")
                        } else {
                            Text("Podés editarlo si es una red oculta")
                        }
                    },
                    trailingIcon = {
                        IconButton(onClick = ::onSearchNetworksClick) {
                            Icon(
                                imageVector = Icons.Default.Wifi,
                                contentDescription = "Buscar otras redes",
                                tint = HabitergyColors.Primary,
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Text,
                    ),
                    shape = RoundedCornerShape(20.dp),
                    colors = wifiFieldColors(isError = state.wifiSsidError),
                )

                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = state.wifiPassword,
                    onValueChange = onPasswordChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Contraseña") },
                    singleLine = true,
                    supportingText = {
                        Text(
                            text = if (state.wifiPassword.isEmpty()) {
                                "Sin contraseña"
                            } else {
                                " "
                            },
                        )
                    },
                    visualTransformation = if (state.wifiPasswordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = onTogglePasswordVisibility) {
                            Icon(
                                imageVector = if (state.wifiPasswordVisible) {
                                    Icons.Default.VisibilityOff
                                } else {
                                    Icons.Default.Visibility
                                },
                                contentDescription = if (state.wifiPasswordVisible) {
                                    "Ocultar contraseña"
                                } else {
                                    "Mostrar contraseña"
                                },
                                tint = HabitergyColors.IconNormal,
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Password,
                    ),
                    shape = RoundedCornerShape(20.dp),
                    colors = wifiFieldColors(isError = false),
                )
            },
            footer = {
                HabitergyPrimaryButton(
                    label = "Continuar",
                    enabled = state.canProceedFromStep3,
                    onClick = {
                        if (onContinue()) {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    "Configuración del controlador: próximamente",
                                )
                            }
                        }
                    },
                )
            },
        )
    }

    if (state.showWifiNetworkSheet) {
        ModalBottomSheet(
            onDismissRequest = onDismissNetworkSheet,
            sheetState = sheetState,
            containerColor = HabitergyColors.Surface,
            shape = HabitergyShapes.extraLarge,
        ) {
            WifiNetworkSheetContent(
                state = state,
                openSettingsInstead = openSettingsInstead,
                onRequestPermission = ::requestWifiPermissionOrOpenSettings,
                onOpenLocationSettings = {
                    locationSettingsLauncher.launch(
                        Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS),
                    )
                },
                onOpenWifiSettings = {
                    wifiSettingsLauncher.launch(Intent(Settings.ACTION_WIFI_SETTINGS))
                },
                onRetry = onRetryWifiScan,
                onSelect = onSelectNetwork,
            )
        }
    }
}

@Composable
private fun WifiNetworkSheetContent(
    state: AdoptionUiState,
    openSettingsInstead: Boolean,
    onRequestPermission: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onOpenWifiSettings: () -> Unit,
    onRetry: () -> Unit,
    onSelect: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
    ) {
        Text(
            text = "Elegí una red WiFi",
            style = MaterialTheme.typography.titleLarge,
            color = HabitergyColors.TextTitle,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Al elegir una red solo completamos el nombre. " +
                "La contraseña la ingresás vos.",
            style = MaterialTheme.typography.bodyMedium,
            color = HabitergyColors.TextSecondary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 20.dp),
            textAlign = TextAlign.Center,
        )

        when (state.wifiScanPhase) {
            WifiScanPhase.PermissionRequired -> WifiSheetStatus(
                icon = Icons.Default.Wifi,
                message = if (openSettingsInstead) {
                    "El permiso está bloqueado. Abrí los ajustes de la app y " +
                        "habilitá el acceso a dispositivos cercanos / ubicación."
                } else {
                    "Necesitamos permiso para ver las redes WiFi cercanas."
                },
                actionLabel = if (openSettingsInstead) "Abrir ajustes" else "Otorgar permisos",
                onAction = onRequestPermission,
            )

            WifiScanPhase.LocationOff -> WifiSheetStatus(
                icon = Icons.Default.LocationOff,
                message = "La ubicación del teléfono está apagada. Activala para " +
                    "listar las redes cercanas.",
                actionLabel = "Activar ubicación",
                onAction = onOpenLocationSettings,
            )

            WifiScanPhase.WifiOff -> WifiSheetStatus(
                icon = Icons.Default.WifiOff,
                message = "El WiFi está apagado. Encendelo para buscar redes.",
                actionLabel = "Abrir ajustes WiFi",
                onAction = onOpenWifiSettings,
            )

            WifiScanPhase.Scanning, WifiScanPhase.Idle -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(
                        color = HabitergyColors.Primary,
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp,
                    )
                    Text(
                        text = "Buscando redes cercanas…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = HabitergyColors.TextSecondary,
                    )
                }
            }

            WifiScanPhase.Empty -> WifiSheetStatus(
                icon = Icons.Default.Search,
                message = "No encontramos redes WiFi cercanas. Acercate al router o " +
                    "escribí el SSID a mano.",
                actionLabel = "Buscar de nuevo",
                onAction = onRetry,
            )

            WifiScanPhase.Error -> WifiSheetStatus(
                icon = Icons.Default.WifiOff,
                message = state.wifiScanErrorMessage
                    ?: "No pudimos buscar redes WiFi. Intentá de nuevo.",
                actionLabel = "Buscar de nuevo",
                onAction = onRetry,
                tint = HabitergyColors.Error,
            )

            WifiScanPhase.Results -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                ) {
                    items(state.nearbyWifiNetworks, key = { it.ssid }) { network ->
                        WifiNetworkRow(
                            network = network,
                            onClick = { onSelect(network.ssid) },
                        )
                        HorizontalDivider(color = HabitergyColors.OutlineVariant)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                HabitergySecondaryButton(label = "Buscar de nuevo", onClick = onRetry)
            }
        }
    }
}

@Composable
private fun WifiNetworkRow(
    network: WifiNetwork,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Wifi,
            contentDescription = null,
            tint = HabitergyColors.Primary,
            modifier = Modifier.size(24.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = network.ssid,
                style = MaterialTheme.typography.bodyLarge,
                color = HabitergyColors.TextPrimary,
            )
            Text(
                text = network.signalLabel,
                style = MaterialTheme.typography.labelSmall,
                color = HabitergyColors.TextSecondary,
            )
        }
        Icon(
            imageVector = if (network.isSecured) Icons.Default.Lock else Icons.Default.LockOpen,
            contentDescription = if (network.isSecured) "Red protegida" else "Red abierta",
            tint = HabitergyColors.IconNormal,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun WifiSheetStatus(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = HabitergyColors.Secondary,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = HabitergyColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
        HabitergyPrimaryButton(
            label = actionLabel,
            showArrow = false,
            onClick = onAction,
        )
    }
}

@Composable
private fun wifiFieldColors(isError: Boolean) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = if (isError) HabitergyColors.Error else HabitergyColors.Primary,
    unfocusedBorderColor = if (isError) HabitergyColors.Error else HabitergyColors.BorderNormal,
    focusedLabelColor = if (isError) HabitergyColors.Error else HabitergyColors.Primary,
    unfocusedLabelColor = HabitergyColors.TextSecondary,
    cursorColor = HabitergyColors.Primary,
    focusedTextColor = HabitergyColors.TextPrimary,
    unfocusedTextColor = HabitergyColors.TextPrimary,
    focusedSupportingTextColor = if (isError) HabitergyColors.Error else HabitergyColors.TextSecondary,
    unfocusedSupportingTextColor = if (isError) HabitergyColors.Error else HabitergyColors.TextSecondary,
    focusedContainerColor = HabitergyColors.Card,
    unfocusedContainerColor = HabitergyColors.Card,
)

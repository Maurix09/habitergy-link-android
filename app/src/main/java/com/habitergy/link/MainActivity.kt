package com.habitergy.link

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.habitergy.link.domain.AdoptionDeepLinkParser
import com.habitergy.link.domain.AdoptionLaunchRequest
import com.habitergy.link.domain.model.AdoptionEntryState
import com.habitergy.link.domain.model.AdoptionEvent
import com.habitergy.link.ui.adoption.AdoptionEntryGate
import com.habitergy.link.ui.adoption.AdoptionFlow
import com.habitergy.link.ui.adoption.AdoptionViewModel
import com.habitergy.link.ui.theme.HabitergyColors
import com.habitergy.link.ui.theme.HabitergyTheme
import kotlinx.coroutines.flow.collect
import java.util.UUID

class MainActivity : ComponentActivity() {
    private var launchRequest by mutableStateOf<AdoptionLaunchRequest>(AdoptionLaunchRequest.NoSession)
    private var launchVersion by mutableLongStateOf(0L)
    private var forceWizardReset: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        acceptIntent(intent, forceReset = false)
        enableEdgeToEdge()
        setContent {
            val adoptionViewModel: AdoptionViewModel = viewModel()
            val entryState by adoptionViewModel.entryState.collectAsState()

            LaunchedEffect(launchVersion) {
                adoptionViewModel.handleLaunch(
                    request = launchRequest,
                    forceReset = forceWizardReset,
                )
            }

            LaunchedEffect(adoptionViewModel) {
                adoptionViewModel.events.collect { event ->
                    when (event) {
                        is AdoptionEvent.ReturnToManager ->
                            returnToManager(event.sessionId, adoptionViewModel)
                    }
                }
            }

            HabitergyTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding(),
                    color = HabitergyColors.Surface,
                ) {
                    // Evita el flash del gate "Abrí Link desde Manager" mientras se
                    // valida el deep link: pantalla en blanco del mismo color hasta Ready.
                    val state = entryState
                    val awaitingSession =
                        launchRequest is AdoptionLaunchRequest.Session &&
                            (state is AdoptionEntryState.NoSession ||
                                state is AdoptionEntryState.Loading)

                    when {
                        awaitingSession -> Unit
                        state is AdoptionEntryState.Ready -> AdoptionFlow(
                            viewModel = adoptionViewModel,
                            sessionContext = state.context,
                            onExitToManager = ::finishAndRemoveTask,
                        )
                        else -> AdoptionEntryGate(
                            state = state,
                            onRetry = adoptionViewModel::retrySessionContext,
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptIntent(intent, forceReset = true)
    }

    private fun acceptIntent(intent: Intent, forceReset: Boolean) {
        launchRequest = intent.dataString?.let(AdoptionDeepLinkParser::parse)
            ?: AdoptionLaunchRequest.NoSession
        forceWizardReset = forceReset
        launchVersion += 1
        // El token no queda retenido en el Intent del task ni se persiste al recrear el proceso.
        intent.data = null
    }

    private fun returnToManager(sessionId: String, viewModel: AdoptionViewModel) {
        val canonicalSessionId = runCatching { UUID.fromString(sessionId).toString() }
            .getOrElse {
                viewModel.onReturnNavigationFailed()
                return
            }
        val returnUri = Uri.Builder()
            .scheme("https")
            .authority("app.habitergy.com")
            .path("/adopcion-link/retorno")
            .appendQueryParameter("sessionId", canonicalSessionId)
            .build()

        try {
            startActivity(Intent(Intent.ACTION_VIEW, returnUri))
            finishAndRemoveTask()
        } catch (_: ActivityNotFoundException) {
            viewModel.onReturnNavigationFailed()
        } catch (_: SecurityException) {
            viewModel.onReturnNavigationFailed()
        }
    }
}

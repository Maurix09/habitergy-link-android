package com.habitergy.link.domain.model

data class AdoptionSiteDisplay(
    val id: String,
    val name: String,
)

data class AdoptionSessionContext(
    val sessionId: String,
    val expiresAt: String,
    val returnTo: String,
    val site: AdoptionSiteDisplay?,
)

sealed interface AdoptionEntryState {
    data object NoSession : AdoptionEntryState
    data object Loading : AdoptionEntryState
    data class Ready(val context: AdoptionSessionContext) : AdoptionEntryState
    data object Invalid : AdoptionEntryState
    data object Expired : AdoptionEntryState
    data object NetworkError : AdoptionEntryState
}

enum class CompletionPhase {
    Idle,
    Completing,
    Error,
    Completed,
}

sealed interface AdoptionEvent {
    data class ReturnToManager(val sessionId: String) : AdoptionEvent
}

package com.habitergy.link.data.api

import kotlinx.serialization.Serializable

/** Respuesta de GET /api/adoption/devices/:deviceCode. */
@Serializable
data class AdoptionDeviceDto(
    val deviceCode: String,
    val model: String,
    val macAddress: String,
    val status: String,
)

@Serializable
data class AdoptionProvisionDto(
    val deviceCode: String,
    val shortCode: String,
    val provisioned: Boolean,
)

@Serializable
data class AdoptionOnlineDto(
    val deviceCode: String,
    val isOnline: Boolean,
    val lastSeenAt: String? = null,
)

@Serializable
data class AdoptionErrorDto(
    val error: Boolean = true,
    val message: String? = null,
)

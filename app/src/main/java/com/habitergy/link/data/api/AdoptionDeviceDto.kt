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

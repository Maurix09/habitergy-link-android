package com.habitergy.link.data.api

/**
 * Configuración de la API. Habitergy Link consulta el backend público;
 * la API es quien habla con PostgreSQL (`db.habitergy.com`).
 */
object ApiConfig {
    const val BASE_URL: String = "https://api.habitergy.com"

    const val ADOPTION_DEVICE_PATH = "/api/adoption/devices"
}

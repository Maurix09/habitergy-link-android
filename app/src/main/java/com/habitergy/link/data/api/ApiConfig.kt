package com.habitergy.link.data.api

import com.habitergy.link.BuildConfig

/**
 * Configuración de la API. En debug apunta al emulador (10.0.2.2 = localhost
 * de la máquina host); en release al dominio de producción.
 */
object ApiConfig {
    val BASE_URL: String = if (BuildConfig.DEBUG) {
        "http://10.0.2.2:3000"
    } else {
        "https://api.habitergy.com"
    }

    const val ADOPTION_DEVICE_PATH = "/api/adoption/devices"
}

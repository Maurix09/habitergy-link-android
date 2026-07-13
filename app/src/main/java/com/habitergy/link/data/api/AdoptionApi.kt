package com.habitergy.link.data.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Cliente HTTP del flujo de adopción. Llama al endpoint público
 * GET /api/adoption/devices/{deviceCode} de apps/api.
 */
class AdoptionApi(
    private val baseUrl: String = ApiConfig.BASE_URL,
    private val client: HttpClient = createClient(),
) {
    suspend fun lookupDevice(fullCode: String): HttpResponse {
        return client.get("$baseUrl${ApiConfig.ADOPTION_DEVICE_PATH}/$fullCode")
    }

    suspend fun parseFound(response: HttpResponse): AdoptionDeviceDto {
        return response.body<AdoptionDeviceDto>()
    }

    companion object {
        fun createClient(): HttpClient = HttpClient(Android) {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                    },
                )
            }
        }
    }
}

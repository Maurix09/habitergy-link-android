package com.habitergy.link.data.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject

/**
 * Cliente HTTP del flujo de adopción. Llama a los endpoints públicos de apps/api.
 */
class AdoptionApi(
    private val baseUrl: String = ApiConfig.BASE_URL,
    private val client: HttpClient = createClient(),
) {
    suspend fun lookupDevice(fullCode: String): HttpResponse {
        return client.get("$baseUrl${ApiConfig.ADOPTION_DEVICE_PATH}/$fullCode")
    }

    /**
     * Fastify rechaza POST con `Content-Type: application/json` y body vacío
     * (ERROR 5 en Link). Enviamos `{}` para que el content-type y el body coincidan.
     */
    suspend fun provisionDevice(fullCode: String): HttpResponse {
        return client.post("$baseUrl${ApiConfig.ADOPTION_DEVICE_PATH}/$fullCode/provision") {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {})
        }
    }

    suspend fun getOnlineStatus(fullCode: String): HttpResponse {
        return client.get("$baseUrl${ApiConfig.ADOPTION_DEVICE_PATH}/$fullCode/online")
    }

    suspend fun parseFound(response: HttpResponse): AdoptionDeviceDto {
        return response.body<AdoptionDeviceDto>()
    }

    suspend fun parseProvision(response: HttpResponse): AdoptionProvisionDto {
        return response.body<AdoptionProvisionDto>()
    }

    suspend fun parseOnline(response: HttpResponse): AdoptionOnlineDto {
        return response.body<AdoptionOnlineDto>()
    }

    suspend fun parseErrorMessage(response: HttpResponse): String? {
        return runCatching {
            response.body<AdoptionErrorDto>().message
        }.getOrNull()
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

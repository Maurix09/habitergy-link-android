package com.habitergy.link.data

import com.habitergy.link.data.api.AdoptionApi
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException

/**
 * Resultado del lookup de adopción. El ViewModel mapea esto al estado de UI
 * (Available / Assigned / Unavailable / NotFound / NetworkError).
 */
sealed interface AdoptionLookupResult {
    data class Found(
        val deviceCode: String,
        val macAddress: String,
        val model: String,
        val status: String,
    ) : AdoptionLookupResult

    data object NotFound : AdoptionLookupResult
    data object NetworkError : AdoptionLookupResult
}

/**
 * Repositorio del flujo de adopción. Aisla el ViewModel del cliente HTTP;
 * permite inyectar una implementación de prueba en el futuro.
 */
class AdoptionRepository(
    private val api: AdoptionApi = AdoptionApi(),
) {
    suspend fun lookup(fullCode: String): AdoptionLookupResult = try {
        val response = api.lookupDevice(fullCode)
        when {
            response.status.isSuccess() -> {
                val dto = api.parseFound(response)
                AdoptionLookupResult.Found(
                    deviceCode = dto.deviceCode,
                    macAddress = dto.macAddress,
                    model = dto.model,
                    status = dto.status,
                )
            }
            response.status.value == 404 -> AdoptionLookupResult.NotFound
            else -> AdoptionLookupResult.NetworkError
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (_: Exception) {
        AdoptionLookupResult.NetworkError
    }
}

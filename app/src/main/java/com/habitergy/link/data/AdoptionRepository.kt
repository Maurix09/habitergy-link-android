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

sealed interface AdoptionProvisionResult {
    data class Success(val deviceCode: String, val shortCode: String) : AdoptionProvisionResult
    data object NotFound : AdoptionProvisionResult
    data object Conflict : AdoptionProvisionResult
    data class ApiError(val message: String) : AdoptionProvisionResult
    data object NetworkError : AdoptionProvisionResult
}

sealed interface AdoptionOnlineResult {
    data class Status(
        val deviceCode: String,
        val isOnline: Boolean,
        val lastSeenAt: String?,
    ) : AdoptionOnlineResult

    data object NotFound : AdoptionOnlineResult
    data object NetworkError : AdoptionOnlineResult
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

    suspend fun provision(fullCode: String): AdoptionProvisionResult = try {
        val response = api.provisionDevice(fullCode)
        when {
            response.status.isSuccess() -> {
                val dto = api.parseProvision(response)
                AdoptionProvisionResult.Success(
                    deviceCode = dto.deviceCode,
                    shortCode = dto.shortCode,
                )
            }
            response.status.value == 404 -> AdoptionProvisionResult.NotFound
            response.status.value == 409 -> AdoptionProvisionResult.Conflict
            else -> AdoptionProvisionResult.ApiError(
                api.parseErrorMessage(response) ?: "No pudimos registrar el controlador en el broker.",
            )
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (_: Exception) {
        AdoptionProvisionResult.NetworkError
    }

    suspend fun getOnlineStatus(fullCode: String): AdoptionOnlineResult = try {
        val response = api.getOnlineStatus(fullCode)
        when {
            response.status.isSuccess() -> {
                val dto = api.parseOnline(response)
                AdoptionOnlineResult.Status(
                    deviceCode = dto.deviceCode,
                    isOnline = dto.isOnline,
                    lastSeenAt = dto.lastSeenAt,
                )
            }
            response.status.value == 404 -> AdoptionOnlineResult.NotFound
            else -> AdoptionOnlineResult.NetworkError
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (_: Exception) {
        AdoptionOnlineResult.NetworkError
    }
}

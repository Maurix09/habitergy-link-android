package com.habitergy.link.domain

import java.net.URI

sealed interface AdoptionLaunchRequest {
    data object NoSession : AdoptionLaunchRequest
    data object Invalid : AdoptionLaunchRequest
    data class Session(val token: String) : AdoptionLaunchRequest
}

/**
 * Parser puro y estricto del enlace que Habitergy Manager envía a Link.
 *
 * No decodifica ni normaliza el token: el contrato admite únicamente 43
 * caracteres base64url y exactamente un query parameter llamado `token`.
 */
object AdoptionDeepLinkParser {
    private val tokenPattern = Regex("^[A-Za-z0-9_-]{43}$")

    fun parse(rawUri: String): AdoptionLaunchRequest {
        val uri = runCatching { URI(rawUri) }.getOrNull()
            ?: return AdoptionLaunchRequest.Invalid

        if (uri.scheme != SCHEME ||
            uri.host != HOST ||
            uri.rawPath != PATH ||
            uri.userInfo != null ||
            uri.port != -1 ||
            uri.rawFragment != null
        ) {
            return AdoptionLaunchRequest.Invalid
        }

        val rawQuery = uri.rawQuery ?: return AdoptionLaunchRequest.Invalid
        if (!rawQuery.startsWith("$TOKEN_PARAMETER=") || rawQuery.contains('&')) {
            return AdoptionLaunchRequest.Invalid
        }

        val token = rawQuery.substringAfter('=')
        return if (tokenPattern.matches(token)) {
            AdoptionLaunchRequest.Session(token)
        } else {
            AdoptionLaunchRequest.Invalid
        }
    }

    private const val SCHEME = "habitergy"
    private const val HOST = "link"
    private const val PATH = "/adopt"
    private const val TOKEN_PARAMETER = "token"
}

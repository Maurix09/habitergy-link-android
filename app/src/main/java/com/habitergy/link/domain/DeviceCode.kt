package com.habitergy.link.domain

/**
 * Validación de device_code (formato SH-XXXXC) en el cliente.
 *
 * Réplica EXACTA del algoritmo del backend en
 * `packages/utils/src/shortCode.ts`. Cualquier cambio aquí debe replicarse
 * en el backend y viceversa.
 *
 * Estructura: prefijo "SH-" (fijo) + 4 caracteres de cuerpo + 1 carácter de
 * checksum calculado sobre el cuerpo. El usuario tipea solo el sufijo de 5
 * caracteres (cuerpo + checksum); el prefijo lo muestra la UI.
 */
object DeviceCode {
    const val PREFIX = "SH-"
    const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    const val BODY_LENGTH = 4
    const val SUFFIX_LENGTH = 5

    private val WEIGHTS = intArrayOf(1, 3, 1, 3)

    private fun charToIndex(char: Char): Int {
        val index = ALPHABET.indexOf(char)
        require(index >= 0) { "Invalid device_code character: $char" }
        return index
    }

    /** Calcula el carácter de checksum de un cuerpo de 4 caracteres. */
    fun computeChecksum(body: String): Char {
        require(body.length == BODY_LENGTH && body.all { it in ALPHABET }) {
            "body must be $BODY_LENGTH valid characters from the alphabet"
        }
        var sum = 0
        for (i in body.indices) {
            sum += charToIndex(body[i]) * WEIGHTS[i]
        }
        return ALPHABET[sum % ALPHABET.length]
    }

    /**
     * Valida un sufijo de 5 caracteres (cuerpo + checksum), sin el prefijo.
     * Devuelve true solo si el checksum calculado coincide con el 5º carácter.
     */
    fun isValidSuffix(suffix: String): Boolean {
        val normalized = suffix.trim().uppercase()
        if (normalized.length != SUFFIX_LENGTH) return false
        if (normalized.any { it !in ALPHABET }) return false
        val body = normalized.substring(0, BODY_LENGTH)
        val checksum = normalized[BODY_LENGTH]
        return computeChecksum(body) == checksum
    }

    /** Sanitiza la entrada del usuario a sufijo válido (mayúsculas, alfanumérico). */
    fun normalizeSuffix(raw: String): String =
        raw.trim().uppercase().filter { it.isLetterOrDigit() }.take(SUFFIX_LENGTH)

    /** Construye el código completo SH-XXXXC a partir del sufijo de 5 chars. */
    fun fullCode(suffix: String): String = PREFIX + suffix.trim().uppercase()
}

package com.habitergy.link.data.ble

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Digest auth SHA-256 para RPC Shelly Gen2+ (BLE / WebSocket / “other transports”).
 *
 * @see https://shelly-api-docs.shelly.cloud/gen2/General/Authentication/
 * @see aioshelly AuthData (Home Assistant)
 */
internal class ShellyDigestAuth(
    private val username: String,
    private val password: String,
    realm: String,
) {
    var realm: String = realm
        private set

    private var nonceElement: JsonElement? = null
    private var nonceForHash: String? = null
    private var nc: Int = 0
    private var algorithm: String = "SHA-256"

    private val ha1: String
        get() = sha256Hex("$username:$realm:$password")

    val hasNonce: Boolean
        get() = nonceForHash != null

    fun updateChallenge(challenge: JsonObject) {
        val challengeRealm = challenge["realm"]?.jsonPrimitive?.contentOrNull
            ?: throw ShellyBleRpcException("Challenge digest sin realm.")
        realm = challengeRealm

        val algo = challenge["algorithm"]?.jsonPrimitive?.contentOrNull ?: "SHA-256"
        if (!algo.equals("SHA-256", ignoreCase = true)) {
            throw ShellyBleRpcException("Algoritmo de auth no soportado: $algo")
        }
        algorithm = algo

        val nonce = challenge["nonce"]
            ?: throw ShellyBleRpcException("Challenge digest sin nonce.")
        nonceElement = nonce
        nonceForHash = nonce.jsonPrimitive.content

        nc = challenge["nc"]?.jsonPrimitive?.intOrNull ?: 1
    }

    /** Construye el objeto `auth` del frame RPC e incrementa `nc`. */
    fun buildAuthObject(): JsonObject {
        val nonceValue = nonceForHash
            ?: throw ShellyBleRpcException("Digest auth sin nonce (falta challenge 401).")
        val nonceJson = nonceElement
            ?: throw ShellyBleRpcException("Digest auth sin nonce (falta challenge 401).")

        val cnonceBytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val cnonce = Base64.getEncoder().encodeToString(cnonceBytes)
        val response = sha256Hex("$ha1:$nonceValue:$nc:$cnonce:auth:$HA2")

        val auth = buildJsonObject {
            put("realm", realm)
            put("username", username)
            put("nonce", nonceJson)
            put("nc", nc)
            put("cnonce", cnonce)
            put("response", response)
            put("algorithm", algorithm)
        }
        nc += 1
        return auth
    }

    companion object {
        /** HA2 fijo para transports RPC distintos de HTTP (BLE incluido). */
        val HA2: String = sha256Hex("dummy_method:dummy_uri")

        fun sha256Hex(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            return digest.digest(input.toByteArray(Charsets.UTF_8))
                .joinToString("") { byte -> "%02x".format(byte) }
        }

        /**
         * Parsea el `error.message` de un 401 Shelly.
         * El message es un JSON string con auth_type/nonce/realm/algorithm.
         */
        fun parseChallengeMessage(message: String): JsonObject? {
            return try {
                val element = kotlinx.serialization.json.Json.parseToJsonElement(message.trim())
                val obj = element as? JsonObject ?: return null
                if (obj["auth_type"]?.jsonPrimitive?.contentOrNull != "digest") return null
                obj
            } catch (_: Exception) {
                null
            }
        }

        /** Extrae code numérico del error RPC (401 = unauthorized). */
        fun errorCode(error: JsonObject): Int? {
            val code = error["code"]?.jsonPrimitive ?: return null
            return code.intOrNull ?: code.longOrNull?.toInt()
        }
    }
}

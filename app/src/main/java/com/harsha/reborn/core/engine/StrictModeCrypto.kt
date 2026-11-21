package com.harsha.reborn.core.engine

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Pure JVM cryptographic utilities for Strict Mode PINs and offline Trusted Contact codes.
 * Uses salted SHA-256 hashes and constant-time digest comparison so neither PINs nor
 * friend codes are stored in plaintext.
 */
object StrictModeCrypto {

    private val random = SecureRandom()
    private const val FRIEND_CODE_ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"

    fun generateSaltHex(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return bytes.toHex()
    }

    fun generateTrustedContactCode(): String {
        val raw = buildString {
            repeat(8) {
                append(FRIEND_CODE_ALPHABET[random.nextInt(FRIEND_CODE_ALPHABET.length)])
            }
        }
        return "${raw.substring(0, 4)}-${raw.substring(4, 8)}"
    }

    fun normalizeTrustedCode(code: String): String =
        code.trim().uppercase().replace("-", "").replace(" ", "")

    fun hashSecret(secret: String, saltHex: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(saltHex.toByteArray(Charsets.UTF_8))
        digest.update(":".toByteArray(Charsets.UTF_8))
        digest.update(secret.toByteArray(Charsets.UTF_8))
        return digest.digest().toHex()
    }

    fun verifySecret(candidate: String, saltHex: String, expectedHashHex: String): Boolean {
        if (saltHex.isBlank() || expectedHashHex.isBlank()) return false
        val candidateHash = hashSecret(candidate, saltHex)
        return MessageDigest.isEqual(
            candidateHash.toByteArray(Charsets.UTF_8),
            expectedHashHex.toByteArray(Charsets.UTF_8),
        )
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }
}

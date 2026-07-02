package dev.touchpilot.app.security

import java.util.Base64

/**
 * Serializable wire format for a secret encrypted at rest.
 *
 * Serialized form: `tpsec:<version>:<base64(iv)>:<base64(ciphertext+tag)>`.
 *
 * The version field lets a future key rotation or algorithm change stay
 * backward compatible: readers can branch on it while old envelopes still
 * decrypt. The type is pure (no Android dependencies) so the envelope codec is
 * unit-testable off device — the same split used elsewhere in the codebase
 * between thin platform adapters and testable cores.
 */
class EncryptedSecret(
    val version: Int,
    val iv: ByteArray,
    val ciphertext: ByteArray,
) {
    /** Encodes this envelope to its canonical string form. */
    fun serialize(): String {
        val encoder = Base64.getEncoder()
        return PREFIX +
            "$version:" +
            "${encoder.encodeToString(iv)}:" +
            encoder.encodeToString(ciphertext)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptedSecret) return false
        return version == other.version &&
            iv.contentEquals(other.iv) &&
            ciphertext.contentEquals(other.ciphertext)
    }

    override fun hashCode(): Int {
        var result = version
        result = 31 * result + iv.contentHashCode()
        result = 31 * result + ciphertext.contentHashCode()
        return result
    }

    companion object {
        /** Marker prefix that distinguishes an encrypted envelope from legacy plaintext. */
        const val PREFIX = "tpsec:"

        /** Current envelope version emitted by [serialize]. */
        const val CURRENT_VERSION = 1

        /**
         * Returns true if [stored] carries the encrypted-envelope marker. A value
         * that does not start with [PREFIX] is treated as legacy plaintext.
         */
        fun looksEncrypted(stored: String): Boolean = stored.startsWith(PREFIX)

        /**
         * Parses a serialized envelope produced by [serialize].
         *
         * @throws IllegalArgumentException if the value is not a well-formed envelope.
         */
        fun parse(stored: String): EncryptedSecret {
            require(looksEncrypted(stored)) { "Not an encrypted secret envelope" }

            val fields = stored.removePrefix(PREFIX).split(FIELD_SEPARATOR)
            require(fields.size == FIELD_COUNT) {
                "Malformed encrypted secret: expected $FIELD_COUNT fields, got ${fields.size}"
            }

            val version = fields[0].toIntOrNull()
                ?: throw IllegalArgumentException("Malformed encrypted secret: bad version '${fields[0]}'")

            val decoder = Base64.getDecoder()
            val iv = decodeField(decoder, fields[1], "iv")
            val ciphertext = decodeField(decoder, fields[2], "ciphertext")

            require(iv.isNotEmpty()) { "Malformed encrypted secret: empty iv" }
            require(ciphertext.isNotEmpty()) { "Malformed encrypted secret: empty ciphertext" }

            return EncryptedSecret(version, iv, ciphertext)
        }

        private const val FIELD_SEPARATOR = ":"
        private const val FIELD_COUNT = 3

        private fun decodeField(decoder: Base64.Decoder, value: String, field: String): ByteArray {
            return runCatching { decoder.decode(value) }
                .getOrElse {
                    throw IllegalArgumentException("Malformed encrypted secret: bad base64 for $field")
                }
        }
    }
}

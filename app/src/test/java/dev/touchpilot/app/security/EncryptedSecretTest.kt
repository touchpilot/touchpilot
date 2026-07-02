package dev.touchpilot.app.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EncryptedSecretTest {

    @Test
    fun serializeThenParseRoundTrips() {
        val original = EncryptedSecret(
            version = EncryptedSecret.CURRENT_VERSION,
            iv = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12),
            ciphertext = byteArrayOf(42, -7, 0, 99, -128, 127),
        )

        val parsed = EncryptedSecret.parse(original.serialize())

        assertEquals(original, parsed)
    }

    @Test
    fun serializedFormCarriesPrefixAndVersion() {
        val serialized = EncryptedSecret(
            version = 1,
            iv = byteArrayOf(1, 2, 3),
            ciphertext = byteArrayOf(4, 5, 6),
        ).serialize()

        assertTrue(serialized.startsWith("tpsec:1:"))
    }

    @Test
    fun looksEncryptedDistinguishesEnvelopeFromPlaintext() {
        assertTrue(EncryptedSecret.looksEncrypted("tpsec:1:AAAA:BBBB"))
        assertFalse(EncryptedSecret.looksEncrypted("sk-live-plaintext-key"))
        assertFalse(EncryptedSecret.looksEncrypted(""))
    }

    @Test
    fun parseRejectsPlaintext() {
        assertFailsWith<IllegalArgumentException> { EncryptedSecret.parse("sk-live-plaintext-key") }
    }

    @Test
    fun parseRejectsWrongFieldCount() {
        assertFailsWith<IllegalArgumentException> { EncryptedSecret.parse("tpsec:1:AAAA") }
        assertFailsWith<IllegalArgumentException> { EncryptedSecret.parse("tpsec:1:AAAA:BBBB:CCCC") }
    }

    @Test
    fun parseRejectsNonNumericVersion() {
        assertFailsWith<IllegalArgumentException> { EncryptedSecret.parse("tpsec:x:AAAA:BBBB") }
    }

    @Test
    fun parseRejectsBadBase64() {
        assertFailsWith<IllegalArgumentException> { EncryptedSecret.parse("tpsec:1:!!!!:BBBB") }
    }

    @Test
    fun parseRejectsEmptyComponents() {
        assertFailsWith<IllegalArgumentException> { EncryptedSecret.parse("tpsec:1::BBBB") }
    }
}

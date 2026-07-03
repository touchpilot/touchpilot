package dev.touchpilot.app.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LenientJsonTest {

    @Test
    fun leavesWellFormedJsonUnchanged() {
        val valid = """{"tool":"tap","args":{"text":"x"}}"""
        assertEquals(valid, LenientJson.repair(valid))
    }

    @Test
    fun dropsTrailingCommaBeforeClosingBrace() {
        assertEquals("""{"a":1}""", LenientJson.repair("""{"a":1,}"""))
    }

    @Test
    fun dropsTrailingCommaBeforeClosingBracket() {
        assertEquals("""[1,2]""", LenientJson.repair("""[1,2,]"""))
    }

    @Test
    fun keepsWhitespaceAndClosesAfterDroppedComma() {
        assertEquals("""{"a":1 }""", LenientJson.repair("""{"a":1, }"""))
    }

    @Test
    fun normalizesSmartQuotes() {
        assertEquals("""{"a":"b"}""", LenientJson.repair("{“a”:“b”}"))
    }

    @Test
    fun preservesCommaInsideStringValue() {
        // The comma inside "a,]" must survive; only the trailing comma is dropped.
        assertEquals("""{"t":"a,]"}""", LenientJson.repair("""{"t":"a,]",}"""))
    }

    @Test
    fun preservesBraceAndCommentMarkersInsideStringValue() {
        val input = """{"t":"has } and // and /* inside","x":1,}"""
        assertEquals("""{"t":"has } and // and /* inside","x":1}""", LenientJson.repair(input))
    }

    @Test
    fun stripsLineComment() {
        val repaired = LenientJson.repair("{\"a\":1 // pick this\n}")
        assertFalse("pick this" in repaired)
        assertTrue(repaired.trimEnd().endsWith("}"))
    }

    @Test
    fun stripsBlockComment() {
        assertEquals("""{"a":1}""", LenientJson.repair("""{"a":1/* note */}"""))
    }

    @Test
    fun handlesEscapedQuoteInsideString() {
        val input = """{"t":"he said \"hi\"",}"""
        assertEquals("""{"t":"he said \"hi\""}""", LenientJson.repair(input))
    }
}

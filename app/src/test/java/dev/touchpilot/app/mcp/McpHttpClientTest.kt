package dev.touchpilot.app.mcp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class McpHttpClientTest {

    private val client = McpHttpClient("http://example.invalid/mcp")

    @Test
    fun parsesPlainJsonBodyUnchanged() {
        val parsed = client.parseResponse(
            """{"jsonrpc":"2.0","id":1,"result":{"tools":[]}}"""
        )

        assertEquals(1, parsed.optInt("id"))
        assertTrue(parsed.has("result"))
    }

    @Test
    fun parsesSingleSseDataLine() {
        val sse = "data: {\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"ok\":true}}"
        val parsed = client.parseResponse(sse)

        assertEquals(2, parsed.optInt("id"))
        assertEquals(true, parsed.optJSONObject("result")?.optBoolean("ok"))
    }

    @Test
    fun parsesSingleEventWithMultilineDataPayload() {
        // SSE spec allows a single event to span multiple `data:` lines; they
        // join with `\n` within the event. This must keep working.
        val sse = """
            data: {
            data:   "jsonrpc": "2.0",
            data:   "id": 4,
            data:   "result": { "ok": true }
            data: }
        """.trimIndent()

        val parsed = client.parseResponse(sse)

        assertEquals(4, parsed.optInt("id"))
        assertTrue(parsed.has("result"))
    }

    @Test
    fun progressNotificationFollowedByResultPicksResult() {
        val sse = """
            event: progress
            data: {"jsonrpc":"2.0","method":"notifications/progress","params":{"progress":0.5}}

            event: message
            data: {"jsonrpc":"2.0","id":7,"result":{"tools":[{"name":"echo"}]}}
        """.trimIndent()

        val parsed = client.parseResponse(sse)

        assertEquals(7, parsed.optInt("id"))
        assertTrue(parsed.has("result"))
        assertEquals(
            "echo",
            parsed.optJSONObject("result")!!.optJSONArray("tools")!!.optJSONObject(0).optString("name")
        )
    }

    @Test
    fun multipleNotificationsThenResultPicksResult() {
        val sse = """
            data: {"jsonrpc":"2.0","method":"notifications/progress","params":{"progress":0.25}}

            data: {"jsonrpc":"2.0","method":"notifications/progress","params":{"progress":0.75}}

            data: {"jsonrpc":"2.0","id":3,"result":{"ok":true}}
        """.trimIndent()

        val parsed = client.parseResponse(sse)

        assertEquals(3, parsed.optInt("id"))
        assertTrue(parsed.has("result"))
    }

    @Test
    fun errorResponseEventIsPickedOverPriorNotifications() {
        val sse = """
            data: {"jsonrpc":"2.0","method":"notifications/progress","params":{"progress":1.0}}

            data: {"jsonrpc":"2.0","id":1,"error":{"code":-32601,"message":"Method not found"}}
        """.trimIndent()

        val parsed = client.parseResponse(sse)

        assertTrue(parsed.has("error"))
        assertEquals(-32601, parsed.optJSONObject("error")!!.optInt("code"))
        assertNull(parsed.optJSONObject("result"))
    }

    @Test
    fun responseEventIsPickedEvenWhenFollowedByTrailingNotification() {
        // Some servers stream a final progress notification after the result.
        // The response event still wins because it's the only one with
        // `result` or `error`.
        val sse = """
            data: {"jsonrpc":"2.0","id":5,"result":{"ok":true}}

            data: {"jsonrpc":"2.0","method":"notifications/progress","params":{"progress":1.0}}
        """.trimIndent()

        val parsed = client.parseResponse(sse)

        assertEquals(5, parsed.optInt("id"))
        assertTrue(parsed.has("result"))
    }

    @Test
    fun crlfEventSeparatorIsAccepted() {
        // Some servers (and proxies) use CRLF line endings; SSE event
        // boundaries must still split correctly.
        val sse = "data: {\"jsonrpc\":\"2.0\",\"method\":\"notifications/progress\",\"params\":{}}\r\n\r\n" +
            "data: {\"jsonrpc\":\"2.0\",\"id\":9,\"result\":{\"ok\":true}}"

        val parsed = client.parseResponse(sse)

        assertEquals(9, parsed.optInt("id"))
        assertTrue(parsed.has("result"))
    }

    @Test
    fun ignoresNonDataLinesInsideEvents() {
        // SSE may include `event:`, `id:`, `retry:`, comment lines, etc.;
        // only `data:` lines contribute to the payload of an event.
        val sse = """
            event: progress
            id: 1
            : keepalive
            data: {"jsonrpc":"2.0","method":"notifications/progress","params":{}}

            event: message
            id: 2
            retry: 1000
            data: {"jsonrpc":"2.0","id":11,"result":{"ok":true}}
        """.trimIndent()

        val parsed = client.parseResponse(sse)

        assertEquals(11, parsed.optInt("id"))
        assertTrue(parsed.has("result"))
    }

    @Test
    fun emptyBodyThrowsClearError() {
        val error = assertFailsWith<IllegalStateException> {
            client.parseResponse("")
        }
        assertTrue(error.message!!.startsWith("MCP response did not contain a JSON object"))
    }

    @Test
    fun bodyWithoutAnyJsonThrowsClearError() {
        val error = assertFailsWith<IllegalStateException> {
            client.parseResponse(
                """
                event: heartbeat
                : keepalive

                event: heartbeat
                : keepalive
                """.trimIndent()
            )
        }
        assertTrue(error.message!!.startsWith("MCP response did not contain a JSON object"))
    }

    @Test
    fun invalidJsonEventsAreSkippedAndValidResponseStillWins() {
        val sse = """
            data: {not-json-at-all

            data: {"jsonrpc":"2.0","id":13,"result":{"ok":true}}
        """.trimIndent()

        val parsed = client.parseResponse(sse)

        assertEquals(13, parsed.optInt("id"))
        assertTrue(parsed.has("result"))
    }
}

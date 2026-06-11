package dev.touchpilot.app

import kotlin.test.Test
import kotlin.test.assertEquals

class McpResultStoreTest {
    @Test
    fun defaultMessageWhenNoMcpCallRecorded() {
        val store = McpResultStore()

        assertEquals(McpResultStore.Default, store.forMcp())
    }

    @Test
    fun recordingMcpResultUpdatesLatestValue() {
        val store = McpResultStore()

        store.recordMcpResult("MCP call failed: connection refused")

        assertEquals("MCP call failed: connection refused", store.forMcp())
    }

    @Test
    fun overwritingMcpResultReplacesPreviousValue() {
        val store = McpResultStore()

        store.recordMcpResult("mcp value")
        store.recordMcpResult("mcp value v2")

        assertEquals("mcp value v2", store.forMcp())
    }
}

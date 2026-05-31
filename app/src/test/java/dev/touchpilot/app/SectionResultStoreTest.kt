package dev.touchpilot.app

import kotlin.test.Test
import kotlin.test.assertEquals

class SectionResultStoreTest {
    @Test
    fun defaultsAreSectionSpecific() {
        val store = SectionResultStore()

        assertEquals(SectionResultStore.ToolsDefault, store.forTools())
        assertEquals(SectionResultStore.McpDefault, store.forMcp())
        assertEquals(SectionResultStore.LogsDefault, store.forLogs())
    }

    @Test
    fun recordingToolsResultDoesNotLeakIntoMcpOrLogs() {
        val store = SectionResultStore()

        store.recordToolsResult("observe_screen({}) -> true: snapshot length=42")

        assertEquals("observe_screen({}) -> true: snapshot length=42", store.forTools())
        assertEquals(SectionResultStore.McpDefault, store.forMcp())
        assertEquals(SectionResultStore.LogsDefault, store.forLogs())
    }

    @Test
    fun recordingMcpResultDoesNotLeakIntoToolsOrLogs() {
        val store = SectionResultStore()

        store.recordMcpResult("MCP call failed: connection refused")

        assertEquals(SectionResultStore.ToolsDefault, store.forTools())
        assertEquals("MCP call failed: connection refused", store.forMcp())
        assertEquals(SectionResultStore.LogsDefault, store.forLogs())
    }

    @Test
    fun recordingLogsResultDoesNotLeakIntoToolsOrMcp() {
        val store = SectionResultStore()

        store.recordLogsResult("Debug trace exported: /sdcard/traces/touchpilot.txt")

        assertEquals(SectionResultStore.ToolsDefault, store.forTools())
        assertEquals(SectionResultStore.McpDefault, store.forMcp())
        assertEquals("Debug trace exported: /sdcard/traces/touchpilot.txt", store.forLogs())
    }

    @Test
    fun sequentialWritesPreserveIndependentSectionState() {
        val store = SectionResultStore()

        store.recordToolsResult("tools-1")
        store.recordMcpResult("mcp-1")
        store.recordLogsResult("logs-1")
        store.recordToolsResult("tools-2")

        assertEquals("tools-2", store.forTools())
        assertEquals("mcp-1", store.forMcp())
        assertEquals("logs-1", store.forLogs())
    }

    @Test
    fun overwritingASectionDoesNotResetOtherSectionsToTheirDefault() {
        val store = SectionResultStore()

        store.recordToolsResult("tools value")
        store.recordMcpResult("mcp value")
        store.recordLogsResult("logs value")
        store.recordMcpResult("mcp value v2")

        assertEquals("tools value", store.forTools())
        assertEquals("mcp value v2", store.forMcp())
        assertEquals("logs value", store.forLogs())
    }
}

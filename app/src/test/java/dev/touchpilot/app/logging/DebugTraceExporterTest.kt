package dev.touchpilot.app.logging

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DebugTraceExporterTest {

    @Test
    fun buildContentContainsHeader() {
        val content = DebugTraceExporter.buildContent(
            timestamp = "20240101-120000",
            isAccessibilityConnected = false,
            toolLog = "No developer logs yet.",
            screenSnapshot = "Home screen"
        )

        assertContains(content, "TouchPilot debug trace")
    }

    @Test
    fun buildContentEmbeddsTimestamp() {
        val content = DebugTraceExporter.buildContent(
            timestamp = "20240101-120000",
            isAccessibilityConnected = false,
            toolLog = "No developer logs yet.",
            screenSnapshot = "Home screen"
        )

        assertContains(content, "timestamp=20240101-120000")
    }

    @Test
    fun buildContentReportsAccessibilityConnectedTrue() {
        val content = DebugTraceExporter.buildContent(
            timestamp = "20240101-120000",
            isAccessibilityConnected = true,
            toolLog = "",
            screenSnapshot = ""
        )

        assertContains(content, "Accessibility connected=true")
    }

    @Test
    fun buildContentReportsAccessibilityConnectedFalse() {
        val content = DebugTraceExporter.buildContent(
            timestamp = "20240101-120000",
            isAccessibilityConnected = false,
            toolLog = "",
            screenSnapshot = ""
        )

        assertContains(content, "Accessibility connected=false")
    }

    @Test
    fun buildContentIncludesToolLogVerbatim() {
        val toolLog = "[12:00:00] observe_screen() -> ok: snapshot length=42"
        val content = DebugTraceExporter.buildContent(
            timestamp = "20240101-120000",
            isAccessibilityConnected = false,
            toolLog = toolLog,
            screenSnapshot = ""
        )

        assertContains(content, toolLog)
    }

    @Test
    fun buildContentIncludesCurrentScreenSection() {
        val content = DebugTraceExporter.buildContent(
            timestamp = "20240101-120000",
            isAccessibilityConnected = false,
            toolLog = "",
            screenSnapshot = "Home screen content"
        )

        assertContains(content, "Current screen")
        assertContains(content, "Home screen content")
    }

    @Test
    fun buildContentRedactsSensitiveTextInScreenSnapshot() {
        val sensitiveSnapshot = "Login form: password=hunter2"
        val content = DebugTraceExporter.buildContent(
            timestamp = "20240101-120000",
            isAccessibilityConnected = false,
            toolLog = "",
            screenSnapshot = sensitiveSnapshot
        )

        assertFalse(content.contains("hunter2"), "Sensitive text must be redacted from screen snapshot")
    }

    @Test
    fun buildContentContainsAllSections() {
        val content = DebugTraceExporter.buildContent(
            timestamp = "20240101-120000",
            isAccessibilityConnected = true,
            toolLog = "some log",
            screenSnapshot = "some screen"
        )

        assertTrue(content.contains("Tool executions"))
        assertTrue(content.contains("Current screen"))
    }
}

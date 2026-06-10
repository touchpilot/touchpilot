package dev.touchpilot.app.screen.ocr

import kotlin.test.Test
import kotlin.test.assertEquals

class ScreenSignalsExtractorTest {
    @Test
    fun extractsCountsFromTypicalSettingsDump() {
        val dump = """
            TouchPilot screen snapshot
            - FrameLayout node_id="0" bounds="0,0,1080,2400"
              - LinearLayout node_id="0.0" bounds="0,0,1080,2400"
                - TextView node_id="0.0.0" text="Settings" bounds="0,0,1080,200"
                - RecyclerView node_id="0.0.1" bounds="0,200,1080,2400"
                  - LinearLayout node_id="0.0.1.0" clickable bounds="0,200,1080,400"
                    - TextView node_id="0.0.1.0.0" text="Network & internet" bounds="0,200,800,400"
                  - LinearLayout node_id="0.0.1.1" clickable bounds="0,400,1080,600"
                    - TextView node_id="0.0.1.1.0" text="Apps" bounds="0,400,800,600"
        """.trimIndent()

        val signals = ScreenSignalsExtractor.fromObserveDump(dump, packageName = "com.android.settings")

        assertEquals(8, signals.totalNodeCount)
        assertEquals(3, signals.visibleTextCount)
        assertEquals(2, signals.clickableNodeCount)
        assertEquals(0, signals.inputFieldCount)
        assertEquals(4, signals.maxTreeDepth)
        assertEquals("com.android.settings", signals.packageName)
    }

    @Test
    fun wordClickableInsideLabelDoesNotCountAsClickableNode() {
        val dump = """
            TouchPilot screen snapshot
            - TextView node_id="0" text="Not clickable yet"
            - LinearLayout node_id="1" clickable
        """.trimIndent()

        val signals = ScreenSignalsExtractor.fromObserveDump(dump)

        assertEquals(2, signals.totalNodeCount)
        // Only node 1 carries the bare `clickable` flag; the word inside node 0's
        // text value must not count. Before the fix this was 2.
        assertEquals(1, signals.clickableNodeCount)
    }

    @Test
    fun emptyDumpYieldsEmptySignals() {
        val signals = ScreenSignalsExtractor.fromObserveDump("TouchPilot screen snapshot")
        assertEquals(ObservedScreenSignals.EMPTY.copy(packageName = null), signals)
    }

    @Test
    fun blankTextDoesNotCountAsVisibleText() {
        val dump = """
            TouchPilot screen snapshot
            - TextView node_id="0" text=""
            - TextView node_id="1" desc=""
        """.trimIndent()

        val signals = ScreenSignalsExtractor.fromObserveDump(dump)

        assertEquals(2, signals.totalNodeCount)
        assertEquals(0, signals.visibleTextCount)
    }

    @Test
    fun editTextIsCountedAsInputField() {
        val dump = """
            TouchPilot screen snapshot
            - EditText node_id="0" text="user@example.com" focused
            - androidx.appcompat.widget.AppCompatAutoCompleteTextView node_id="1"
        """.trimIndent()

        val signals = ScreenSignalsExtractor.fromObserveDump(dump)

        assertEquals(2, signals.totalNodeCount)
        assertEquals(2, signals.inputFieldCount)
        assertEquals(1, signals.visibleTextCount)
    }

    @Test
    fun nonNodeLinesAreIgnored() {
        val dump = """
            TouchPilot screen snapshot
            some debug header line
            - FrameLayout node_id="0"
              - TextView node_id="0.0" text="hello"
        """.trimIndent()

        val signals = ScreenSignalsExtractor.fromObserveDump(dump)

        assertEquals(2, signals.totalNodeCount)
        assertEquals(1, signals.visibleTextCount)
    }
}

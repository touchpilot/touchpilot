package dev.touchpilot.app.localinference

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalModelBenchmarkSummaryLiveTest {
    private val tag = "LocalModelBenchmark"

    @Test
    fun logsBundledLiteRtBenchmarkSummary() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val summary = LocalModelBenchmarks.run(
            runtimeFactory = { LiteRtCommandModelRuntime(context) },
            iterationsPerScenario = 5
        )

        Log.i(tag, summary.toConsoleSummary())

        assertTrue("Bundled LiteRT model should be available for benchmark.", summary.available)
        assertTrue("Expected at least one inference scenario.", summary.scenarios.isNotEmpty())
    }
}

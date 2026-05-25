package dev.touchpilot.app.tools

import dev.touchpilot.app.screen.ScreenContext
import dev.touchpilot.app.security.SensitiveTextRedactor

class ToolVerifier {
    fun verify(
        toolName: String,
        args: Map<String, String>,
        result: ToolResult,
        before: ScreenContext,
        after: ScreenContext,
    ): ToolVerificationResult {
        if (!result.ok) {
            return ToolVerificationResult.Skipped("tool did not report success")
        }

        return when (toolName) {
            "observe_screen" -> ToolVerificationResult.Passed(
                reason = "screen observation returned ${result.message.length} character(s)",
                data = mapOf("snapshot_length" to result.message.length.toString())
            )
            "open_app" -> verifyOpenApp(args, after)
            "tap" -> verifyChangedOrFocused(before, after, "tap")
            "type_text" -> verifyTypeText(args, after)
            "scroll" -> verifyScroll(result, before, after)
            "press_back" -> verifyChangedOrFocused(before, after, "press_back")
            "press_home" -> verifyHome(after)
            "wait_for_ui" -> verifyWaitForUi(args, after)
            else -> ToolVerificationResult.Skipped("no verifier for $toolName")
        }
    }

    private fun verifyOpenApp(
        args: Map<String, String>,
        after: ScreenContext,
    ): ToolVerificationResult {
        val target = args["target"].orEmpty()
        if (target.isBlank()) {
            return ToolVerificationResult.Failed("open_app target was blank")
        }

        val packageName = after.packageName.orEmpty()
        val appLabel = after.appLabel.orEmpty()
        val matched = packageName.equals(target, ignoreCase = true) ||
            appLabel.equals(target, ignoreCase = true) ||
            appLabel.contains(target, ignoreCase = true)

        return if (matched) {
            ToolVerificationResult.Passed(
                reason = "foreground app matched requested target",
                data = mapOf(
                    "package" to packageName,
                    "app_label" to appLabel,
                ).redactedValues()
            )
        } else {
            ToolVerificationResult.Failed(
                reason = "foreground app did not match requested target",
                data = mapOf(
                    "target" to target,
                    "package" to packageName,
                    "app_label" to appLabel,
                ).redactedValues()
            )
        }
    }

    private fun verifyChangedOrFocused(
        before: ScreenContext,
        after: ScreenContext,
        toolName: String,
    ): ToolVerificationResult {
        val changed = !sameSnapshot(before, after)
        val focusChanged = before.focusedNodeId() != after.focusedNodeId()
        return if (changed || focusChanged) {
            ToolVerificationResult.Passed(
                reason = "$toolName changed screen or focus state",
                data = mapOf(
                    "screen_changed" to changed.toString(),
                    "focus_changed" to focusChanged.toString(),
                )
            )
        } else {
            ToolVerificationResult.Failed(
                reason = "$toolName reported success but screen/focus did not change",
                data = mapOf(
                    "screen_changed" to "false",
                    "focus_changed" to "false",
                )
            )
        }
    }

    private fun verifyTypeText(
        args: Map<String, String>,
        after: ScreenContext,
    ): ToolVerificationResult {
        val text = args["text"].orEmpty()
        val focused = after.nodes.firstOrNull { it.focused && it.isInputField }
        val inputWithText = after.inputFields.firstOrNull { it.text.raw == text }
        val received = focused?.text?.raw == text || inputWithText != null

        return if (received) {
            ToolVerificationResult.Passed(
                reason = "input field contains expected text",
                data = mapOf(
                    "text_length" to text.length.toString(),
                    "node_id" to (focused?.nodeId ?: inputWithText?.nodeId).orEmpty(),
                )
            )
        } else {
            ToolVerificationResult.Failed(
                reason = "no focused or visible input field contains the typed text",
                data = mapOf("text_length" to text.length.toString())
            )
        }
    }

    private fun verifyScroll(
        result: ToolResult,
        before: ScreenContext,
        after: ScreenContext,
    ): ToolVerificationResult {
        val resultChanged = result.data["screen_changed"]?.toBooleanStrictOrNull()
        val changed = resultChanged ?: !sameSnapshot(before, after)
        return if (changed) {
            ToolVerificationResult.Passed(
                reason = "scroll changed visible screen content",
                data = mapOf("screen_changed" to "true")
            )
        } else {
            ToolVerificationResult.Failed(
                reason = "scroll reported success but screen content did not change",
                data = mapOf("screen_changed" to "false")
            )
        }
    }

    private fun verifyHome(after: ScreenContext): ToolVerificationResult {
        val packageName = after.packageName.orEmpty().lowercase()
        val appLabel = after.appLabel.orEmpty().lowercase()
        val looksLikeLauncher = "launcher" in packageName || "launcher" in appLabel || "home" in appLabel
        return if (looksLikeLauncher) {
            ToolVerificationResult.Passed(
                reason = "foreground screen looks like launcher/home",
                data = mapOf("package" to packageName, "app_label" to appLabel).redactedValues()
            )
        } else {
            ToolVerificationResult.Failed(
                reason = "foreground screen does not look like launcher/home",
                data = mapOf("package" to packageName, "app_label" to appLabel).redactedValues()
            )
        }
    }

    private fun verifyWaitForUi(
        args: Map<String, String>,
        after: ScreenContext,
    ): ToolVerificationResult {
        val expected = args["text"].orEmpty()
        val present = after.nodes.any { it.text.raw.contains(expected, ignoreCase = true) }
        return if (present) {
            ToolVerificationResult.Passed(
                reason = "expected UI text is present",
                data = mapOf("text" to SensitiveTextRedactor.redact(expected))
            )
        } else {
            ToolVerificationResult.Failed(
                reason = "expected UI text is not present after wait",
                data = mapOf("text" to SensitiveTextRedactor.redact(expected))
            )
        }
    }

    private fun sameSnapshot(a: ScreenContext, b: ScreenContext): Boolean {
        if (a.nodes.size != b.nodes.size) return false
        return a.toRedactedJson() == b.toRedactedJson()
    }

    private fun ScreenContext.focusedNodeId(): String? {
        return nodes.firstOrNull { it.focused }?.nodeId
    }

    private fun Map<String, String>.redactedValues(): Map<String, String> {
        return mapValues { (_, value) -> SensitiveTextRedactor.redact(value) }
    }
}

sealed class ToolVerificationResult {
    abstract val status: ToolVerificationStatus
    abstract val reason: String
    abstract val data: Map<String, String>

    data class Passed(
        override val reason: String,
        override val data: Map<String, String> = emptyMap(),
    ) : ToolVerificationResult() {
        override val status = ToolVerificationStatus.PASSED
    }

    data class Failed(
        override val reason: String,
        override val data: Map<String, String> = emptyMap(),
    ) : ToolVerificationResult() {
        override val status = ToolVerificationStatus.FAILED
    }

    data class Skipped(
        override val reason: String,
        override val data: Map<String, String> = emptyMap(),
    ) : ToolVerificationResult() {
        override val status = ToolVerificationStatus.SKIPPED
    }
}

enum class ToolVerificationStatus(val wireName: String) {
    PASSED("passed"),
    FAILED("failed"),
    SKIPPED("skipped"),
}

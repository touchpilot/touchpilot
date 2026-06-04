package dev.touchpilot.app.tools

import dev.touchpilot.app.androidcontrol.ForegroundAppInfo

object WaitForApp {
    const val PackageArg = "package"
    const val LabelArg = "label"
    const val TimeoutArg = "timeout_ms"
    const val DefaultTimeoutMs = 5_000L
    const val MinTimeoutMs = 250L
    const val MaxTimeoutMs = 30_000L

    fun timeoutMs(args: Map<String, String>): Long {
        return (args[TimeoutArg]?.toLongOrNull() ?: DefaultTimeoutMs)
            .coerceIn(MinTimeoutMs, MaxTimeoutMs)
    }

    fun matches(args: Map<String, String>, info: ForegroundAppInfo): WaitForAppMatch {
        val expectedPackage = args[PackageArg].orEmpty().trim()
        val expectedLabel = args[LabelArg].orEmpty().trim()
        val packageMatches = expectedPackage.isNotBlank() &&
            info.packageName.equals(expectedPackage, ignoreCase = true)
        val labelMatches = expectedLabel.isNotBlank() &&
            info.appLabel.orEmpty().contains(expectedLabel, ignoreCase = true)

        return when {
            packageMatches -> WaitForAppMatch(true, PackageArg)
            labelMatches -> WaitForAppMatch(true, LabelArg)
            else -> WaitForAppMatch(false, "")
        }
    }

    fun successResult(args: Map<String, String>, info: ForegroundAppInfo, matchedBy: String): ToolResult {
        return ToolResult(
            ok = true,
            message = "waitForApp",
            data = resultData(args, info) + ("matched_by" to matchedBy)
        )
    }

    fun timeoutResult(args: Map<String, String>, info: ForegroundAppInfo, timeoutMs: Long): ToolResult {
        return ToolResult(
            ok = false,
            message = "Timed out waiting for foreground app: ${expectedSummary(args)}",
            data = resultData(args, info) + mapOf(
                "timed_out" to "true",
                "timeout_ms" to timeoutMs.toString(),
            )
        )
    }

    fun expectedSummary(args: Map<String, String>): String {
        return listOfNotNull(
            args[PackageArg]?.takeIf { it.isNotBlank() }?.let { "package=$it" },
            args[LabelArg]?.takeIf { it.isNotBlank() }?.let { "label=$it" },
        ).joinToString(", ")
    }

    private fun resultData(args: Map<String, String>, info: ForegroundAppInfo): Map<String, String> {
        return buildMap {
            args[PackageArg]?.takeIf { it.isNotBlank() }?.let { put("expected_package", it) }
            args[LabelArg]?.takeIf { it.isNotBlank() }?.let { put("expected_label", it) }
            put("accessibility_connected", info.accessibilityConnected.toString())
            info.packageName?.takeIf { it.isNotBlank() }?.let { put("package_name", it) }
            info.appLabel?.takeIf { it.isNotBlank() }?.let { put("app_label", it) }
            info.windowTitle?.takeIf { it.isNotBlank() }?.let { put("window_title", it) }
            info.activityClass?.takeIf { it.isNotBlank() }?.let { put("activity_class", it) }
            put("json", info.toJson().toString())
        }
    }
}

data class WaitForAppMatch(
    val matched: Boolean,
    val matchedBy: String,
)

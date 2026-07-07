package dev.touchpilot.app.tools

/**
 * Structured wait/retry policy for Android tools.
 *
 * The policy is deliberately local and deterministic: it never overrides
 * validation, policy, approval, ambiguity, or sensitive-data failures. It only
 * retries transient Android timing failures for tools that are safe to attempt
 * more than once.
 */
class AndroidToolRetryPolicy(
    private val configs: Map<String, ToolRetryConfig> = defaultConfigs(),
    private val fallback: ToolRetryConfig = ToolRetryConfig()
) {
    fun configFor(toolName: String): ToolRetryConfig = configs[toolName] ?: fallback

    fun classify(result: ToolResult): ToolFailureCategory {
        if (result.ok) return ToolFailureCategory.NONE
        val message = result.message.lowercase()
        return when {
            "validation" in message ||
                "unknown tool" in message ||
                "unknown argument" in message ||
                "missing required" in message ||
                "invalid" in message ||
                "unsupported" in message -> ToolFailureCategory.NON_RETRYABLE
            "policy=" in message ||
                "blocked" in message ||
                "denied" in message ||
                "approval" in message ||
                "ambiguous" in message ||
                "sensitive" in message ||
                "not an editable input" in message ||
                "not found" in message ||
                "no scrollable" in message -> ToolFailureCategory.NON_RETRYABLE
            "not enabled" in message ||
                "no active window" in message ||
                "not connected" in message ||
                "timed out" in message ||
                "timeout" in message ||
                "unable to perform" in message ||
                "unable to focus" in message ||
                "screen did not change" in message ||
                "no editable focused input" in message ||
                "no settings activity" in message ||
                "openapp" in message ||
                "tap" == message ||
                "scroll" == message -> ToolFailureCategory.RETRYABLE_TRANSIENT
            else -> ToolFailureCategory.UNKNOWN
        }
    }

    fun shouldRetry(toolName: String, result: ToolResult, attempt: Int): RetryDecision {
        val config = configFor(toolName)
        val category = classify(result)
        val nextAttempt = attempt + 1
        val canRetry = !result.ok &&
            config.retryable &&
            category.retryable &&
            nextAttempt < config.maxAttempts

        return RetryDecision(
            retry = canRetry,
            category = category,
            reason = when {
                result.ok -> "tool succeeded"
                !config.retryable -> "tool is not retryable"
                !category.retryable -> "failure is ${category.wireName}"
                nextAttempt >= config.maxAttempts -> "max attempts reached"
                else -> "retryable ${category.wireName} failure"
            },
            nextAttempt = nextAttempt + 1,
            delayMs = if (canRetry) config.retryDelayMs else 0L,
        )
    }

    companion object {
        fun defaultConfigs(): Map<String, ToolRetryConfig> {
            val action = ToolRetryConfig(
                maxAttempts = 3,
                retryDelayMs = 250L,
                idleTimeoutMs = 1_500L,
                retryable = true,
                waitForIdleAfterSuccess = true,
            )
            return mapOf(
                "observe_screen" to ToolRetryConfig(maxAttempts = 1, retryable = false),
                "observe_screen_context" to ToolRetryConfig(maxAttempts = 1, retryable = false),
                "open_app" to action,
                "open_settings_panel" to action,
                "tap" to action,
                "long_press" to action,
                "double_tap" to action,
                "type_text" to action,
                "scroll" to action,
                // Single attempt: scroll_to_element already runs its own bounded
                // observe/scroll/match loop, so an outer retry would multiply the
                // scroll budget for no benefit.
                "scroll_to_element" to ToolRetryConfig(maxAttempts = 1, retryable = false),
                "swipe" to action,
                "drag_and_drop" to action,
                "press_back" to action,
                "press_home" to action,
                "recent_apps" to action,
                "wait_for_ui" to action.copy(waitForIdleAfterSuccess = false),
                "wait_for_idle" to ToolRetryConfig(maxAttempts = 1, retryable = false),
                "wait_for_app" to ToolRetryConfig(maxAttempts = 1, retryable = false),
                "wait_for_element" to ToolRetryConfig(maxAttempts = 1, retryable = false),
                "wait_for_element_gone" to ToolRetryConfig(maxAttempts = 1, retryable = false),
                "focus_input" to action,
                "get_foreground_app" to ToolRetryConfig(maxAttempts = 1, retryable = false),
                "find_element" to ToolRetryConfig(maxAttempts = 1, retryable = false),
                // Single attempt only. The tool flips the IME show mode via
                // softKeyboardController, which is idempotent and has no
                // navigation hazard, but retrying would still spend extra
                // wall-clock time for no behavioral benefit.
                "dismiss_keyboard" to ToolRetryConfig(
                    maxAttempts = 1,
                    retryable = false,
                ),
            )
        }
    }
}

data class ToolRetryConfig(
    val maxAttempts: Int = 1,
    val retryDelayMs: Long = 0L,
    val idleTimeoutMs: Long = 0L,
    val retryable: Boolean = false,
    val waitForIdleAfterSuccess: Boolean = false,
) {
    init {
        require(maxAttempts >= 1) { "maxAttempts must be at least 1" }
        require(retryDelayMs >= 0L) { "retryDelayMs must be non-negative" }
        require(idleTimeoutMs >= 0L) { "idleTimeoutMs must be non-negative" }
    }
}

enum class ToolFailureCategory(val wireName: String, val retryable: Boolean) {
    NONE("none", false),
    RETRYABLE_TRANSIENT("retryable_transient", true),
    NON_RETRYABLE("non_retryable", false),
    UNKNOWN("unknown", false),
}

data class RetryDecision(
    val retry: Boolean,
    val category: ToolFailureCategory,
    val reason: String,
    val nextAttempt: Int,
    val delayMs: Long,
)

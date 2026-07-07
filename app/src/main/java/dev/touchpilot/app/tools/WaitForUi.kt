package dev.touchpilot.app.tools

object WaitForUi {
    const val TextArg = "text"
    const val TimeoutArg = "timeout_ms"
    const val DefaultTimeoutMs = 5_000L

    /**
     * Log-safe description of the wait query. Text content is reduced to length so
     * sensitive query content (OTP codes, PINs, etc.) is never written to the
     * tool execution log.
     */
    fun logArgs(text: String, timeoutMs: Long): String {
        return "text_length=${text.length}, timeout_ms=$timeoutMs"
    }
}

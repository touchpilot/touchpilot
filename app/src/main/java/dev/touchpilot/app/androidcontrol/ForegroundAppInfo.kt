package dev.touchpilot.app.androidcontrol

import org.json.JSONObject

/**
 * Snapshot of the foreground Android app, returned by the
 * `get_foreground_app` tool (issue #87). All fields are nullable because
 * Android does not always expose them — for example `windowTitle` is often
 * empty on launcher screens, and `activityClass` is only populated when the
 * Accessibility service has observed a `TYPE_WINDOW_STATE_CHANGED` event from
 * the current window.
 *
 * The struct is the stable verification signal that the agent runner and
 * future post-action verifier (#86) consume; downstream code should depend on
 * this shape, not on raw `AccessibilityNodeInfo` calls.
 */
data class ForegroundAppInfo(
    val packageName: String? = null,
    val appLabel: String? = null,
    val windowTitle: String? = null,
    val activityClass: String? = null,
    val accessibilityConnected: Boolean = false
) {
    /**
     * True when the snapshot carries at least one identifying signal beyond
     * the connection bit. Useful for callers that want to distinguish a
     * "service attached but no foreground info available" case from a real
     * foreground reading.
     */
    val hasContent: Boolean
        get() = !packageName.isNullOrBlank() ||
            !appLabel.isNullOrBlank() ||
            !windowTitle.isNullOrBlank() ||
            !activityClass.isNullOrBlank()

    fun toJson(): JSONObject {
        return JSONObject().apply {
            putOrNull("packageName", packageName)
            putOrNull("appLabel", appLabel)
            putOrNull("windowTitle", windowTitle)
            putOrNull("activityClass", activityClass)
            put("accessibilityConnected", accessibilityConnected)
        }
    }

    private fun JSONObject.putOrNull(key: String, value: String?) {
        if (value.isNullOrBlank()) put(key, JSONObject.NULL) else put(key, value)
    }

    /**
     * Compact one-line summary suitable for the tool result's `message`
     * field and the action log. Avoids dumping the whole struct so logs stay
     * short and free of sensitive window titles.
     */
    fun summarize(): String {
        if (!accessibilityConnected) {
            return "TouchPilot Accessibility service is not connected."
        }
        if (!hasContent) {
            return "Foreground app is unknown."
        }
        val identity = appLabel?.takeIf { it.isNotBlank() }
            ?: packageName?.takeIf { it.isNotBlank() }
            ?: "current app"
        val window = windowTitle?.takeIf { it.isNotBlank() }
        return if (window != null && window != identity) {
            "Foreground: $identity ($window)"
        } else {
            "Foreground: $identity"
        }
    }

    companion object {
        val Disconnected: ForegroundAppInfo = ForegroundAppInfo(accessibilityConnected = false)
    }
}

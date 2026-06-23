package dev.touchpilot.app.demonstration

import android.content.SharedPreferences

/**
 * SharedPreferences keys and helpers for demonstration recording mode (issue #302).
 *
 * When enabled, each agent run captures per-step tool calls, arguments, and
 * screen context before/after every action.
 */
object DemonstrationPreferences {
    const val KEY_RECORDING_ENABLED = "demonstration_recording_enabled"
    const val KEY_AUTO_EXPORT = "demonstration_auto_export"
    const val KEY_CAPTURE_SCREEN_DELTA = "demonstration_capture_screen_delta"
    const val KEY_INCLUDE_FAILED_STEPS = "demonstration_include_failed_steps"
    const val KEY_MAX_STORED_SESSIONS = "demonstration_max_stored_sessions"

    fun isRecordingEnabled(preferences: SharedPreferences): Boolean {
        return preferences.getBoolean(KEY_RECORDING_ENABLED, false)
    }

    fun setRecordingEnabled(preferences: SharedPreferences, enabled: Boolean) {
        preferences.edit().putBoolean(KEY_RECORDING_ENABLED, enabled).apply()
    }

    fun isAutoExportEnabled(preferences: SharedPreferences): Boolean {
        return preferences.getBoolean(KEY_AUTO_EXPORT, false)
    }

    fun setAutoExportEnabled(preferences: SharedPreferences, enabled: Boolean) {
        preferences.edit().putBoolean(KEY_AUTO_EXPORT, enabled).apply()
    }

    fun isScreenDeltaEnabled(preferences: SharedPreferences): Boolean {
        return preferences.getBoolean(KEY_CAPTURE_SCREEN_DELTA, true)
    }

    fun setScreenDeltaEnabled(preferences: SharedPreferences, enabled: Boolean) {
        preferences.edit().putBoolean(KEY_CAPTURE_SCREEN_DELTA, enabled).apply()
    }

    fun includeFailedSteps(preferences: SharedPreferences): Boolean {
        return preferences.getBoolean(KEY_INCLUDE_FAILED_STEPS, true)
    }

    fun setIncludeFailedSteps(preferences: SharedPreferences, enabled: Boolean) {
        preferences.edit().putBoolean(KEY_INCLUDE_FAILED_STEPS, enabled).apply()
    }

    fun maxStoredSessions(preferences: SharedPreferences): Int {
        return preferences.getInt(KEY_MAX_STORED_SESSIONS, 50).coerceIn(1, 500)
    }

    fun setMaxStoredSessions(preferences: SharedPreferences, count: Int) {
        preferences.edit().putInt(KEY_MAX_STORED_SESSIONS, count.coerceIn(1, 500)).apply()
    }

    fun recordingConfig(preferences: SharedPreferences): DemonstrationRecordingConfig {
        return DemonstrationRecordingConfig(
            enabled = isRecordingEnabled(preferences),
            autoExport = isAutoExportEnabled(preferences),
            captureScreenDelta = isScreenDeltaEnabled(preferences),
            includeFailedSteps = includeFailedSteps(preferences),
            maxStoredSessions = maxStoredSessions(preferences),
        )
    }
}

data class DemonstrationRecordingConfig(
    val enabled: Boolean = false,
    val autoExport: Boolean = false,
    val captureScreenDelta: Boolean = true,
    val includeFailedSteps: Boolean = true,
    val maxStoredSessions: Int = 50,
)

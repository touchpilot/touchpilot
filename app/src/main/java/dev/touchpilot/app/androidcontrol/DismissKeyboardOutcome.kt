package dev.touchpilot.app.androidcontrol

/**
 * Result of [TouchPilotAccessibilityService.dismissKeyboard].
 *
 * The tool deliberately distinguishes "already hidden" from "hidden" so
 * agents can tell when they spent a Back dispatch versus when the keyboard
 * was never showing in the first place.
 */
sealed class DismissKeyboardOutcome {
    abstract val wasVisibleBefore: Boolean
    abstract val stillVisibleAfter: Boolean

    /** Keyboard was not visible when the tool ran. No system action dispatched. */
    data object AlreadyHidden : DismissKeyboardOutcome() {
        override val wasVisibleBefore = false
        override val stillVisibleAfter = false
    }

    /**
     * Keyboard was visible and the IME show mode has been flipped to hidden.
     *
     * [stillVisibleAfter] is *measured* after the show mode is restored: the
     * restore can re-trigger the platform auto-show while an editable field is
     * still focused, so the keyboard may be back on screen by the time the tool
     * returns. The agent uses this to decide whether to re-dispatch.
     */
    data class Hidden(override val stillVisibleAfter: Boolean) : DismissKeyboardOutcome() {
        override val wasVisibleBefore = true
    }

    /** The accessibility service is not connected. */
    data object NotConnected : DismissKeyboardOutcome() {
        override val wasVisibleBefore = false
        override val stillVisibleAfter = false
    }
}

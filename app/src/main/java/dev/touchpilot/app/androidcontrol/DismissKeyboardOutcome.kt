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

    /** Keyboard was visible and the IME show mode has been flipped to hidden. */
    data object Hidden : DismissKeyboardOutcome() {
        override val wasVisibleBefore = true
        override val stillVisibleAfter = false
    }

    /** The accessibility service is not connected. */
    data object NotConnected : DismissKeyboardOutcome() {
        override val wasVisibleBefore = false
        override val stillVisibleAfter = false
    }
}

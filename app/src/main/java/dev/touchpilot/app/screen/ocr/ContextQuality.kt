package dev.touchpilot.app.screen.ocr

/**
 * Classification of an Accessibility observation's usefulness for local screen
 * understanding. Strong context can drive summary + suggestions directly. Weak
 * context should be marked for OCR fallback. Empty context should not produce
 * guesses.
 */
sealed class ContextQuality {
    object Strong : ContextQuality()

    data class Weak(val reason: WeakReason) : ContextQuality()

    object Empty : ContextQuality()
}

enum class WeakReason {
    /** Tree has nodes but almost none expose readable text or content descriptions. */
    NO_VISIBLE_TEXT,

    /** Tree exposes text but no clickable affordances, so no actions can be suggested. */
    NO_CLICKABLE_NODES,

    /** Tree is shallow enough that the surface likely renders custom views. */
    SHALLOW_TREE,

    /** Tree has nodes, but the ratio of readable signals to total nodes is low. */
    MOSTLY_EMPTY,
}

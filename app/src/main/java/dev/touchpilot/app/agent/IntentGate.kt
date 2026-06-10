package dev.touchpilot.app.agent

import dev.touchpilot.app.memory.Skill
import dev.touchpilot.app.tools.SettingsPanelIntent

/**
 * Classifies a user task before the local reasoning core decides what to do
 * with it. The gate never executes a tool — it only routes the request into
 * one of the five Milestone-2 categories described in
 * https://github.com/touchpilot/touchpilot/issues/34.
 */
sealed class IntentDecision {
    abstract val reason: String

    /**
     * Deterministic local-router action — bypasses any local LLM.
     */
    data class ExactCommand(
        val tool: String,
        val args: Map<String, String>,
        override val reason: String
    ) : IntentDecision()

    /**
     * One of the loaded skills matches the request. The runner still gates the
     * tool calls behind the skill allowlist, policy, and approval flow.
     */
    data class KnownSkill(
        val skillId: String,
        val skillTitle: String,
        override val reason: String
    ) : IntentDecision()

    /**
     * Request matches an unsafe workflow (payments, secrets, destructive
     * actions, etc.). The reasoning core must refuse without invoking the
     * agent runner.
     */
    data class UnsafeRequest(
        override val reason: String,
        val userMessage: String
    ) : IntentDecision()

    /**
     * Ambiguous reference ("do the thing", "this", "that") — the agent needs
     * the user to clarify before it can plan anything safely.
     */
    data class ClarificationNeeded(
        override val reason: String,
        val clarification: String,
        val candidateLabels: List<String> = emptyList()
    ) : IntentDecision()

    /**
     * User is asking about the current Android screen ("what screen am I on",
     * "what can you do here", "suggest actions for this screen"). The
     * reasoning core answers from a local [dev.touchpilot.app.screen.ScreenContext]
     * summary instead of invoking the agent runner.
     */
    data class ScreenInquiry(
        override val reason: String
    ) : IntentDecision()

    /**
     * No deterministic path matched — defer to the configured local model path
     * for reasoning.
     */
    data class LocalModelNeeded(
        override val reason: String
    ) : IntentDecision()
}

fun interface IntentClassifier {
    fun classify(task: String, skills: List<Skill>): IntentDecision
}

class IntentGate : IntentClassifier {
    fun classify(task: String): IntentDecision = classify(task, emptyList())

    override fun classify(task: String, skills: List<Skill>): IntentDecision {
        val normalized = task.trim().lowercase()
        if (normalized.isBlank()) {
            return IntentDecision.ClarificationNeeded(
                reason = "empty task",
                clarification = "What would you like me to do?"
            )
        }

        detectUnsafe(normalized)?.let { return it }
        detectScreenInquiry(normalized)?.let { return it }
        detectExactCommand(normalized)?.let { return it }
        detectKnownSkill(normalized, skills)?.let { return it }
        detectAmbiguousReference(normalized)?.let { return it }

        return IntentDecision.LocalModelNeeded(reason = "no deterministic route matched")
    }

    private fun detectScreenInquiry(normalized: String): IntentDecision.ScreenInquiry? {
        val match = ScreenInquiryPatterns.firstOrNull { pattern -> pattern.containsMatchIn(normalized) }
            ?: return null
        return IntentDecision.ScreenInquiry(reason = "screen inquiry phrase '${match.pattern}'")
    }

    private fun detectUnsafe(normalized: String): IntentDecision.UnsafeRequest? {
        val match = UnsafePatterns.firstOrNull { (needle, _) -> needle in normalized }
            ?: return null
        return IntentDecision.UnsafeRequest(
            reason = match.second,
            userMessage = "TouchPilot will not run this because ${match.second}."
        )
    }

    private fun detectExactCommand(normalized: String): IntentDecision.ExactCommand? {
        SettingsPanelIntent.panelFromTask(normalized)?.let { panel ->
            return IntentDecision.ExactCommand(
                tool = "open_settings_panel",
                args = mapOf(SettingsPanelIntent.PanelArg to panel),
                reason = "settings panel phrase"
            )
        }
        OpenAppPattern.find(normalized)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
            ?.let { target ->
                return IntentDecision.ExactCommand(
                    tool = "open_app",
                    args = mapOf("target" to target),
                    reason = "open or launch phrase"
                )
            }
        LongPressPattern.find(normalized)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
            ?.let { text ->
                return IntentDecision.ExactCommand(
                    tool = "long_press",
                    args = mapOf("text" to text),
                    reason = "long press phrase"
                )
            }
        TapPattern.find(normalized)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
            ?.let { text ->
                return IntentDecision.ExactCommand(
                    tool = "tap",
                    args = mapOf("text" to text),
                    reason = "tap or press phrase"
                )
            }
        if (ScrollUpPattern.containsMatchIn(normalized)) {
            return IntentDecision.ExactCommand(
                tool = "scroll",
                args = mapOf("direction" to "backward"),
                reason = "scroll up phrase"
            )
        }
        if (ScrollPattern.containsMatchIn(normalized)) {
            return IntentDecision.ExactCommand(
                tool = "scroll",
                args = mapOf("direction" to "forward"),
                reason = "scroll phrase"
            )
        }
        SwipePattern.find(normalized)?.groupValues?.getOrNull(1)?.let { direction ->
            return IntentDecision.ExactCommand(
                tool = "swipe",
                args = mapOf("direction" to direction),
                reason = "swipe phrase"
            )
        }
        if (BackPattern.containsMatchIn(normalized)) {
            return IntentDecision.ExactCommand(
                tool = "press_back",
                args = emptyMap(),
                reason = "back phrase"
            )
        }
        if (HomePattern.containsMatchIn(normalized)) {
            return IntentDecision.ExactCommand(
                tool = "press_home",
                args = emptyMap(),
                reason = "home phrase"
            )
        }
        return null
    }

    private fun detectKnownSkill(normalized: String, skills: List<Skill>): IntentDecision.KnownSkill? {
        if (skills.isEmpty()) return null
        val match = skills.firstOrNull { skill ->
            val title = skill.title.lowercase()
            title.isNotBlank() && title in normalized
        } ?: return null
        return IntentDecision.KnownSkill(
            skillId = match.id,
            skillTitle = match.title,
            reason = "skill '${match.title}' title matched"
        )
    }

    private fun detectAmbiguousReference(normalized: String): IntentDecision.ClarificationNeeded? {
        val match = AmbiguousPhrases.firstOrNull { it in normalized } ?: return null
        return IntentDecision.ClarificationNeeded(
            reason = "ambiguous reference '$match'",
            clarification = "Can you describe what you would like me to do more specifically?"
        )
    }

    private companion object {
        // Mirrors DefaultActionPolicy.blockedWorkflow so the gate refuses early
        // and the policy layer cannot be bypassed by a deterministic route.
        val UnsafePatterns: List<Pair<String, String>> = listOf(
            "payment" to "payments are blocked",
            "pay " to "payments are blocked",
            "password" to "password workflows are blocked",
            "passcode" to "password workflows are blocked",
            "account recovery" to "account recovery workflows are blocked",
            "recover account" to "account recovery workflows are blocked",
            "factory reset" to "destructive settings changes are blocked",
            "erase all" to "destructive settings changes are blocked",
            "delete account" to "destructive account changes are blocked",
            "purchase" to "purchases are blocked",
            "buy now" to "purchases are blocked",
            "bank" to "banking or financial actions are blocked",
            "wire transfer" to "banking or financial actions are blocked",
            "transfer money" to "banking or financial actions are blocked"
        )

        val ScreenInquiryPatterns: List<Regex> = listOf(
            Regex("\\bwhat\\s+screen\\s+am\\s+i\\s+on\\b"),
            Regex("\\bwhat\\s+can\\s+you\\s+do\\s+here\\b"),
            Regex("\\bwhat\\s+can\\s+i\\s+do\\s+here\\b"),
            Regex("\\bwhat\\s+(actions|buttons|controls)\\s+(are|can\\s+i)\\s+\\w+"),
            Regex("\\bwhat\\s+(is|are)\\s+on\\s+(this|the)\\s+screen\\b"),
            Regex("\\b(summari[sz]e|describe)\\b[\\w\\s-]{0,40}?\\bscreen\\b"),
            Regex("\\bsuggest\\s+actions?\\s+(for|on)\\s+(this|the)\\s+screen\\b"),
            Regex("\\bwhat\\s+actions?\\s+can\\s+i\\s+take\\s+here\\b")
        )

        val AmbiguousPhrases: List<String> = listOf(
            "do the thing",
            "the usual",
            "as usual",
            "what i usually do",
            "do what i did",
            "you know what",
            "this thing",
            "that thing"
        )

        val OpenAppPattern: Regex = Regex("(?:open|launch)\\s+([\\w .-]+)")
        val LongPressPattern: Regex = Regex("(?:long[- ]press|long tap|press and hold)\\s+([\\w .-]+)")
        val TapPattern: Regex = Regex("(?:tap|press)\\s+([\\w .-]+)")

        // Word-boundary anchors keep bare navigation routes from triggering on
        // accidental substring hits like "feedback", "homemade", or "scrollbar".
        val ScrollUpPattern: Regex = Regex("\\bscroll\\s+up\\b")
        val ScrollPattern: Regex = Regex("\\bscroll\\b")
        // Captures the travel direction after "swipe" so the gesture routes to
        // the swipe tool instead of falling through to the local model. Allows
        // light filler ("swipe to the left") but keeps the direction adjacent
        // so "swipe the back button" does not get misread as a direction.
        val SwipePattern: Regex = Regex("\\bswipe\\s+(?:to\\s+)?(?:the\\s+)?(left|right|up|down)\\b")
        val BackPattern: Regex = Regex("\\bback\\b")
        val HomePattern: Regex = Regex("\\bhome\\b")
    }
}

package dev.touchpilot.app.agent

import dev.touchpilot.app.memory.Skill
import dev.touchpilot.app.memory.SkillRisk
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
        override val reason: String,
        /**
         * Match strength in `0.0..1.0`. Title/alias phrase hits are high
         * confidence; example and keyword overlap are lower. Surfaced so callers
         * and logs can see *why* a skill was chosen and how strong the match was.
         */
        val confidence: Double = 1.0
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

    /**
     * Matches the request against the enabled skills using registry data —
     * title, aliases, examples, and skill-specific keywords — and returns the
     * single best match with a confidence and reason.
     *
     * It runs *after* [detectExactCommand], so exact deterministic routes
     * (`back`, `home`, `scroll`, `tap …`, `open …`) always win and can never be
     * captured by a skill. Matching is deliberately conservative: phrases are
     * matched on word boundaries (so `sms` does not match `smshing`), weak
     * single-keyword hits must be distinctive, and high-risk skills never match
     * on keyword overlap alone — they require a title/alias/example phrase.
     */
    private fun detectKnownSkill(normalized: String, skills: List<Skill>): IntentDecision.KnownSkill? {
        if (skills.isEmpty()) return null
        val taskTokens = significantTokens(normalized)

        var best: ScoredSkill? = null
        for (skill in skills) {
            val scored = scoreSkill(normalized, taskTokens, skill) ?: continue
            // Strictly-greater keeps the first skill in load order on a tie.
            if (best == null || scored.confidence > best.confidence) {
                best = scored
            }
        }

        val winner = best ?: return null
        if (winner.confidence < MinSkillConfidence) return null
        return IntentDecision.KnownSkill(
            skillId = winner.skill.id,
            skillTitle = winner.skill.title,
            reason = winner.reason,
            confidence = winner.confidence
        )
    }

    private fun scoreSkill(normalized: String, taskTokens: Set<String>, skill: Skill): ScoredSkill? {
        val title = skill.title.trim()
        if (title.isNotBlank() && containsPhrase(normalized, title)) {
            return ScoredSkill(skill, TitleConfidence, "skill '${skill.title}' title matched")
        }

        val alias = skill.aliases
            .map { it.trim() }
            .filter { it.isNotBlank() && containsPhrase(normalized, it) }
            .maxByOrNull { it.length }
        if (alias != null) {
            return ScoredSkill(skill, AliasConfidence, "skill '${skill.title}' alias '$alias' matched")
        }

        bestExampleMatch(taskTokens, skill.examples)?.let { match ->
            return ScoredSkill(skill, match.confidence, "skill '${skill.title}' example '${match.example}' matched")
        }

        // Conservative for high-risk skills: never route them on loose keyword
        // overlap; they must match an explicit title/alias/example phrase above.
        if (skill.risk != SkillRisk.HIGH) {
            keywordMatch(taskTokens, skill)?.let { match ->
                return ScoredSkill(skill, match.confidence, "skill '${skill.title}' keyword '${match.keyword}' matched")
            }
        }
        return null
    }

    private fun bestExampleMatch(taskTokens: Set<String>, examples: List<String>): ExampleMatch? {
        if (taskTokens.isEmpty()) return null
        var best: ExampleMatch? = null
        for (example in examples) {
            val exampleTokens = significantTokens(example)
            if (exampleTokens.isEmpty()) continue
            val overlap = taskTokens.intersect(exampleTokens)
            if (overlap.size < 2) continue

            val confidence = when {
                // The user's phrase is fully contained in a known example (or
                // vice versa) — a strong, specific signal.
                taskTokens.size >= 2 && exampleTokens.containsAll(taskTokens) -> StrongExampleConfidence
                exampleTokens.size >= 2 && taskTokens.containsAll(exampleTokens) -> StrongExampleConfidence
                // Otherwise require a solid majority of the example's tokens.
                overlap.size >= ((exampleTokens.size * 3 + 4) / 5) -> ExampleConfidence
                else -> continue
            }
            if (best == null || confidence > best.confidence) {
                best = ExampleMatch(example, confidence)
            }
        }
        return best
    }

    private fun keywordMatch(taskTokens: Set<String>, skill: Skill): KeywordMatch? {
        if (taskTokens.isEmpty()) return null
        val keywords = skillKeywords(skill)
        val matched = taskTokens.intersect(keywords)
        return when {
            matched.size >= 2 -> KeywordMatch(matched.sorted().joinToString(", "), MultiKeywordConfidence)
            // A single keyword only counts when it is distinctive enough to not
            // be incidental noise.
            matched.size == 1 && matched.first().length >= DistinctiveKeywordLength ->
                KeywordMatch(matched.first(), SingleKeywordConfidence)
            else -> null
        }
    }

    /** Distinctive tokens drawn from the skill's title, aliases, and examples. */
    private fun skillKeywords(skill: Skill): Set<String> {
        val tokens = linkedSetOf<String>()
        tokens += significantTokens(skill.title)
        skill.aliases.forEach { tokens += significantTokens(it) }
        skill.examples.forEach { tokens += significantTokens(it) }
        return tokens.filter { it.length >= KeywordMinLength }.toSet()
    }

    private fun significantTokens(text: String): Set<String> {
        return normalizeWords(text)
            .split(' ')
            .filter { it.length >= 2 && it !in StopWords }
            .toSet()
    }

    /** Word-boundary phrase containment, tolerant of punctuation/spacing. */
    private fun containsPhrase(haystack: String, phrase: String): Boolean {
        val normalizedPhrase = normalizeWords(phrase)
        if (normalizedPhrase.isBlank()) return false
        return (" " + normalizeWords(haystack) + " ").contains(" $normalizedPhrase ")
    }

    private fun normalizeWords(text: String): String {
        return text.lowercase().replace(NonWord, " ").trim()
    }

    private data class ScoredSkill(val skill: Skill, val confidence: Double, val reason: String)
    private data class ExampleMatch(val example: String, val confidence: Double)
    private data class KeywordMatch(val keyword: String, val confidence: Double)

    private fun detectAmbiguousReference(normalized: String): IntentDecision.ClarificationNeeded? {
        val match = AmbiguousPhrases.firstOrNull { it in normalized } ?: return null
        return IntentDecision.ClarificationNeeded(
            reason = "ambiguous reference '$match'",
            clarification = "Can you describe what you would like me to do more specifically?"
        )
    }

    private companion object {
        // Skill-matching tuning. Phrase hits (title/alias) are confident;
        // example and keyword overlap are progressively weaker.
        const val TitleConfidence = 1.0
        const val AliasConfidence = 0.9
        const val StrongExampleConfidence = 0.85
        const val ExampleConfidence = 0.75
        const val MultiKeywordConfidence = 0.65
        const val SingleKeywordConfidence = 0.55
        const val MinSkillConfidence = 0.5
        const val KeywordMinLength = 3
        const val DistinctiveKeywordLength = 4

        val NonWord = Regex("[^a-z0-9]+")

        // Generic filler dropped before token comparison so matching keys on
        // distinctive words, not common command/connective words.
        val StopWords: Set<String> = setOf(
            "a", "an", "the", "to", "of", "for", "on", "in", "at", "is", "are",
            "am", "be", "and", "or", "my", "me", "i", "you", "it", "this", "that",
            "here", "there", "now", "please", "help", "with", "can", "could",
            "do", "go", "get", "set", "want", "need", "let", "us", "show", "open",
            "launch", "find"
        )

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
        val BackPattern: Regex = Regex("\\bback\\b")
        val HomePattern: Regex = Regex("\\bhome\\b")
    }
}

package dev.touchpilot.app.agent

import dev.touchpilot.app.memory.Skill
import dev.touchpilot.app.tools.SettingsPanelIntent
import java.util.Locale

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
        val normalized = task.trim().lowercase(Locale.US)
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
        // Detect recents before the open/launch pattern so phrases like
        // "open recents" or "open the app switcher" route to recent_apps
        // instead of being parsed as an open_app target.
        if (RecentsPhrases.any { it in normalized }) {
            return IntentDecision.ExactCommand(
                tool = "recent_apps",
                args = emptyMap(),
                reason = "recent apps phrase"
            )
        }
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
        // "press back/home" must route to system navigation before TapPattern,
        // which also accepts "press <target>" and would misread them as tap(text=…).
        if (PressBackPattern.containsMatchIn(normalized)) {
            return IntentDecision.ExactCommand(
                tool = "press_back",
                args = emptyMap(),
                reason = "press back phrase"
            )
        }
        if (PressHomePattern.containsMatchIn(normalized)) {
            return IntentDecision.ExactCommand(
                tool = "press_home",
                args = emptyMap(),
                reason = "press home phrase"
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
        val request = skillMatchRequest(normalized)
        val match = skills.asSequence()
            .mapNotNull { skill -> bestSkillMatch(skill, request) }
            .filter { skillMatch -> skillMatch.confidence >= SkillMatchThreshold }
            .sortedWith(
                compareByDescending<SkillMatch> { skillMatch -> skillMatch.confidence }
                    .thenBy { skillMatch -> skillMatch.skill.id }
            )
            .firstOrNull()
            ?: return null

        return IntentDecision.KnownSkill(
            skillId = match.skill.id,
            skillTitle = match.skill.title,
            reason = "skill '${match.skill.title}' ${match.reason} " +
                "(confidence=${match.confidence.formatConfidence()})",
            confidence = match.confidence
        )
    }

    private fun skillMatchRequest(task: String): SkillMatchRequest {
        val normalized = normalizeMatchText(task)
        return SkillMatchRequest(
            normalized = normalized,
            compact = compactMatchText(normalized),
            keywords = keywordsFrom(normalized)
        )
    }

    private fun bestSkillMatch(skill: Skill, request: SkillMatchRequest): SkillMatch? {
        return skillCandidates(skill)
            .asSequence()
            .mapNotNull { candidate -> matchCandidate(skill, candidate, request) }
            .sortedWith(
                compareByDescending<SkillMatch> { skillMatch -> skillMatch.confidence }
                    .thenBy { skillMatch -> skillMatch.candidateText }
            )
            .firstOrNull()
    }

    private fun skillCandidates(skill: Skill): List<SkillCandidate> {
        val candidates = mutableListOf<SkillCandidate>()
        fun add(source: String, text: String) {
            if (text.isNotBlank()) {
                candidates += SkillCandidate(source = source, text = text)
            }
        }

        add("title", skill.title)
        add("title", SkillSuffixPattern.replace(skill.title, "").trim())
        skill.aliases.forEach { alias -> add("alias", alias) }
        skill.examples.forEach { example -> add("example", example) }

        return candidates.distinctBy { candidate ->
            candidate.source to normalizeMatchText(candidate.text)
        }
    }

    private fun matchCandidate(
        skill: Skill,
        candidate: SkillCandidate,
        request: SkillMatchRequest
    ): SkillMatch? {
        val phrase = normalizeMatchText(candidate.text)
        val phraseKeywords = keywordsFrom(phrase)
        val compactPhrase = compactMatchText(phrase)
        if (!isUsefulSkillPhrase(phrase, compactPhrase, phraseKeywords)) return null

        if (phraseMatchesRequest(phrase, compactPhrase, request)) {
            val confidence = (candidate.phraseConfidence() + if (request.normalized == phrase) 0.04 else 0.0)
                .coerceAtMost(0.98)
            return SkillMatch(
                skill = skill,
                candidateText = candidate.text,
                confidence = confidence,
                reason = "matched ${candidate.source} '${candidate.text}'"
            )
        }

        val commonKeywords = phraseKeywords.intersect(request.keywords)
        if (commonKeywords.size < MinKeywordOverlap) return null

        val coverage = commonKeywords.size.toDouble() / minOf(phraseKeywords.size, request.keywords.size)
        if (coverage < KeywordCoverageThreshold) return null

        val confidence = (candidate.keywordConfidence() + (coverage * KeywordCoverageBonus))
            .coerceAtMost(candidate.phraseConfidence() - 0.03)
        return SkillMatch(
            skill = skill,
            candidateText = candidate.text,
            confidence = confidence,
            reason = "matched ${candidate.source} keywords ${commonKeywords.sorted().joinToString()}"
        )
    }

    private fun normalizeMatchText(value: String): String {
        return NonAlphanumericPattern
            .replace(value.lowercase(Locale.US), " ")
            .trim()
            .replace(WhitespacePattern, " ")
    }

    private fun compactMatchText(value: String): String {
        return value.filter { char -> char in 'a'..'z' || char in '0'..'9' }
    }

    private fun keywordsFrom(normalized: String): Set<String> {
        return normalized
            .split(" ")
            .asSequence()
            .map { token -> token.trim() }
            .filter { token -> token.length >= MinSingleTokenLength }
            .filter { token -> token !in StopWords }
            .toSet()
    }

    private fun isUsefulSkillPhrase(
        phrase: String,
        compactPhrase: String,
        phraseKeywords: Set<String>
    ): Boolean {
        if (phrase.isBlank()) return false
        if (phraseKeywords.isEmpty()) return compactPhrase.length >= MinCompactPhraseLength
        if (phraseKeywords.size > 1) return true
        val onlyKeyword = phraseKeywords.single()
        return onlyKeyword !in ReservedExactCommandWords
    }

    private fun phraseMatchesRequest(
        phrase: String,
        compactPhrase: String,
        request: SkillMatchRequest
    ): Boolean {
        val paddedRequest = " ${request.normalized} "
        val paddedPhrase = " $phrase "
        return paddedPhrase in paddedRequest ||
            (compactPhrase.length >= MinCompactPhraseLength && compactPhrase in request.compact)
    }

    private fun SkillCandidate.phraseConfidence(): Double {
        return when (source) {
            "alias" -> 0.92
            "example" -> 0.86
            else -> 0.82
        }
    }

    private fun SkillCandidate.keywordConfidence(): Double {
        return when (source) {
            "alias" -> 0.76
            "example" -> 0.72
            else -> 0.68
        }
    }

    private fun Double.formatConfidence(): String {
        return String.format(Locale.US, "%.2f", this)
    }

    private fun detectAmbiguousReference(normalized: String): IntentDecision.ClarificationNeeded? {
        val match = AmbiguousPhrases.firstOrNull { it in normalized } ?: return null
        return IntentDecision.ClarificationNeeded(
            reason = "ambiguous reference '$match'",
            clarification = "Can you describe what you would like me to do more specifically?"
        )
    }

    private data class SkillMatchRequest(
        val normalized: String,
        val compact: String,
        val keywords: Set<String>
    )

    private data class SkillCandidate(
        val source: String,
        val text: String
    )

    private data class SkillMatch(
        val skill: Skill,
        val candidateText: String,
        val confidence: Double,
        val reason: String
    )

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

        // Substring needles for the recents/overview screen. Kept specific so
        // generic words like "overview" do not steal unrelated requests.
        val RecentsPhrases: List<String> = listOf(
            "recent apps",
            "recent app",
            "recents",
            "app switcher",
            "task switcher",
            "switch apps",
            "switch app"
        )

        val OpenAppPattern: Regex = Regex("(?:open|launch)\\s+([\\w .-]+)")
        val LongPressPattern: Regex = Regex("(?:long[- ]press|long tap|press and hold)\\s+([\\w .-]+)")
        val TapPattern: Regex = Regex("(?:tap|press)\\s+([\\w .-]+)")
        val PressBackPattern: Regex = Regex("\\bpress\\s+back\\b")
        val PressHomePattern: Regex = Regex("\\bpress\\s+home\\b")

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

        const val SkillMatchThreshold = 0.65
        const val MinKeywordOverlap = 2
        const val KeywordCoverageThreshold = 0.5
        const val KeywordCoverageBonus = 0.12
        const val MinSingleTokenLength = 3
        const val MinCompactPhraseLength = 4

        val SkillSuffixPattern: Regex = Regex("\\s+skill\\s*$", RegexOption.IGNORE_CASE)
        val NonAlphanumericPattern: Regex = Regex("[^a-z0-9]+")
        val WhitespacePattern: Regex = Regex("\\s+")
        val StopWords: Set<String> = setOf(
            "a",
            "an",
            "and",
            "android",
            "app",
            "do",
            "for",
            "go",
            "help",
            "i",
            "in",
            "me",
            "my",
            "of",
            "on",
            "or",
            "please",
            "show",
            "skill",
            "task",
            "that",
            "the",
            "this",
            "to",
            "use",
            "with",
            "you"
        )
        val ReservedExactCommandWords: Set<String> = setOf(
            "back",
            "home",
            "launch",
            "open",
            "press",
            "scroll",
            "swipe",
            "tap",
            "type"
        )
    }
}

package dev.touchpilot.app.agent

data class ConversationalResponse(val message: String)

object ConversationalGate {
    private const val GreetingReply = "Hello, I am TouchPilot, how can I help you?"
    private const val HelpReply =
        "I can help you control Android apps, open settings, tap visible text, scroll, " +
            "go back or home, and use approved skills. What would you like to do?"

    private val Conversations: List<ConversationRule> = listOf(
        ConversationRule(
            pattern = Regex(
                "^(hello|hi|hey|hola|howdy|yo|good\\s+(morning|afternoon|evening|night))[\\s.!?,]*$",
                RegexOption.IGNORE_CASE
            ),
            reply = GreetingReply
        ),
        ConversationRule(
            pattern = Regex(
                "^(help|what\\s+can\\s+you\\s+do|how\\s+can\\s+you\\s+help)[\\s.!?,]*$",
                RegexOption.IGNORE_CASE
            ),
            reply = HelpReply
        )
    )

    fun respond(input: String): ConversationalResponse? {
        val normalized = input.trim()
        if (normalized.isEmpty()) return null
        return Conversations
            .firstOrNull { rule -> rule.pattern.matches(normalized) }
            ?.let { rule -> ConversationalResponse(rule.reply) }
    }

    private data class ConversationRule(
        val pattern: Regex,
        val reply: String
    )
}

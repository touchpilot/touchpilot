package dev.touchpilot.app.agent

data class ConversationalResponse(val message: String)

object ConversationalGate {
    private const val GreetingReply = "Hello, I am TouchPilot, how can I help you?"
    private const val HelpReply =
        "I can help you control Android apps, open settings, tap visible text, scroll, " +
            "go back or home, and use approved skills. What would you like to do?"

    private val Conversations: List<Pair<Regex, String>> = listOf(
        Regex(
            "^(hello|hi|hey|hola|howdy|yo|good\\s+(morning|afternoon|evening|night))[\\s.!?,]*$",
            RegexOption.IGNORE_CASE
        ) to GreetingReply,
        Regex(
            "^(help|what\\s+can\\s+you\\s+do|how\\s+can\\s+you\\s+help)[\\s.!?,]*$",
            RegexOption.IGNORE_CASE
        ) to HelpReply
    )

    fun respond(input: String): ConversationalResponse? {
        val normalized = input.trim()
        if (normalized.isEmpty()) return null
        for ((pattern, reply) in Conversations) {
            if (pattern.matches(normalized)) {
                return ConversationalResponse(reply)
            }
        }
        return null
    }
}

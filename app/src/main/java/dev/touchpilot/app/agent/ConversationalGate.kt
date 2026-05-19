package dev.touchpilot.app.agent

data class ConversationalResponse(val message: String)

object ConversationalGate {
    private val GreetingPattern = Regex(
        "^(hello|hi|hey|hola|howdy|yo|good\\s+(morning|afternoon|evening|night))[\\s.!?,]*$",
        RegexOption.IGNORE_CASE
    )

    private const val GreetingReply = "Hello, I am TouchPilot, how can I help you?"

    fun respond(input: String): ConversationalResponse? {
        val normalized = input.trim()
        if (normalized.isEmpty()) return null
        if (GreetingPattern.matches(normalized)) {
            return ConversationalResponse(GreetingReply)
        }
        return null
    }
}

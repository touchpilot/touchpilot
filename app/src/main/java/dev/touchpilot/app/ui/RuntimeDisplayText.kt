package dev.touchpilot.app.ui

import dev.touchpilot.app.agent.AgentProviderMode
import dev.touchpilot.app.localinference.LocalModelStatus

fun AgentProviderMode.label(): String {
    return when (this) {
        AgentProviderMode.LOCAL_MODEL -> "Local model with router fallback"
        AgentProviderMode.LOCAL_ROUTER -> "Local router"
    }
}

fun AgentProviderMode.description(): String {
    return when (this) {
        AgentProviderMode.LOCAL_MODEL ->
            "Use LiteRT command model first; fall back to deterministic routing if unavailable."
        AgentProviderMode.LOCAL_ROUTER ->
            "Use the deterministic router only. Predictable, no on-device model load."
    }
}

fun LocalModelStatus.shortLine(): String {
    return if (available) {
        "$runtime model available"
    } else {
        "$runtime fallback: ${message.substringBefore(';')}"
    }
}

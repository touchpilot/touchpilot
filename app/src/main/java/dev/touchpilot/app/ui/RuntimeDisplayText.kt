package dev.touchpilot.app.ui

import dev.touchpilot.app.agent.AgentProviderMode
import dev.touchpilot.app.localinference.LocalModelStatus

data class RuntimeIndicator(
    val mode: AgentProviderMode,
    val localModelStatus: LocalModelStatus,
)

fun AgentProviderMode.label(): String {
    return when (this) {
        AgentProviderMode.LOCAL_MODEL -> "Local LiteRT model"
        AgentProviderMode.LOCAL_ROUTER -> "Local router"
    }
}

fun AgentProviderMode.description(): String {
    return when (this) {
        AgentProviderMode.LOCAL_MODEL ->
            "Use the on-device LiteRT command model. If it cannot load or route, the run stops with a final answer."
        AgentProviderMode.LOCAL_ROUTER ->
            "Use deterministic on-device routing only. Predictable, no model load."
    }
}

fun LocalModelStatus.shortLine(): String {
    return if (available) {
        "$runtime model ready ($version)"
    } else {
        "$runtime unavailable: ${message.substringBefore(';')}"
    }
}

fun RuntimeIndicator.activePathLabel(): String {
    return when (mode) {
        AgentProviderMode.LOCAL_ROUTER -> "Deterministic router"
        AgentProviderMode.LOCAL_MODEL -> {
            if (localModelStatus.available) {
                "${localModelStatus.runtime} model"
            } else {
                "${localModelStatus.runtime} unavailable"
            }
        }
    }
}

fun RuntimeIndicator.settingsChipText(): String {
    return when (mode) {
        AgentProviderMode.LOCAL_ROUTER -> "local only"
        AgentProviderMode.LOCAL_MODEL -> {
            if (localModelStatus.available) "model ready" else "model unavailable"
        }
    }
}

fun RuntimeIndicator.settingsChipAccent(): Boolean {
    return when (mode) {
        AgentProviderMode.LOCAL_ROUTER -> true
        AgentProviderMode.LOCAL_MODEL -> localModelStatus.available
    }
}

fun RuntimeIndicator.chatContextStrip(skillTitle: String): String {
    return "Core: local only · ${activePathLabel()}   ·   Skill: $skillTitle"
}

fun RuntimeIndicator.workingDetail(): String {
    return "Core runtime: local only · ${activePathLabel()}"
}

fun RuntimeIndicator.welcomeDetail(): String {
    return when (mode) {
        AgentProviderMode.LOCAL_ROUTER ->
            "Local router is ready for simple Android actions. Core runtime is local only."
        AgentProviderMode.LOCAL_MODEL -> {
            if (localModelStatus.available) {
                "Local LiteRT model (${localModelStatus.version}) is ready. Core runtime is local only."
            } else {
                "Local model mode is selected but LiteRT is unavailable. " +
                    localModelStatus.message.substringBefore(';')
            }
        }
    }
}

fun RuntimeIndicator.settingsDetailBody(): String {
    return buildString {
        appendLine("Core agent runs on this device; no cloud is required for routing.")
        appendLine("Optional integrations (MCP, cloud profile) are configured separately in Settings.")
        appendLine()
        appendLine("Active path: ${activePathLabel()}")
        when (mode) {
            AgentProviderMode.LOCAL_ROUTER -> {
                append(
                    "LiteRT model: ${liteRtAvailabilityForRouterMode()} (not used in router mode)"
                )
            }
            AgentProviderMode.LOCAL_MODEL -> {
                appendLine("LiteRT model: ${localModelStatus.shortLine()}")
            }
        }
        if (localModelStatus.available) {
            appendLine()
            append("Version: ${localModelStatus.version}")
        }
    }
}

private fun RuntimeIndicator.liteRtAvailabilityForRouterMode(): String {
    return if (localModelStatus.available) "available" else "unavailable"
}

package dev.touchpilot.app.agent

import dev.touchpilot.app.security.SensitiveTextRedactor

data class ToolCallCardModel(
    val tool: String,
    val args: Map<String, String>,
    val policyStatus: ToolCallPolicyStatus,
    val resultStatus: ToolCallResultStatus,
    val message: String,
    val verificationStatus: String?,
    val verificationReason: String?
) {
    companion object {
        fun fromEvents(events: List<AgentEvent>): List<ToolCallCardModel> {
            val cards = mutableListOf<ToolCallCardModel>()

            events.forEach { event ->
                when (event) {
                    is AgentEvent.ToolRequested -> {
                        cards += ToolCallCardModel(
                            tool = event.tool,
                            args = SensitiveTextRedactor.redact(event.args),
                            policyStatus = ToolCallPolicyStatus.CHECKING,
                            resultStatus = ToolCallResultStatus.REQUESTED,
                            message = "Tool selected by ${event.source.name.lowercase().replace('_', ' ')}.",
                            verificationStatus = null,
                            verificationReason = null
                        )
                    }
                    is AgentEvent.ApprovalRequired -> {
                        val index = cards.lastOpenIndexFor(event.tool)
                        val updated = (cards.getOrNull(index) ?: event.newCard()).copy(
                            args = SensitiveTextRedactor.redact(event.args),
                            policyStatus = ToolCallPolicyStatus.APPROVAL_REQUIRED,
                            resultStatus = ToolCallResultStatus.WAITING,
                            message = event.userMessage.redacted()
                        )
                        cards.putOrAdd(index, updated)
                    }
                    is AgentEvent.ToolRunning -> {
                        val index = cards.lastOpenIndexFor(event.tool)
                        val existing = cards.getOrNull(index)
                        val updated = (existing ?: event.newCard()).copy(
                            args = SensitiveTextRedactor.redact(event.args),
                            policyStatus = if (existing?.policyStatus == ToolCallPolicyStatus.APPROVAL_REQUIRED) {
                                ToolCallPolicyStatus.APPROVED
                            } else {
                                ToolCallPolicyStatus.ALLOWED
                            },
                            resultStatus = ToolCallResultStatus.RUNNING,
                            message = "Tool is running."
                        )
                        cards.putOrAdd(index, updated)
                    }
                    is AgentEvent.ToolSucceeded -> {
                        cards.updateResult(
                            tool = event.tool,
                            ok = true,
                            message = event.message,
                            data = event.data
                        )
                    }
                    is AgentEvent.ToolFailed -> {
                        cards.updateResult(
                            tool = event.tool,
                            ok = false,
                            message = event.message,
                            data = event.data
                        )
                    }
                    is AgentEvent.PolicyBlocked -> {
                        val tool = event.tool ?: "policy"
                        val index = cards.lastOpenIndexFor(tool)
                        val updated = (cards.getOrNull(index) ?: ToolCallCardModel(
                            tool = tool,
                            args = emptyMap(),
                            policyStatus = ToolCallPolicyStatus.BLOCKED,
                            resultStatus = ToolCallResultStatus.BLOCKED,
                            message = event.userMessage.redacted(),
                            verificationStatus = null,
                            verificationReason = null
                        )).copy(
                            policyStatus = ToolCallPolicyStatus.BLOCKED,
                            resultStatus = ToolCallResultStatus.BLOCKED,
                            message = event.userMessage.redacted()
                        )
                        cards.putOrAdd(index, updated)
                    }
                    else -> Unit
                }
            }

            return cards
        }

        private fun AgentEvent.ApprovalRequired.newCard(): ToolCallCardModel {
            return ToolCallCardModel(
                tool = tool,
                args = SensitiveTextRedactor.redact(args),
                policyStatus = ToolCallPolicyStatus.APPROVAL_REQUIRED,
                resultStatus = ToolCallResultStatus.WAITING,
                message = userMessage.redacted(),
                verificationStatus = null,
                verificationReason = null
            )
        }

        private fun AgentEvent.ToolRunning.newCard(): ToolCallCardModel {
            return ToolCallCardModel(
                tool = tool,
                args = SensitiveTextRedactor.redact(args),
                policyStatus = ToolCallPolicyStatus.ALLOWED,
                resultStatus = ToolCallResultStatus.RUNNING,
                message = "Tool is running.",
                verificationStatus = null,
                verificationReason = null
            )
        }

        private fun MutableList<ToolCallCardModel>.updateResult(
            tool: String,
            ok: Boolean,
            message: String,
            data: Map<String, String>
        ) {
            val index = lastOpenIndexFor(tool)
            val verificationStatus = data["verification_status"]?.redacted()
            val verificationReason = data["verification_reason"]?.redacted()
            val status = if (ok) ToolCallResultStatus.SUCCEEDED else ToolCallResultStatus.FAILED
            val updated = (getOrNull(index) ?: ToolCallCardModel(
                tool = tool,
                args = emptyMap(),
                policyStatus = ToolCallPolicyStatus.ALLOWED,
                resultStatus = status,
                message = message.redacted(),
                verificationStatus = verificationStatus,
                verificationReason = verificationReason
            )).copy(
                policyStatus = if (getOrNull(index)?.policyStatus in setOf(
                        ToolCallPolicyStatus.APPROVAL_REQUIRED,
                        ToolCallPolicyStatus.APPROVED
                    )
                ) {
                    ToolCallPolicyStatus.APPROVED
                } else {
                    ToolCallPolicyStatus.ALLOWED
                },
                resultStatus = status,
                message = message.redacted(),
                verificationStatus = verificationStatus,
                verificationReason = verificationReason
            )
            putOrAdd(index, updated)
        }

        private fun List<ToolCallCardModel>.lastOpenIndexFor(tool: String): Int {
            return indexOfLast { card ->
                card.tool == tool && card.resultStatus !in setOf(
                    ToolCallResultStatus.SUCCEEDED,
                    ToolCallResultStatus.FAILED,
                    ToolCallResultStatus.BLOCKED
                )
            }
        }

        private fun MutableList<ToolCallCardModel>.putOrAdd(index: Int, card: ToolCallCardModel) {
            if (index >= 0) {
                this[index] = card
            } else {
                add(card)
            }
        }

        private fun String.redacted(): String = SensitiveTextRedactor.redact(this)
    }
}

enum class ToolCallPolicyStatus(val label: String) {
    CHECKING("Policy check"),
    ALLOWED("Allowed"),
    APPROVAL_REQUIRED("Approval required"),
    APPROVED("Approved"),
    BLOCKED("Blocked")
}

enum class ToolCallResultStatus(val label: String) {
    REQUESTED("Requested"),
    WAITING("Waiting"),
    RUNNING("Running"),
    SUCCEEDED("Succeeded"),
    FAILED("Failed"),
    BLOCKED("Not run")
}

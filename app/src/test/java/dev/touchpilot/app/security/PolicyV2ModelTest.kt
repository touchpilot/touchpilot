package dev.touchpilot.app.security

import dev.touchpilot.app.tools.ToolRisk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PolicyV2ModelTest {
    @Test
    fun mapsCurrentToolRiskToPolicyDecisionVocabulary() {
        assertEquals(PolicyDecisionKind.ALLOW, PolicyV2Defaults.decisionForToolRisk(ToolRisk.LOW))
        assertEquals(PolicyDecisionKind.ASK, PolicyV2Defaults.decisionForToolRisk(ToolRisk.MEDIUM))
        assertEquals(PolicyDecisionKind.ASK, PolicyV2Defaults.decisionForToolRisk(ToolRisk.HIGH))
        assertEquals(PolicyDecisionKind.BLOCK, PolicyV2Defaults.decisionForToolRisk(ToolRisk.BLOCKED))
    }

    @Test
    fun strictestDecisionWinsAcrossRules() {
        val evaluation = PolicyEvaluation.fromRules(
            listOf(
                PolicyV2Defaults.ruleForToolRisk(ToolRisk.LOW),
                PolicyV2Defaults.ruleForWorkflow(PolicyWorkflowClass.MESSAGE_SEND),
                PolicyV2Defaults.ruleForWorkflow(PolicyWorkflowClass.PAYMENT)
            )
        )

        assertEquals(PolicyDecisionKind.BLOCK, evaluation.decision)
        assertTrue(evaluation.userMessage.contains("payment"))
    }

    @Test
    fun unknownSensitiveWorkflowFailsClosed() {
        val rule = PolicyV2Defaults.ruleForWorkflow(PolicyWorkflowClass.UNKNOWN_SENSITIVE)

        assertEquals(PolicySubject.WORKFLOW, rule.subject)
        assertEquals(PolicyDecisionKind.BLOCK, rule.decision)
        assertEquals(PolicyRiskBand.BLOCKED, rule.riskBand)
    }

    @Test
    fun messageSendDefaultsToAskNotBlock() {
        val rule = PolicyV2Defaults.ruleForWorkflow(PolicyWorkflowClass.MESSAGE_SEND)

        assertEquals(PolicyDecisionKind.ASK, rule.decision)
        assertEquals(PolicyRiskBand.MEDIUM, rule.riskBand)
    }

    @Test
    fun modelCarriesRuleSubjectWorkflowAndRiskMetadata() {
        val rule = PolicyRule(
            id = "app-banking",
            subject = PolicySubject.APP,
            decision = PolicyDecisionKind.ASK,
            reason = "banking app requires approval",
            workflowClass = PolicyWorkflowClass.ACCOUNT_CHANGE,
            riskBand = PolicyRiskBand.HIGH
        )

        assertEquals("app-banking", rule.id)
        assertEquals(PolicySubject.APP, rule.subject)
        assertEquals(PolicyWorkflowClass.ACCOUNT_CHANGE, rule.workflowClass)
        assertEquals(PolicyRiskBand.HIGH, rule.riskBand)
    }
}

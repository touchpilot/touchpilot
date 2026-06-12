package dev.touchpilot.app.security

import dev.touchpilot.app.tools.ToolRisk
import dev.touchpilot.app.tools.ToolSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkflowClassifierTest {
    @Test
    fun classifiesPaymentIntentFromToolArguments() {
        val classes = WorkflowClassifier.classify(
            request(
                tool = "tap",
                args = mapOf("text" to "Pay now")
            )
        )

        assertTrue(PolicyWorkflowClass.PAYMENT in classes)
    }

    @Test
    fun doesNotClassifyScreenOnlyBankingKeywordsAsPayment() {
        val classes = WorkflowClassifier.classify(
            request(
                tool = "tap",
                args = mapOf("text" to "Settings"),
                activeScreen = "Banking app home screen"
            )
        )

        assertFalse(PolicyWorkflowClass.PAYMENT in classes)
        assertEquals(listOf(PolicyWorkflowClass.GENERAL), classes)
    }

    @Test
    fun classifiesSensitiveTextEntry() {
        val classes = WorkflowClassifier.classify(
            request(
                tool = "type_text",
                args = mapOf("text" to "password=secret")
            )
        )

        assertEquals(listOf(PolicyWorkflowClass.SENSITIVE_TEXT_ENTRY), classes)
    }

    @Test
    fun classifiesMessageSendWithScreenContext() {
        val classes = WorkflowClassifier.classify(
            request(
                tool = "tap",
                args = mapOf("text" to "Send"),
                activeScreen = "Messages conversation"
            )
        )

        assertEquals(listOf(PolicyWorkflowClass.MESSAGE_SEND), classes)
    }

    @Test
    fun blockedIntentRuleUsesHumanReadableReason() {
        val rule = WorkflowClassifier.blockedIntentRule(
            request(
                tool = "tap",
                args = mapOf("text" to "Pay now")
            )
        )

        assertEquals("payments are blocked", rule?.reason)
        assertEquals(PolicyDecisionKind.BLOCK, rule?.decision)
    }

    private fun request(
        tool: String,
        args: Map<String, String>,
        activeScreen: String = ""
    ): ToolPolicyRequest {
        return ToolPolicyRequest(
            tool = ToolSpec(
                name = tool,
                description = "Test tool",
                risk = ToolRisk.MEDIUM,
                arguments = emptyMap(),
                requiredArguments = emptySet()
            ),
            args = args,
            source = ToolSource.LOCAL_ROUTER,
            activeScreen = activeScreen
        )
    }
}

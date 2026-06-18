package dev.touchpilot.app.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkflowRiskClassifierTest {
    private fun classOf(
        toolName: String,
        args: Map<String, String> = emptyMap(),
        screenText: String = "",
    ): PolicyWorkflowClass {
        return WorkflowRiskClassifier.classify(toolName, args, screenText).workflowClass
    }

    @Test
    fun benignActionIsGeneral() {
        val result = WorkflowRiskClassifier.classify(
            toolName = "tap",
            args = mapOf("text" to "Settings"),
            screenText = "Home screen with apps",
        )

        assertEquals(PolicyWorkflowClass.GENERAL, result.workflowClass)
        assertTrue(result.matched.isEmpty(), result.matched.toString())
    }

    @Test
    fun classifiesPayment() {
        assertEquals(
            PolicyWorkflowClass.PAYMENT,
            classOf("tap", mapOf("text" to "Confirm"), screenText = "Checkout — pay now with credit card"),
        )
    }

    @Test
    fun classifiesPurchase() {
        assertEquals(
            PolicyWorkflowClass.PURCHASE,
            classOf("tap", mapOf("text" to "Buy now"), screenText = "Product page"),
        )
    }

    @Test
    fun classifiesMessageSendOnlyWithMessagingContext() {
        assertEquals(
            PolicyWorkflowClass.MESSAGE_SEND,
            classOf("tap", mapOf("text" to "Send"), screenText = "WhatsApp conversation"),
        )
        // "Send" without a messaging context is not a message send.
        assertEquals(
            PolicyWorkflowClass.GENERAL,
            classOf("tap", mapOf("text" to "Send"), screenText = "A generic form screen"),
        )
    }

    @Test
    fun classifiesDeletion() {
        assertEquals(
            PolicyWorkflowClass.DELETION,
            classOf("tap", mapOf("text" to "Reset"), screenText = "Factory reset this device"),
        )
    }

    @Test
    fun classifiesAccountChange() {
        assertEquals(
            PolicyWorkflowClass.ACCOUNT_CHANGE,
            classOf("tap", mapOf("text" to "Continue"), screenText = "Delete account permanently"),
        )
    }

    @Test
    fun classifiesAccountRecovery() {
        assertEquals(
            PolicyWorkflowClass.ACCOUNT_RECOVERY,
            classOf("tap", mapOf("text" to "Next"), screenText = "Reset password to recover account"),
        )
    }

    @Test
    fun classifiesPermissionChange() {
        assertEquals(
            PolicyWorkflowClass.PERMISSION_CHANGE,
            classOf("tap", mapOf("text" to "Allow"), screenText = "Grant permission to access contacts"),
        )
    }

    @Test
    fun classifiesSecuritySettings() {
        assertEquals(
            PolicyWorkflowClass.SECURITY_SETTINGS,
            classOf("tap", mapOf("text" to "Open"), screenText = "Security settings: screen lock and biometric"),
        )
    }

    @Test
    fun classifiesSensitiveTextEntryFromArgsAlone() {
        // No sensitive screen text; the secret is in the typed argument.
        val result = WorkflowRiskClassifier.classify(
            toolName = "type_text",
            args = mapOf("text" to "password: hunter2"),
            screenText = "A neutral screen",
        )

        assertEquals(PolicyWorkflowClass.SENSITIVE_TEXT_ENTRY, result.workflowClass)
    }

    @Test
    fun typeTextWithNonSensitiveValueIsNotFlagged() {
        assertEquals(
            PolicyWorkflowClass.GENERAL,
            classOf("type_text", mapOf("text" to "hello world"), screenText = "Notes app"),
        )
    }

    @Test
    fun classifiesSensitiveArgumentKeyWhenValueLooksBenign() {
        assertEquals(
            PolicyWorkflowClass.UNKNOWN_SENSITIVE,
            classOf("tap", mapOf("api_key" to "sk-test"), screenText = "Neutral screen"),
        )
        assertEquals(
            PolicyWorkflowClass.UNKNOWN_SENSITIVE,
            classOf("observe_screen", mapOf("private-key" to "MIIE"), screenText = ""),
        )
        assertEquals(
            PolicyWorkflowClass.UNKNOWN_SENSITIVE,
            classOf("tap", mapOf("credential" to "json-secret"), screenText = "Form"),
        )
    }

    @Test
    fun benignArgumentKeysRemainGeneral() {
        assertEquals(
            PolicyWorkflowClass.GENERAL,
            classOf("tap", mapOf("text" to "Settings", "target" to "OK"), screenText = "Home"),
        )
    }

    @Test
    fun ambiguousSensitiveInputFallsBackToUnknownSensitive() {
        assertEquals(
            PolicyWorkflowClass.UNKNOWN_SENSITIVE,
            classOf("tap", mapOf("text" to "Continue"), screenText = "Please verify your identity to proceed"),
        )
    }

    @Test
    fun strictestWorkflowWinsWhenMultipleMatch() {
        // A message-send tap on a payment screen: BLOCK (payment) must beat ASK (message send).
        val result = WorkflowRiskClassifier.classify(
            toolName = "tap",
            args = mapOf("text" to "Send"),
            screenText = "Send money via your bank — Messages",
        )

        assertEquals(PolicyWorkflowClass.PAYMENT, result.workflowClass)
        assertTrue(PolicyWorkflowClass.MESSAGE_SEND in result.matched, result.matched.toString())
        assertTrue(PolicyWorkflowClass.PAYMENT in result.matched, result.matched.toString())
    }

    @Test
    fun detectionIsCaseInsensitive() {
        assertEquals(
            PolicyWorkflowClass.PAYMENT,
            classOf("tap", mapOf("text" to "PAY NOW"), screenText = "CHECKOUT — PAY NOW"),
        )
    }

    @Test
    fun reasonMatchesPolicyV2Defaults() {
        val result = WorkflowRiskClassifier.classify(
            toolName = "tap",
            args = mapOf("text" to "Buy now"),
        )

        assertEquals(
            PolicyV2Defaults.ruleForWorkflow(PolicyWorkflowClass.PURCHASE).reason,
            result.reason,
        )
    }

    @Test
    fun classifiedWorkflowsResolveToCautiousDecisions() {
        // Every sensitive class the classifier emits must map to ASK or stricter.
        val sensitive = listOf(
            classOf("tap", mapOf("text" to "Pay"), screenText = "payment"),
            classOf("tap", mapOf("text" to "Send"), screenText = "telegram"),
            classOf("tap", screenText = "verify your identity"),
        )

        sensitive.forEach { workflowClass ->
            val decision = PolicyV2Defaults.decisionForWorkflow(workflowClass)
            assertTrue(
                decision.precedence >= PolicyDecisionKind.ASK.precedence,
                "$workflowClass resolved to $decision",
            )
        }
    }
}

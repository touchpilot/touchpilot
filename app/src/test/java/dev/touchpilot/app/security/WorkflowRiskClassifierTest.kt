package dev.touchpilot.app.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkflowRiskClassifierTest {
    private val classifier = WorkflowRiskClassifier()

    private fun classOf(
        tool: String,
        args: Map<String, String> = emptyMap(),
        app: String = "",
        screen: String = "",
        text: String = ""
    ): PolicyWorkflowClass = classifier.classify(
        WorkflowSignal(
            toolName = tool,
            args = args,
            activeApp = app,
            activeScreen = screen,
            screenText = text
        )
    ).primary

    @Test
    fun plainNavigationIsGeneralAndNotSensitive() {
        val result = classifier.classify(WorkflowSignal(toolName = "scroll", args = mapOf("direction" to "forward")))
        assertEquals(PolicyWorkflowClass.GENERAL, result.primary)
        assertFalse(result.isSensitive)
    }

    @Test
    fun tappingOkOnAnOrdinaryScreenStaysGeneral() {
        assertEquals(PolicyWorkflowClass.GENERAL, classOf("tap", mapOf("text" to "OK"), screen = "Photos"))
    }

    @Test
    fun secretLikeTypeTextIsSensitiveTextEntry() {
        // A six-digit value is treated as a secret by SensitiveTextRedactor
        // (e.g. an OTP), so entering it is sensitive text entry on its own.
        assertEquals(
            PolicyWorkflowClass.SENSITIVE_TEXT_ENTRY,
            classOf("type_text", mapOf("text" to "482913"))
        )
    }

    @Test
    fun typingIntoAPasswordFieldIsSensitiveEvenWithoutRawSecret() {
        // The value may already be redacted upstream; the target field label
        // still marks it as sensitive entry.
        assertEquals(
            PolicyWorkflowClass.SENSITIVE_TEXT_ENTRY,
            classOf("type_text", mapOf("text" to "[redacted]", "target_text" to "Password"))
        )
    }

    @Test
    fun paymentScreenIsPayment() {
        assertEquals(
            PolicyWorkflowClass.PAYMENT,
            classOf("tap", mapOf("text" to "Pay now"), app = "com.bank.app", screen = "Confirm payment")
        )
    }

    @Test
    fun checkoutIsPurchase() {
        assertEquals(
            PolicyWorkflowClass.PURCHASE,
            classOf("tap", mapOf("text" to "Place order"), screen = "Checkout")
        )
    }

    @Test
    fun tapSendInMessagingSurfaceIsMessageSend() {
        assertEquals(
            PolicyWorkflowClass.MESSAGE_SEND,
            classOf("tap", mapOf("text" to "Send"), app = "com.google.android.apps.messaging", screen = "New message")
        )
    }

    @Test
    fun naturalLanguageSendTextIsMessageSend() {
        assertEquals(
            PolicyWorkflowClass.MESSAGE_SEND,
            classOf("tap", mapOf("text" to "send a text to Alex"))
        )
    }

    @Test
    fun tapSendOutsideMessagingIsNotMessageSend() {
        // "Send" on a non-messaging surface (e.g. a form submit) must not be
        // misread as an outbound message.
        assertEquals(PolicyWorkflowClass.GENERAL, classOf("tap", mapOf("text" to "Send"), screen = "Feedback form"))
    }

    @Test
    fun deleteActionIsDeletion() {
        assertEquals(
            PolicyWorkflowClass.DELETION,
            classOf("tap", mapOf("text" to "Delete"), screen = "Files")
        )
    }

    @Test
    fun deleteAccountPrefersAccountChange() {
        // Both account-change and deletion fire; the more specific account
        // class wins the tie since both block by default.
        assertEquals(
            PolicyWorkflowClass.ACCOUNT_CHANGE,
            classOf("tap", mapOf("text" to "Delete account"), screen = "Account settings")
        )
    }

    @Test
    fun resetPasswordIsAccountRecovery() {
        assertEquals(
            PolicyWorkflowClass.ACCOUNT_RECOVERY,
            classOf("tap", mapOf("text" to "Reset password"), screen = "Forgot password")
        )
    }

    @Test
    fun permissionGrantIsPermissionChange() {
        assertEquals(
            PolicyWorkflowClass.PERMISSION_CHANGE,
            classOf("tap", mapOf("text" to "Allow access"), screen = "Location permission")
        )
    }

    @Test
    fun securityScreenIsSecuritySettings() {
        assertEquals(
            PolicyWorkflowClass.SECURITY_SETTINGS,
            classOf("tap", mapOf("text" to "Screen lock"), screen = "Security settings")
        )
    }

    @Test
    fun ambiguousIrreversibleMarkerFailsClosedToUnknownSensitive() {
        // No specific class matches, but the screen warns the action is
        // irreversible — conservatively sensitive, never general.
        assertEquals(
            PolicyWorkflowClass.UNKNOWN_SENSITIVE,
            classOf("tap", mapOf("text" to "Confirm"), screen = "This cannot be undone")
        )
    }

    @Test
    fun strictestClassWinsWhenPaymentAndPurchaseBothMatch() {
        val result = classifier.classify(
            WorkflowSignal(
                toolName = "tap",
                args = mapOf("text" to "Pay now"),
                activeScreen = "Checkout"
            )
        )
        // Payment and purchase both fire; payment is preferred by specificity
        // (both block by default).
        assertEquals(PolicyWorkflowClass.PAYMENT, result.primary)
        assertTrue(result.matches.any { it.workflowClass == PolicyWorkflowClass.PURCHASE })
        assertTrue(result.isSensitive)
    }

    @Test
    fun classificationReasonNamesTheWorkflowAndEvidence() {
        val result = classifier.classify(
            WorkflowSignal(toolName = "tap", args = mapOf("text" to "Pay now"), activeScreen = "Confirm payment")
        )
        assertTrue(result.reason.contains("payment"))
    }
}

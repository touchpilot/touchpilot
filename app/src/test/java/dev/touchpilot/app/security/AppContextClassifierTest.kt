package dev.touchpilot.app.security

import dev.touchpilot.app.androidcontrol.ForegroundAppInfo
import dev.touchpilot.app.tools.ToolRisk
import dev.touchpilot.app.tools.ToolSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppContextClassifierTest {
    @Test
    fun classifiesBankingFromScreenSummary() {
        val categories = AppContextClassifier.classify(
            request(activeScreen = "Banking app home screen")
        )

        assertEquals(listOf(PolicyAppCategory.BANKING), categories)
    }

    @Test
    fun classifiesBankingFromForegroundPackage() {
        val categories = AppContextClassifier.classify(
            request(
                foregroundApp = ForegroundAppInfo(
                    packageName = "com.chase.sig.android",
                    appLabel = "Chase",
                    accessibilityConnected = true
                )
            )
        )

        assertEquals(listOf(PolicyAppCategory.BANKING), categories)
    }

    @Test
    fun classifiesCheckoutScreen() {
        val categories = AppContextClassifier.classify(
            request(activeScreen = "Checkout payment screen with order total")
        )

        assertTrue(PolicyAppCategory.CHECKOUT_PAYMENT in categories)
    }

    @Test
    fun classifiesDestructiveSettingsScreen() {
        val categories = AppContextClassifier.classify(
            request(activeScreen = "System settings: factory reset phone")
        )

        assertEquals(listOf(PolicyAppCategory.DESTRUCTIVE_SETTINGS), categories)
    }

    @Test
    fun ignoresToolArgumentsWhenClassifyingAppContext() {
        val categories = AppContextClassifier.classify(
            request(
                tool = "tap",
                args = mapOf("text" to "Pay now"),
                activeScreen = "Calculator"
            )
        )

        assertTrue(categories.isEmpty())
    }

    @Test
    fun wordBoundaryNeedlesAvoidEmbeddedSubstringFalsePositives() {
        val emailClient = AppContextClassifier.classify(
            request(
                foregroundApp = ForegroundAppInfo(
                    packageName = "com.example.email.client",
                    appLabel = "Email Client",
                    accessibilityConnected = true
                )
            )
        )
        assertTrue(PolicyAppCategory.MESSAGING !in emailClient)

        val assignment = AppContextClassifier.classify(
            request(activeScreen = "Assignment due tomorrow")
        )
        assertTrue(PolicyAppCategory.MESSAGING !in assignment)

        val feedback = AppContextClassifier.classify(
            request(activeScreen = "Feedback form")
        )
        assertTrue(PolicyAppCategory.BANKING !in feedback)
    }

    @Test
    fun wordBoundaryNeedlesStillMatchIntendedContexts() {
        val categories = AppContextClassifier.classify(
            request(
                foregroundApp = ForegroundAppInfo(
                    packageName = "org.thoughtcrime.securesms",
                    appLabel = "Signal",
                    accessibilityConnected = true
                )
            )
        )
        assertEquals(listOf(PolicyAppCategory.MESSAGING), categories)
    }

    @Test
    fun appRulesOnlyAskNeverAllow() {
        val rules = AppContextClassifier.rules(
            request(activeScreen = "Banking app home screen")
        )

        assertEquals(1, rules.size)
        assertEquals(PolicySubject.APP, rules.single().subject)
        assertEquals(PolicyDecisionKind.ASK, rules.single().decision)
    }

    private fun request(
        tool: String = "tap",
        args: Map<String, String> = mapOf("text" to "Settings"),
        activeScreen: String = "",
        foregroundApp: ForegroundAppInfo? = null
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
            activeScreen = activeScreen,
            foregroundApp = foregroundApp
        )
    }
}

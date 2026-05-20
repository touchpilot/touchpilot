package dev.touchpilot.app.screen

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class ScreenRedactionTest {
    @Test
    fun testSensitivePackageDetection() {
        assertTrue(ScreenRedaction.isSensitivePackage("com.android.settings"))
        assertTrue(ScreenRedaction.isSensitivePackage("com.google.android.gms"))
        assertTrue(ScreenRedaction.isSensitivePackage("com.android.keychain"))
        assertFalse(ScreenRedaction.isSensitivePackage("com.example.app"))
        assertFalse(ScreenRedaction.isSensitivePackage(null))
    }
    
    @Test
    fun testSensitiveTextDetection() {
        assertTrue(ScreenRedaction.isSensitiveText("Enter your password"))
        assertTrue(ScreenRedaction.isSensitiveText("PIN code"))
        assertTrue(ScreenRedaction.isSensitiveText("Secret token"))
        assertTrue(ScreenRedaction.isSensitiveText("API Key"))
        assertTrue(ScreenRedaction.isSensitiveText("Account number"))
        assertTrue(ScreenRedaction.isSensitiveText("Credit card"))
        assertFalse(ScreenRedaction.isSensitiveText("Username"))
        assertFalse(ScreenRedaction.isSensitiveText("Email address"))
        assertFalse(ScreenRedaction.isSensitiveText(null))
    }
    
    @Test
    fun testSensitiveFieldDetection() {
        assertTrue(ScreenRedaction.isSensitiveField("Enter password", null))
        assertTrue(ScreenRedaction.isSensitiveField(null, "com.example:id/password_field"))
        assertTrue(ScreenRedaction.isSensitiveField("PIN", "com.example:id/pin"))
        assertFalse(ScreenRedaction.isSensitiveField("Username", "com.example:id/username"))
        assertFalse(ScreenRedaction.isSensitiveField(null, null))
    }
    
    @Test
    fun testRedactText() {
        assertEquals("***REDACTED***", ScreenRedaction.redactText("secret", true))
        assertEquals("public", ScreenRedaction.redactText("public", false))
    }
    
    @Test
    fun testRedactVisibleText() {
        val texts = listOf("Hello", "World", "Password: 123")
        
        val redactedSensitive = ScreenRedaction.redactVisibleText(texts, isSensitiveContext = true)
        assertTrue(redactedSensitive.all { it == "***REDACTED***" })
        
        val redactedNormal = ScreenRedaction.redactVisibleText(texts, isSensitiveContext = false)
        assertEquals("Hello", redactedNormal[0])
        assertEquals("World", redactedNormal[1])
        assertEquals("***REDACTED***", redactedNormal[2])
    }
    
    @Test
    fun testShouldMarkContextSensitive() {
        assertTrue(
            ScreenRedaction.shouldMarkContextSensitive(
                packageName = "com.android.settings",
                inputFields = emptyList(),
                windowTitle = null
            )
        )
        
        assertTrue(
            ScreenRedaction.shouldMarkContextSensitive(
                packageName = "com.example.app",
                inputFields = emptyList(),
                windowTitle = "Enter Password"
            )
        )
        
        val passwordField = InputField(
            hint = "Password",
            text = "secret",
            bounds = Rect(0, 0, 100, 50),
            resourceId = null,
            isEnabled = true,
            isFocused = false,
            isPassword = true,
            inputType = 129
        )
        
        assertTrue(
            ScreenRedaction.shouldMarkContextSensitive(
                packageName = "com.example.app",
                inputFields = listOf(passwordField),
                windowTitle = null
            )
        )
        
        val sensitiveHintField = InputField(
            hint = "Enter your PIN",
            text = "1234",
            bounds = Rect(0, 0, 100, 50),
            resourceId = null,
            isEnabled = true,
            isFocused = false,
            isPassword = false,
            inputType = 2
        )
        
        assertTrue(
            ScreenRedaction.shouldMarkContextSensitive(
                packageName = "com.example.app",
                inputFields = listOf(sensitiveHintField),
                windowTitle = null
            )
        )
        
        val normalField = InputField(
            hint = "Username",
            text = "john",
            bounds = Rect(0, 0, 100, 50),
            resourceId = null,
            isEnabled = true,
            isFocused = false,
            isPassword = false,
            inputType = 1
        )
        
        assertFalse(
            ScreenRedaction.shouldMarkContextSensitive(
                packageName = "com.example.app",
                inputFields = listOf(normalField),
                windowTitle = "Login"
            )
        )
    }
    
    @Test
    fun testCaseInsensitiveSensitiveDetection() {
        assertTrue(ScreenRedaction.isSensitiveText("PASSWORD"))
        assertTrue(ScreenRedaction.isSensitiveText("PaSsWoRd"))
        assertTrue(ScreenRedaction.isSensitiveText("password"))
    }
}

package dev.touchpilot.app.screen

import org.json.JSONObject
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RectTest {
    @Test
    fun testRectDimensions() {
        val rect = Rect(left = 10, top = 20, right = 110, bottom = 120)
        assertEquals(100, rect.width)
        assertEquals(100, rect.height)
        assertFalse(rect.isEmpty())
    }
    
    @Test
    fun testEmptyRect() {
        val rect = Rect(left = 10, top = 20, right = 10, bottom = 20)
        assertTrue(rect.isEmpty())
    }
    
    @Test
    fun testRectJsonSerialization() {
        val rect = Rect(left = 10, top = 20, right = 110, bottom = 120)
        val json = rect.toJson()
        val restored = Rect.fromJson(json)
        
        assertEquals(rect, restored)
    }
}

class ClickableNodeTest {
    @Test
    fun testClickableNodeCreation() {
        val node = ClickableNode(
            label = "Submit",
            contentDescription = "Submit button",
            bounds = Rect(0, 0, 100, 50),
            resourceId = "com.example:id/submit",
            isEnabled = true,
            isClickable = true,
            isFocusable = true,
            className = "android.widget.Button"
        )
        
        assertEquals("Submit", node.label)
        assertTrue(node.isClickable)
    }
    
    @Test
    fun testClickableNodeJsonSerialization() {
        val node = ClickableNode(
            label = "Submit",
            contentDescription = "Submit button",
            bounds = Rect(0, 0, 100, 50),
            resourceId = "com.example:id/submit",
            isEnabled = true,
            isClickable = true,
            isFocusable = true,
            className = "android.widget.Button"
        )
        
        val json = node.toJson()
        val restored = ClickableNode.fromJson(json)
        
        assertEquals(node, restored)
    }
    
    @Test
    fun testClickableNodeWithNullFields() {
        val node = ClickableNode(
            label = null,
            contentDescription = null,
            bounds = Rect(0, 0, 100, 50),
            resourceId = null,
            isEnabled = false,
            isClickable = false,
            isFocusable = false,
            className = null
        )
        
        val json = node.toJson()
        val restored = ClickableNode.fromJson(json)
        
        assertEquals(node.isEnabled, restored.isEnabled)
        assertEquals(node.bounds, restored.bounds)
    }
}

class InputFieldTest {
    @Test
    fun testPasswordFieldRedaction() {
        val field = InputField(
            hint = "Enter password",
            text = "secret123",
            bounds = Rect(0, 0, 200, 50),
            resourceId = "com.example:id/password",
            isEnabled = true,
            isFocused = false,
            isPassword = true,
            inputType = 129
        )
        
        val redacted = field.redactedCopy()
        assertEquals("***REDACTED***", redacted.text)
        assertEquals("Enter password", redacted.hint)
    }
    
    @Test
    fun testNonPasswordFieldNotRedacted() {
        val field = InputField(
            hint = "Enter username",
            text = "john_doe",
            bounds = Rect(0, 0, 200, 50),
            resourceId = "com.example:id/username",
            isEnabled = true,
            isFocused = false,
            isPassword = false,
            inputType = 1
        )
        
        val redacted = field.redactedCopy()
        assertEquals("john_doe", redacted.text)
    }
    
    @Test
    fun testInputFieldJsonSerialization() {
        val field = InputField(
            hint = "Enter text",
            text = "sample",
            bounds = Rect(0, 0, 200, 50),
            resourceId = "com.example:id/input",
            isEnabled = true,
            isFocused = true,
            isPassword = false,
            inputType = 1
        )
        
        val json = field.toJson(redacted = false)
        val restored = InputField.fromJson(json)
        
        assertEquals(field, restored)
    }
    
    @Test
    fun testPasswordFieldJsonRedaction() {
        val field = InputField(
            hint = "Enter password",
            text = "secret123",
            bounds = Rect(0, 0, 200, 50),
            resourceId = "com.example:id/password",
            isEnabled = true,
            isFocused = false,
            isPassword = true,
            inputType = 129
        )
        
        val json = field.toJson(redacted = true)
        assertEquals("***REDACTED***", json.getString("text"))
    }
}

class ScrollableStateTest {
    @Test
    fun testScrollableStateCreation() {
        val state = ScrollableState(
            canScrollUp = true,
            canScrollDown = true,
            canScrollLeft = false,
            canScrollRight = false
        )
        
        assertTrue(state.canScrollUp)
        assertTrue(state.canScrollDown)
        assertFalse(state.canScrollLeft)
    }
    
    @Test
    fun testScrollableStateJsonSerialization() {
        val state = ScrollableState(
            canScrollUp = true,
            canScrollDown = false,
            canScrollLeft = true,
            canScrollRight = false
        )
        
        val json = state.toJson()
        val restored = ScrollableState.fromJson(json)
        
        assertEquals(state, restored)
    }
}

class ScreenContextTest {
    @Test
    fun testScreenContextCreation() {
        val context = createSampleScreenContext()
        
        assertEquals("Settings", context.appLabel)
        assertEquals("com.android.settings", context.packageName)
        assertEquals(2, context.visibleText.size)
        assertEquals(1, context.clickableNodes.size)
        assertEquals(1, context.inputFields.size)
    }
    
    @Test
    fun testScreenContextRedaction() {
        val context = createSampleScreenContext(withPassword = true)
        val redacted = context.redactedCopy()
        
        val passwordField = redacted.inputFields.first { it.isPassword }
        assertEquals("***REDACTED***", passwordField.text)
    }
    
    @Test
    fun testSensitiveScreenContextRedaction() {
        val context = createSampleScreenContext(isSensitive = true)
        val redacted = context.redactedCopy()
        
        assertTrue(redacted.visibleText.all { it == "***REDACTED***" })
    }
    
    @Test
    fun testScreenContextJsonSerialization() {
        val context = createSampleScreenContext()
        val json = context.toJson(redacted = false)
        val restored = ScreenContext.fromJson(json)
        
        assertEquals(context.appLabel, restored.appLabel)
        assertEquals(context.packageName, restored.packageName)
        assertEquals(context.visibleText.size, restored.visibleText.size)
        assertEquals(context.clickableNodes.size, restored.clickableNodes.size)
        assertEquals(context.inputFields.size, restored.inputFields.size)
    }
    
    @Test
    fun testScreenContextRedactedJsonString() {
        val context = createSampleScreenContext(withPassword = true)
        val jsonString = context.toRedactedJson()
        
        assertTrue(jsonString.contains("***REDACTED***"))
        assertFalse(jsonString.contains("secret123"))
    }
    
    @Test
    fun testScreenContextWithEmptyLists() {
        val context = ScreenContext(
            appLabel = "Empty App",
            packageName = "com.example.empty",
            windowTitle = "Empty Window",
            visibleText = emptyList(),
            clickableNodes = emptyList(),
            inputFields = emptyList(),
            scrollableState = ScrollableState(false, false, false, false),
            timestamp = System.currentTimeMillis(),
            isSensitive = false
        )
        
        val json = context.toJson()
        val restored = ScreenContext.fromJson(json)
        
        assertEquals(0, restored.visibleText.size)
        assertEquals(0, restored.clickableNodes.size)
        assertEquals(0, restored.inputFields.size)
    }
    
    @Test
    fun testScreenContextTimestamp() {
        val timestamp = System.currentTimeMillis()
        val context = ScreenContext(
            appLabel = "Test",
            packageName = "com.test",
            windowTitle = "Test Window",
            visibleText = emptyList(),
            clickableNodes = emptyList(),
            inputFields = emptyList(),
            scrollableState = ScrollableState(false, false, false, false),
            timestamp = timestamp,
            isSensitive = false
        )
        
        assertEquals(timestamp, context.timestamp)
    }
    
    private fun createSampleScreenContext(
        withPassword: Boolean = false,
        isSensitive: Boolean = false
    ): ScreenContext {
        val inputField = if (withPassword) {
            InputField(
                hint = "Enter password",
                text = "secret123",
                bounds = Rect(0, 100, 200, 150),
                resourceId = "com.example:id/password",
                isEnabled = true,
                isFocused = false,
                isPassword = true,
                inputType = 129
            )
        } else {
            InputField(
                hint = "Enter username",
                text = "john_doe",
                bounds = Rect(0, 100, 200, 150),
                resourceId = "com.example:id/username",
                isEnabled = true,
                isFocused = false,
                isPassword = false,
                inputType = 1
            )
        }
        
        return ScreenContext(
            appLabel = "Settings",
            packageName = "com.android.settings",
            windowTitle = "Wi-Fi preferences",
            visibleText = listOf("Wi-Fi", "Connected"),
            clickableNodes = listOf(
                ClickableNode(
                    label = "Save",
                    contentDescription = "Save button",
                    bounds = Rect(0, 0, 100, 50),
                    resourceId = "com.example:id/save",
                    isEnabled = true,
                    isClickable = true,
                    isFocusable = true,
                    className = "android.widget.Button"
                )
            ),
            inputFields = listOf(inputField),
            scrollableState = ScrollableState(
                canScrollUp = false,
                canScrollDown = true,
                canScrollLeft = false,
                canScrollRight = false
            ),
            timestamp = System.currentTimeMillis(),
            isSensitive = isSensitive
        )
    }
}

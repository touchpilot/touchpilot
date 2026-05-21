package dev.touchpilot.app.screen

import org.json.JSONObject

/**
 * Demonstration of JSON serialization capabilities for ScreenContext.
 * This can be run as a test or referenced to understand the API.
 */
object JsonSerializationDemo {
    
    fun demonstrateBasicSerialization() {
        println("=== Basic ScreenContext JSON Serialization ===")
        
        val context = ScreenContext(
            appLabel = "Gmail",
            packageName = "com.google.android.gm",
            windowTitle = "Inbox",
            nodes = listOf(
                ScreenNode(
                    nodeId = "compose_button",
                    role = NodeRole.BUTTON,
                    text = ScreenText.of("Compose"),
                    bounds = NodeBounds(left = 10, top = 100, right = 110, bottom = 150),
                    clickable = true,
                    enabled = true,
                    viewIdResourceName = "com.google.android.gm:id/compose",
                    className = "android.widget.Button"
                ),
                ScreenNode(
                    nodeId = "search_field",
                    role = NodeRole.INPUT,
                    text = ScreenText.of("Search"),
                    bounds = NodeBounds(left = 10, top = 160, right = 310, bottom = 210),
                    isInputField = true,
                    enabled = true,
                    viewIdResourceName = "com.google.android.gm:id/search",
                    className = "android.widget.EditText"
                )
            )
        )
        
        val json = context.toJson(redacted = false)
        println("JSON Output:")
        println(json.toString(2))
        
        println("\n=== Round-trip Test ===")
        val restored = ScreenContext.fromJson(json)
        println("Original appLabel: ${context.appLabel}")
        println("Restored appLabel: ${restored.appLabel}")
        println("Match: ${context.appLabel == restored.appLabel}")
        println("Original nodes: ${context.nodes.size}")
        println("Restored nodes: ${restored.nodes.size}")
        println("Match: ${context.nodes.size == restored.nodes.size}")
    }
    
    fun demonstrateRedaction() {
        println("\n=== Sensitive Data Redaction ===")
        
        val context = ScreenContext(
            appLabel = "Banking App",
            packageName = "com.example.bank",
            windowTitle = "Login",
            nodes = listOf(
                ScreenNode(
                    nodeId = "username_field",
                    role = NodeRole.INPUT,
                    text = ScreenText.of("john_doe"),
                    bounds = NodeBounds(left = 10, top = 100, right = 310, bottom = 150),
                    isInputField = true,
                    enabled = true,
                    viewIdResourceName = "com.example.bank:id/username",
                    className = "android.widget.EditText"
                ),
                ScreenNode(
                    nodeId = "password_field",
                    role = NodeRole.INPUT,
                    text = ScreenText.of("password: mySecretPassword123"),
                    bounds = NodeBounds(left = 10, top = 160, right = 310, bottom = 210),
                    isInputField = true,
                    sensitive = true,
                    enabled = true,
                    viewIdResourceName = "com.example.bank:id/password",
                    className = "android.widget.EditText"
                )
            )
        )
        
        println("Original password text: ${context.nodes[1].text.raw}")
        println("Original password displaySafe: ${context.nodes[1].text.displaySafe}")
        println("Contains sensitive content: ${context.containsSensitiveContent}")
        
        println("\n--- Redacted JSON ---")
        val redactedJson = context.toRedactedJson()
        println(redactedJson)
        
        println("\n--- Verification ---")
        println("Contains '[REDACTED]' in JSON: ${redactedJson.contains("[REDACTED]")}")
        println("Contains raw password in JSON: ${redactedJson.contains("mySecretPassword123")}")
        
        println("\n--- Redacted Copy ---")
        val redactedCopy = context.redactedCopy()
        println("Redacted copy password displaySafe: ${redactedCopy.nodes[1].text.displaySafe}")
        println("Redacted copy password raw: ${redactedCopy.nodes[1].text.raw}")
    }
    
    fun demonstrateNodeBoundsSerialization() {
        println("\n=== NodeBounds JSON Serialization ===")
        
        val bounds = NodeBounds(left = 10, top = 20, right = 110, bottom = 120)
        println("Original bounds: left=${bounds.left}, top=${bounds.top}, right=${bounds.right}, bottom=${bounds.bottom}")
        println("Width: ${bounds.width}, Height: ${bounds.height}")
        println("Center: (${bounds.centerX}, ${bounds.centerY})")
        
        val json = bounds.toJson()
        println("\nJSON: $json")
        
        val restored = NodeBounds.fromJson(json)
        println("\nRestored bounds: left=${restored.left}, top=${restored.top}, right=${restored.right}, bottom=${restored.bottom}")
        println("Match: ${bounds == restored}")
    }
    
    fun demonstrateScreenTextSerialization() {
        println("\n=== ScreenText JSON Serialization ===")
        
        val benignText = ScreenText.of("Hello World")
        println("Benign text - raw: '${benignText.raw}', displaySafe: '${benignText.displaySafe}', isSensitive: ${benignText.isSensitive}")
        
        val sensitiveText = ScreenText.of("password: hunter2")
        println("Sensitive text - raw: '${sensitiveText.raw}', displaySafe: '${sensitiveText.displaySafe}', isSensitive: ${sensitiveText.isSensitive}")
        
        println("\n--- JSON Serialization ---")
        val benignJson = benignText.toJson(redacted = false)
        println("Benign JSON: $benignJson")
        
        val sensitiveJson = sensitiveText.toJson(redacted = true)
        println("Sensitive JSON (redacted): $sensitiveJson")
        
        println("\n--- Verification ---")
        println("Sensitive JSON raw field is redacted: ${sensitiveJson.getString("raw") == "[REDACTED]"}")
    }
    
    fun runAllDemos() {
        demonstrateBasicSerialization()
        demonstrateRedaction()
        demonstrateNodeBoundsSerialization()
        demonstrateScreenTextSerialization()
    }
}

// Run this to see all demonstrations
fun main() {
    JsonSerializationDemo.runAllDemos()
}

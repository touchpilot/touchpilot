package dev.touchpilot.app.screen

object ScreenContextExample {
    fun createLoginScreenExample(): ScreenContext {
        return ScreenContext(
            appLabel = "Banking App",
            packageName = "com.example.bank",
            windowTitle = "Login",
            visibleText = listOf(
                "Welcome to Banking App",
                "Username",
                "Password",
                "Remember me",
                "Login",
                "Forgot password?"
            ),
            clickableNodes = listOf(
                ClickableNode(
                    label = "Login",
                    contentDescription = "Login button",
                    bounds = Rect(50, 400, 350, 480),
                    resourceId = "com.example.bank:id/login_button",
                    isEnabled = true,
                    isClickable = true,
                    isFocusable = true,
                    className = "android.widget.Button"
                ),
                ClickableNode(
                    label = "Forgot password?",
                    contentDescription = "Forgot password link",
                    bounds = Rect(100, 500, 300, 550),
                    resourceId = "com.example.bank:id/forgot_password",
                    isEnabled = true,
                    isClickable = true,
                    isFocusable = true,
                    className = "android.widget.TextView"
                )
            ),
            inputFields = listOf(
                InputField(
                    hint = "Enter username",
                    text = "john_doe",
                    bounds = Rect(50, 200, 350, 260),
                    resourceId = "com.example.bank:id/username",
                    isEnabled = true,
                    isFocused = false,
                    isPassword = false,
                    inputType = 1
                ),
                InputField(
                    hint = "Enter password",
                    text = "mySecretPassword123",
                    bounds = Rect(50, 280, 350, 340),
                    resourceId = "com.example.bank:id/password",
                    isEnabled = true,
                    isFocused = false,
                    isPassword = true,
                    inputType = 129
                )
            ),
            scrollableState = ScrollableState(
                canScrollUp = false,
                canScrollDown = false,
                canScrollLeft = false,
                canScrollRight = false
            ),
            timestamp = System.currentTimeMillis(),
            isSensitive = true
        )
    }
    
    fun createSettingsScreenExample(): ScreenContext {
        return ScreenContext(
            appLabel = "Settings",
            packageName = "com.android.settings",
            windowTitle = "Wi-Fi preferences",
            visibleText = listOf(
                "Wi-Fi",
                "Connected to HomeNetwork",
                "Available networks",
                "CoffeeShop_WiFi",
                "Guest_Network",
                "Add network"
            ),
            clickableNodes = listOf(
                ClickableNode(
                    label = "CoffeeShop_WiFi",
                    contentDescription = "CoffeeShop_WiFi network",
                    bounds = Rect(20, 200, 380, 260),
                    resourceId = "com.android.settings:id/network_item",
                    isEnabled = true,
                    isClickable = true,
                    isFocusable = true,
                    className = "android.widget.LinearLayout"
                ),
                ClickableNode(
                    label = "Add network",
                    contentDescription = "Add new network",
                    bounds = Rect(20, 500, 380, 560),
                    resourceId = "com.android.settings:id/add_network",
                    isEnabled = true,
                    isClickable = true,
                    isFocusable = true,
                    className = "android.widget.Button"
                )
            ),
            inputFields = emptyList(),
            scrollableState = ScrollableState(
                canScrollUp = false,
                canScrollDown = true,
                canScrollLeft = false,
                canScrollRight = false
            ),
            timestamp = System.currentTimeMillis(),
            isSensitive = false
        )
    }
    
    fun createEmailComposeExample(): ScreenContext {
        return ScreenContext(
            appLabel = "Gmail",
            packageName = "com.google.android.gm",
            windowTitle = "Compose",
            visibleText = listOf(
                "Compose",
                "From",
                "To",
                "Subject",
                "Compose email"
            ),
            clickableNodes = listOf(
                ClickableNode(
                    label = "Send",
                    contentDescription = "Send email",
                    bounds = Rect(300, 50, 380, 110),
                    resourceId = "com.google.android.gm:id/send",
                    isEnabled = false,
                    isClickable = false,
                    isFocusable = false,
                    className = "android.widget.ImageButton"
                ),
                ClickableNode(
                    label = "Attach file",
                    contentDescription = "Attach file",
                    bounds = Rect(20, 50, 100, 110),
                    resourceId = "com.google.android.gm:id/attach",
                    isEnabled = true,
                    isClickable = true,
                    isFocusable = true,
                    className = "android.widget.ImageButton"
                )
            ),
            inputFields = listOf(
                InputField(
                    hint = "To",
                    text = "",
                    bounds = Rect(20, 150, 380, 200),
                    resourceId = "com.google.android.gm:id/to",
                    isEnabled = true,
                    isFocused = true,
                    isPassword = false,
                    inputType = 33
                ),
                InputField(
                    hint = "Subject",
                    text = "",
                    bounds = Rect(20, 220, 380, 270),
                    resourceId = "com.google.android.gm:id/subject",
                    isEnabled = true,
                    isFocused = false,
                    isPassword = false,
                    inputType = 1
                ),
                InputField(
                    hint = "Compose email",
                    text = "",
                    bounds = Rect(20, 290, 380, 600),
                    resourceId = "com.google.android.gm:id/body",
                    isEnabled = true,
                    isFocused = false,
                    isPassword = false,
                    inputType = 131073
                )
            ),
            scrollableState = ScrollableState(
                canScrollUp = false,
                canScrollDown = true,
                canScrollLeft = false,
                canScrollRight = false
            ),
            timestamp = System.currentTimeMillis(),
            isSensitive = false
        )
    }
    
    fun demonstrateRedaction() {
        val loginScreen = createLoginScreenExample()
        
        println("=== Original Login Screen (UNSAFE for logging) ===")
        println(loginScreen.toJson(redacted = false).toString(2))
        
        println("\n=== Redacted Login Screen (SAFE for logging) ===")
        println(loginScreen.toRedactedJson())
        
        println("\n=== Redacted Copy ===")
        val redacted = loginScreen.redactedCopy()
        println("Password field text: ${redacted.inputFields[1].text}")
        println("Visible text: ${redacted.visibleText}")
    }
    
    fun demonstrateSensitivityDetection() {
        val loginScreen = createLoginScreenExample()
        val settingsScreen = createSettingsScreenExample()
        
        println("=== Sensitivity Detection ===")
        println("Login screen is sensitive: ${
            ScreenRedaction.shouldMarkContextSensitive(
                loginScreen.packageName,
                loginScreen.inputFields,
                loginScreen.windowTitle
            )
        }")
        
        println("Settings screen is sensitive: ${
            ScreenRedaction.shouldMarkContextSensitive(
                settingsScreen.packageName,
                settingsScreen.inputFields,
                settingsScreen.windowTitle
            )
        }")
    }
}

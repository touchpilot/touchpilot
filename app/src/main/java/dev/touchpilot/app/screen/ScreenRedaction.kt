package dev.touchpilot.app.screen

object ScreenRedaction {
    private const val REDACTED_PLACEHOLDER = "***REDACTED***"
    
    private val sensitivePackages = setOf(
        "com.android.settings",
        "com.google.android.gms",
        "com.android.keychain"
    )
    
    private val sensitiveKeywords = setOf(
        "password",
        "pin",
        "secret",
        "token",
        "key",
        "credential",
        "auth",
        "account",
        "card",
        "cvv",
        "ssn",
        "social security"
    )
    
    fun isSensitivePackage(packageName: String?): Boolean {
        if (packageName == null) return false
        return sensitivePackages.any { packageName.contains(it, ignoreCase = true) }
    }
    
    fun isSensitiveText(text: String?): Boolean {
        if (text == null) return false
        val lowerText = text.lowercase()
        return sensitiveKeywords.any { lowerText.contains(it) }
    }
    
    fun isSensitiveField(hint: String?, resourceId: String?): Boolean {
        return isSensitiveText(hint) || isSensitiveText(resourceId)
    }
    
    fun redactText(text: String, shouldRedact: Boolean): String {
        return if (shouldRedact) REDACTED_PLACEHOLDER else text
    }
    
    fun redactVisibleText(texts: List<String>, isSensitiveContext: Boolean): List<String> {
        return if (isSensitiveContext) {
            texts.map { REDACTED_PLACEHOLDER }
        } else {
            texts.map { text ->
                if (isSensitiveText(text)) REDACTED_PLACEHOLDER else text
            }
        }
    }
    
    fun shouldMarkContextSensitive(
        packageName: String?,
        inputFields: List<InputField>,
        windowTitle: String?
    ): Boolean {
        if (isSensitivePackage(packageName)) return true
        
        if (isSensitiveText(windowTitle)) return true
        
        if (inputFields.any { it.isPassword }) return true
        
        if (inputFields.any { isSensitiveField(it.hint, it.resourceId) }) return true
        
        return false
    }
}

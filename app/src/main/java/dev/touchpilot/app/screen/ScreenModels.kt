package dev.touchpilot.app.screen

import org.json.JSONArray
import org.json.JSONObject

data class Rect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    
    fun isEmpty(): Boolean = width <= 0 || height <= 0
    
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("left", left)
            put("top", top)
            put("right", right)
            put("bottom", bottom)
        }
    }
    
    companion object {
        fun fromJson(json: JSONObject): Rect {
            return Rect(
                left = json.optInt("left", 0),
                top = json.optInt("top", 0),
                right = json.optInt("right", 0),
                bottom = json.optInt("bottom", 0)
            )
        }
    }
}

data class ClickableNode(
    val label: String?,
    val contentDescription: String?,
    val bounds: Rect,
    val resourceId: String?,
    val isEnabled: Boolean,
    val isClickable: Boolean,
    val isFocusable: Boolean,
    val className: String?
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("label", label)
            put("contentDescription", contentDescription)
            put("bounds", bounds.toJson())
            put("resourceId", resourceId)
            put("isEnabled", isEnabled)
            put("isClickable", isClickable)
            put("isFocusable", isFocusable)
            put("className", className)
        }
    }
    
    companion object {
        fun fromJson(json: JSONObject): ClickableNode {
            return ClickableNode(
                label = if (json.has("label") && !json.isNull("label")) json.getString("label") else null,
                contentDescription = if (json.has("contentDescription") && !json.isNull("contentDescription")) json.getString("contentDescription") else null,
                bounds = Rect.fromJson(json.getJSONObject("bounds")),
                resourceId = if (json.has("resourceId") && !json.isNull("resourceId")) json.getString("resourceId") else null,
                isEnabled = json.optBoolean("isEnabled", false),
                isClickable = json.optBoolean("isClickable", false),
                isFocusable = json.optBoolean("isFocusable", false),
                className = if (json.has("className") && !json.isNull("className")) json.getString("className") else null
            )
        }
    }
}

data class InputField(
    val hint: String?,
    val text: String?,
    val bounds: Rect,
    val resourceId: String?,
    val isEnabled: Boolean,
    val isFocused: Boolean,
    val isPassword: Boolean,
    val inputType: Int?
) {
    fun toJson(redacted: Boolean = false): JSONObject {
        return JSONObject().apply {
            put("hint", hint)
            put("text", if (redacted && isPassword) REDACTED_TEXT else text)
            put("bounds", bounds.toJson())
            put("resourceId", resourceId)
            put("isEnabled", isEnabled)
            put("isFocused", isFocused)
            put("isPassword", isPassword)
            put("inputType", inputType)
        }
    }
    
    fun redactedCopy(): InputField {
        return if (isPassword) {
            copy(text = REDACTED_TEXT)
        } else {
            this
        }
    }
    
    companion object {
        private const val REDACTED_TEXT = "***REDACTED***"
        
        fun fromJson(json: JSONObject): InputField {
            return InputField(
                hint = if (json.has("hint") && !json.isNull("hint")) json.getString("hint") else null,
                text = if (json.has("text") && !json.isNull("text")) json.getString("text") else null,
                bounds = Rect.fromJson(json.getJSONObject("bounds")),
                resourceId = if (json.has("resourceId") && !json.isNull("resourceId")) json.getString("resourceId") else null,
                isEnabled = json.optBoolean("isEnabled", false),
                isFocused = json.optBoolean("isFocused", false),
                isPassword = json.optBoolean("isPassword", false),
                inputType = if (json.has("inputType")) json.getInt("inputType") else null
            )
        }
    }
}

data class ScrollableState(
    val canScrollUp: Boolean,
    val canScrollDown: Boolean,
    val canScrollLeft: Boolean,
    val canScrollRight: Boolean
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("canScrollUp", canScrollUp)
            put("canScrollDown", canScrollDown)
            put("canScrollLeft", canScrollLeft)
            put("canScrollRight", canScrollRight)
        }
    }
    
    companion object {
        fun fromJson(json: JSONObject): ScrollableState {
            return ScrollableState(
                canScrollUp = json.optBoolean("canScrollUp", false),
                canScrollDown = json.optBoolean("canScrollDown", false),
                canScrollLeft = json.optBoolean("canScrollLeft", false),
                canScrollRight = json.optBoolean("canScrollRight", false)
            )
        }
    }
}

data class ScreenContext(
    val appLabel: String?,
    val packageName: String?,
    val windowTitle: String?,
    val visibleText: List<String>,
    val clickableNodes: List<ClickableNode>,
    val inputFields: List<InputField>,
    val scrollableState: ScrollableState,
    val timestamp: Long,
    val isSensitive: Boolean = false
) {
    fun redactedCopy(): ScreenContext {
        return copy(
            inputFields = inputFields.map { it.redactedCopy() },
            visibleText = if (isSensitive) {
                visibleText.map { REDACTED_TEXT }
            } else {
                visibleText
            }
        )
    }
    
    fun toJson(redacted: Boolean = false): JSONObject {
        val context = if (redacted) redactedCopy() else this
        
        return JSONObject().apply {
            put("appLabel", context.appLabel)
            put("packageName", context.packageName)
            put("windowTitle", context.windowTitle)
            put("visibleText", JSONArray(context.visibleText))
            put("clickableNodes", JSONArray().apply {
                context.clickableNodes.forEach { put(it.toJson()) }
            })
            put("inputFields", JSONArray().apply {
                context.inputFields.forEach { put(it.toJson(redacted)) }
            })
            put("scrollableState", context.scrollableState.toJson())
            put("timestamp", context.timestamp)
            put("isSensitive", context.isSensitive)
        }
    }
    
    fun toRedactedJson(): String {
        return toJson(redacted = true).toString(2)
    }
    
    companion object {
        private const val REDACTED_TEXT = "***REDACTED***"
        
        fun fromJson(json: JSONObject): ScreenContext {
            val visibleTextArray = json.optJSONArray("visibleText") ?: JSONArray()
            val visibleText = mutableListOf<String>()
            for (i in 0 until visibleTextArray.length()) {
                visibleText.add(visibleTextArray.getString(i))
            }
            
            val clickableNodesArray = json.optJSONArray("clickableNodes") ?: JSONArray()
            val clickableNodes = mutableListOf<ClickableNode>()
            for (i in 0 until clickableNodesArray.length()) {
                clickableNodes.add(ClickableNode.fromJson(clickableNodesArray.getJSONObject(i)))
            }
            
            val inputFieldsArray = json.optJSONArray("inputFields") ?: JSONArray()
            val inputFields = mutableListOf<InputField>()
            for (i in 0 until inputFieldsArray.length()) {
                inputFields.add(InputField.fromJson(inputFieldsArray.getJSONObject(i)))
            }
            
            return ScreenContext(
                appLabel = if (json.has("appLabel") && !json.isNull("appLabel")) json.getString("appLabel") else null,
                packageName = if (json.has("packageName") && !json.isNull("packageName")) json.getString("packageName") else null,
                windowTitle = if (json.has("windowTitle") && !json.isNull("windowTitle")) json.getString("windowTitle") else null,
                visibleText = visibleText,
                clickableNodes = clickableNodes,
                inputFields = inputFields,
                scrollableState = ScrollableState.fromJson(json.getJSONObject("scrollableState")),
                timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                isSensitive = json.optBoolean("isSensitive", false)
            )
        }
    }
}

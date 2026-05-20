package dev.touchpilot.app.localinference

import android.content.Context
import dev.touchpilot.app.agent.AgentCommandProvider
import dev.touchpilot.app.agent.LocalRouterCommandProvider
import dev.touchpilot.app.memory.Skill
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class LocalModelManifest(
    val runtime: String,
    val modelAsset: String,
    val tokenizerAsset: String?,
    val version: String
)

data class LocalModelStatus(
    val available: Boolean,
    val runtime: String,
    val modelAsset: String,
    val version: String,
    val message: String
)

interface LocalCommandModelRuntime {
    fun status(): LocalModelStatus
    fun route(task: String, context: String, skill: Skill?): String
}

class LiteRtCommandModelRuntime(
    private val context: Context,
    private val manifestAsset: String = ManifestAsset
) : LocalCommandModelRuntime {
    private val manifest by lazy { loadManifest() }
    private val interpreter by lazy { loadInterpreter() }

    override fun status(): LocalModelStatus {
        val loadedManifest = manifest
            ?: return LocalModelStatus(
                available = false,
                runtime = RuntimeName,
                modelAsset = DefaultModelAsset,
                version = "unconfigured",
                message = "LiteRT command model manifest is not bundled."
            )

        if (!assetExists(loadedManifest.modelAsset)) {
            return LocalModelStatus(
                available = false,
                runtime = loadedManifest.runtime,
                modelAsset = loadedManifest.modelAsset,
                version = loadedManifest.version,
                message = "LiteRT model asset is missing; deterministic local router will be used."
            )
        }

        val loadedInterpreter = interpreter
        return if (loadedInterpreter != null) {
            LocalModelStatus(
                available = true,
                runtime = loadedManifest.runtime,
                modelAsset = loadedManifest.modelAsset,
                version = loadedManifest.version,
                message = "LiteRT command model loaded."
            )
        } else {
            LocalModelStatus(
                available = false,
                runtime = loadedManifest.runtime,
                modelAsset = loadedManifest.modelAsset,
                version = loadedManifest.version,
                message = "LiteRT model asset could not be loaded; deterministic local router will be used."
            )
        }
    }

    override fun route(task: String, context: String, skill: Skill?): String {
        val status = status()
        if (!status.available) {
            error(status.message)
        }

        val input = buildFeatures(task)
        val output = Array(1) { FloatArray(RouteLabels.size) }
        val loadedInterpreter = interpreter ?: error(status.message)
        synchronized(loadedInterpreter) {
            loadedInterpreter.run(input, output)
        }
        return output[0].toCommandJson(task, skill)
    }

    private fun loadManifest(): LocalModelManifest? {
        if (!assetExists(manifestAsset)) return null
        val json = context.assets.open(manifestAsset).bufferedReader().use { it.readText() }
        val manifestJson = JSONObject(json)
        return LocalModelManifest(
            runtime = manifestJson.optString("runtime", RuntimeName),
            modelAsset = manifestJson.optString("model_asset", DefaultModelAsset),
            tokenizerAsset = manifestJson.optString("tokenizer_asset", "").ifBlank { null },
            version = manifestJson.optString("version", "unknown")
        )
    }

    private fun assetExists(path: String): Boolean {
        return runCatching {
            context.assets.open(path).close()
        }.isSuccess
    }

    private fun loadInterpreter(): Interpreter? {
        val loadedManifest = manifest ?: return null
        return runCatching {
            Interpreter(loadAssetBuffer(loadedManifest.modelAsset))
        }.getOrNull()
    }

    private fun loadAssetBuffer(path: String): ByteBuffer {
        val bytes = context.assets.open(path).use { it.readBytes() }
        return ByteBuffer.allocateDirect(bytes.size).apply {
            order(ByteOrder.nativeOrder())
            put(bytes)
            rewind()
        }
    }

    private fun buildFeatures(task: String): Array<FloatArray> {
        val normalized = task.trim().lowercase()
        val features = FloatArray(RouteLabels.size)
        when {
            "back" in normalized -> features[RouteIndexBack] = 1f
            "home" in normalized -> features[RouteIndexHome] = 1f
            "scroll up" in normalized -> features[RouteIndexScrollUp] = 1f
            "scroll" in normalized -> features[RouteIndexScrollDown] = 1f
            OpenPattern.containsMatchIn(normalized) -> features[RouteIndexOpenApp] = 1f
            TapPattern.containsMatchIn(normalized) -> features[RouteIndexTapText] = 1f
        }
        return arrayOf(features)
    }

    private fun FloatArray.toCommandJson(task: String, skill: Skill?): String {
        val bestIndex = indices.maxByOrNull { this[it] } ?: return FinalFallback
        if (this[bestIndex] <= 0f) return FinalFallback

        val route = when (bestIndex) {
            RouteIndexBack -> LocalRoute("press_back")
            RouteIndexHome -> LocalRoute("press_home")
            RouteIndexScrollUp -> LocalRoute("scroll", mapOf("direction" to "backward"))
            RouteIndexScrollDown -> LocalRoute("scroll", mapOf("direction" to "forward"))
            RouteIndexOpenApp -> extract(OpenPattern, task)?.let { target ->
                LocalRoute("open_app", mapOf("target" to target))
            }
            RouteIndexTapText -> extract(TapPattern, task)?.let { text ->
                LocalRoute("tap", mapOf("text" to text))
            }
            else -> null
        } ?: return FinalFallback

        if (skill != null && route.tool !in skill.allowedTools) {
            return """{"final":"Local model route is not allowed by the active skill."}"""
        }

        return route.toJson()
    }

    private fun extract(pattern: Regex, task: String): String? {
        return pattern.find(task.trim().lowercase())
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private data class LocalRoute(
        val tool: String,
        val args: Map<String, String> = emptyMap()
    ) {
        fun toJson(): String {
            val argsJson = args.entries.joinToString(separator = ",") { (key, value) ->
                """"$key":"${escapeJson(value)}""""
            }
            return """{"tool":"$tool","args":{$argsJson}}"""
        }

        private fun escapeJson(value: String): String {
            return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
        }
    }

    private companion object {
        const val RuntimeName = "LiteRT"
        const val ManifestAsset = "models/command_router/manifest.json"
        const val DefaultModelAsset = "models/command_router/model.tflite"
        const val FinalFallback = """{"final":"Local model could not route this task safely."}"""
        const val RouteIndexBack = 0
        const val RouteIndexHome = 1
        const val RouteIndexScrollUp = 2
        const val RouteIndexScrollDown = 3
        const val RouteIndexOpenApp = 4
        const val RouteIndexTapText = 5
        val RouteLabels = listOf("back", "home", "scroll_up", "scroll_down", "open_app", "tap_text")
        val OpenPattern = Regex("(?:open|launch)\\s+([\\w .-]+)")
        val TapPattern = Regex("(?:tap|press)\\s+([\\w .-]+)")
    }
}

class LocalModelCommandProvider(
    private val runtime: LocalCommandModelRuntime,
    private val fallback: LocalRouterCommandProvider,
    private val task: String,
    private val skill: Skill?
) : AgentCommandProvider {
    private var attemptedModel = false

    override fun complete(systemPrompt: String, context: String): String {
        if (!attemptedModel) {
            attemptedModel = true
            val modelResult = runCatching {
                runtime.route(task, context, skill)
            }.getOrNull()

            val validOutput = modelResult?.let { validateModelOutput(it) }
            if (validOutput != null) {
                return validOutput.toCommandJson()
            }
        }

        return fallback.complete(systemPrompt, context)
    }

    private fun validateModelOutput(raw: String): LocalModelOutput? {
        val output = runCatching {
            LocalModelOutputParser.parse(raw)
        }.getOrNull() ?: return null

        return when (val validation = LocalModelOutputValidator.validate(output, skill)) {
            is LocalModelOutputValidation.Valid -> validation.output
            is LocalModelOutputValidation.Invalid -> null
        }
    }
}

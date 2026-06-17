package dev.touchpilot.app.localinference

import android.content.Context
import dev.touchpilot.app.agent.AgentCommandProvider
import dev.touchpilot.app.memory.Skill
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Static, local metadata for one bundled local model role. It records which role
 * the asset serves, the runtime that loads it, the asset path, a human-readable
 * version, and the input/output contract version the runtime must understand.
 *
 * Parsing is pure (no Android), so the manifest can be read and validated from
 * unit tests as well as the app. It never fetches anything remote.
 */
data class LocalModelManifest(
    val role: String,
    val runtime: String,
    val modelAsset: String,
    val tokenizerAsset: String?,
    val version: String,
    val contractVersion: Int
) {
    /** Problems with this manifest; an empty list means it is valid. */
    fun validationErrors(): List<String> {
        val errors = mutableListOf<String>()
        if (role.isBlank()) errors += "role must not be blank"
        if (runtime.isBlank()) errors += "runtime must not be blank"
        if (modelAsset.isBlank()) errors += "model_asset must not be blank"
        if (version.isBlank()) errors += "version must not be blank"
        if (contractVersion < 1) {
            errors += "contract_version must be >= 1 (was $contractVersion)"
        } else if (contractVersion > SUPPORTED_CONTRACT_VERSION) {
            errors += "contract_version $contractVersion is newer than supported $SUPPORTED_CONTRACT_VERSION"
        }
        return errors
    }

    val isValid: Boolean get() = validationErrors().isEmpty()

    companion object {
        /** Highest input/output contract version this build of the runtime understands. */
        const val SUPPORTED_CONTRACT_VERSION = 1

        const val DefaultRole = "command_router"
        const val DefaultRuntime = "LiteRT"
        const val DefaultModelAsset = "models/command_router/model.tflite"

        /** Parses manifest JSON, applying defaults for omitted optional fields. */
        fun parse(json: String): LocalModelManifest {
            val obj = JSONObject(json)
            return LocalModelManifest(
                role = obj.optString("role", DefaultRole),
                runtime = obj.optString("runtime", DefaultRuntime),
                modelAsset = obj.optString("model_asset", DefaultModelAsset),
                tokenizerAsset = obj.optString("tokenizer_asset", "").ifBlank { null },
                version = obj.optString("version", "unknown"),
                contractVersion = obj.optInt("contract_version", SUPPORTED_CONTRACT_VERSION)
            )
        }
    }
}

data class LocalModelStatus(
    val available: Boolean,
    val runtime: String,
    val modelAsset: String,
    val version: String,
    val message: String,
    val role: String = LocalModelManifest.DefaultRole,
    val contractVersion: Int = LocalModelManifest.SUPPORTED_CONTRACT_VERSION
)

private fun LocalModelManifest.toStatus(available: Boolean, message: String): LocalModelStatus {
    return LocalModelStatus(
        available = available,
        runtime = runtime,
        modelAsset = modelAsset,
        version = version,
        message = message,
        role = role,
        contractVersion = contractVersion
    )
}

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

        val manifestErrors = loadedManifest.validationErrors()
        if (manifestErrors.isNotEmpty()) {
            return loadedManifest.toStatus(
                available = false,
                message = "LiteRT manifest is invalid: ${manifestErrors.joinToString("; ")}"
            )
        }

        if (!assetExists(loadedManifest.modelAsset)) {
            return loadedManifest.toStatus(
                available = false,
                message = "LiteRT model asset is missing; the run will stop with a final answer."
            )
        }

        val loadedInterpreter = interpreter
        return if (loadedInterpreter != null) {
            loadedManifest.toStatus(available = true, message = "LiteRT command model loaded.")
        } else {
            loadedManifest.toStatus(
                available = false,
                message = "LiteRT model asset could not be loaded; the run will stop with a final answer."
            )
        }
    }

    override fun route(task: String, context: String, skill: Skill?): String {
        val status = status()
        if (!status.available) {
            error(status.message)
        }

        val dispatched = extractDispatchedTools(context)
        val input = buildFeatures(task, dispatched)
        val output = Array(1) { FloatArray(RouteLabels.size) }
        val loadedInterpreter = interpreter ?: error(status.message)
        synchronized(loadedInterpreter) {
            loadedInterpreter.run(input, output)
        }
        return output[0].toCommandJson(task, dispatched, skill)
    }

    private fun extractDispatchedTools(context: String): Set<String> {
        return Regex(""""tool"\s*:\s*"([^"]+)"""")
            .findAll(context)
            .map { it.groupValues[1] }
            .toSet()
    }

    private fun loadManifest(): LocalModelManifest? {
        if (!assetExists(manifestAsset)) return null
        val json = context.assets.open(manifestAsset).bufferedReader().use { it.readText() }
        return LocalModelManifest.parse(json)
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

    private fun buildFeatures(task: String, dispatched: Set<String>): Array<FloatArray> {
        return arrayOf(CommandRouteClassifier.buildFeatureVector(task, dispatched))
    }

    private fun FloatArray.toCommandJson(task: String, dispatched: Set<String>, skill: Skill?): String {
        val bestIndex = indices.maxByOrNull { this[it] } ?: return FinalFallback
        if (this[bestIndex] <= 0f) return FinalFallback

        val route = CommandRouteClassifier.classify(task, dispatched)
            ?.takeIf { it.labelIndex == bestIndex }
            ?: return FinalFallback

        if (skill != null && route.tool !in skill.allowedTools) {
            return """{"final":"Local model route is not allowed by the active skill."}"""
        }

        return CommandRouteClassifier.toCommandJson(route)
    }

    private companion object {
        const val RuntimeName = "LiteRT"
        const val ManifestAsset = "models/command_router/manifest.json"
        const val DefaultModelAsset = "models/command_router/model.tflite"
        const val FinalFallback = """{"final":"Local model could not route this task safely."}"""
        val RouteLabels = CommandRouteClassifier.RouteLabels
    }
}

class LocalModelCommandProvider(
    private val runtime: LocalCommandModelRuntime,
    private val task: String,
    private val skill: Skill?
) : AgentCommandProvider {
    override fun complete(systemPrompt: String, context: String): String {
        val modelResult = runCatching {
            runtime.route(task, context, skill)
        }.getOrNull()
        val validOutput = modelResult?.let { validateModelOutput(it) }
        return validOutput?.toCommandJson() ?: FinalFallback
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

    companion object {
        private const val FinalFallback = """{"final":"Local model could not route this task safely."}"""
    }
}

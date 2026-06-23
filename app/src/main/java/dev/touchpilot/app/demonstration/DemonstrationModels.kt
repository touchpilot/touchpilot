package dev.touchpilot.app.demonstration

import dev.touchpilot.app.agent.AgentRunRecord
import dev.touchpilot.app.agent.AgentScreenRecord
import dev.touchpilot.app.screen.ScreenContext
import dev.touchpilot.app.security.SensitiveTextRedactor
import org.json.JSONArray
import org.json.JSONObject

/** Lifecycle status of a captured demonstration session. */
enum class DemonstrationStatus(val wireName: String) {
    RECORDING("recording"),
    COMPLETED("completed"),
    FAILED("failed"),
    CANCELLED("cancelled"),
    ;

    companion object {
        fun fromWire(value: String): DemonstrationStatus {
            return entries.firstOrNull { it.wireName == value } ?: COMPLETED
        }
    }
}

/** Phase label for a screen frame within a demonstration step. */
enum class DemonstrationCapturePhase(val wireName: String) {
    BEFORE_ACTION("before_action"),
    AFTER_ACTION("after_action"),
    RUN_INITIAL("run_initial"),
    RUN_FINAL("run_final"),
    APPROVAL_PENDING("approval_pending"),
    CLARIFICATION("clarification"),
    ;

    companion object {
        fun fromWire(value: String): DemonstrationCapturePhase {
            return entries.firstOrNull { it.wireName == value } ?: BEFORE_ACTION
        }
    }
}

/** Metadata describing a demonstration session without sensitive payloads. */
data class DemonstrationMetadata(
    val sessionId: String,
    val runId: String,
    val task: String,
    val startedAtMillis: Long,
    val completedAtMillis: Long? = null,
    val status: DemonstrationStatus = DemonstrationStatus.RECORDING,
    val stepCount: Int = 0,
    val skillId: String? = null,
    val providerMode: String? = null,
    val containsSensitiveContent: Boolean = false,
    val tags: List<String> = emptyList(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("session_id", sessionId)
        put("run_id", runId)
        put("task", task)
        put("started_at_millis", startedAtMillis)
        put("completed_at_millis", completedAtMillis ?: JSONObject.NULL)
        put("status", status.wireName)
        put("step_count", stepCount)
        put("skill_id", skillId ?: JSONObject.NULL)
        put("provider_mode", providerMode ?: JSONObject.NULL)
        put("contains_sensitive_content", containsSensitiveContent)
        put("tags", JSONArray().apply { tags.forEach { put(it) } })
    }

    companion object {
        fun fromJson(json: JSONObject): DemonstrationMetadata {
            val tagsArray = json.optJSONArray("tags") ?: JSONArray()
            val tags = buildList {
                for (i in 0 until tagsArray.length()) {
                    add(tagsArray.getString(i))
                }
            }
            return DemonstrationMetadata(
                sessionId = json.getString("session_id"),
                runId = json.getString("run_id"),
                task = json.getString("task"),
                startedAtMillis = json.getLong("started_at_millis"),
                completedAtMillis = json.optLong("completed_at_millis").takeIf {
                    json.has("completed_at_millis") && !json.isNull("completed_at_millis")
                },
                status = DemonstrationStatus.fromWire(json.getString("status")),
                stepCount = json.optInt("step_count"),
                skillId = json.optString("skill_id").takeIf { json.has("skill_id") && !json.isNull("skill_id") },
                providerMode = json.optString("provider_mode").takeIf {
                    json.has("provider_mode") && !json.isNull("provider_mode")
                },
                containsSensitiveContent = json.optBoolean("contains_sensitive_content"),
                tags = tags,
            )
        }
    }
}

/** A redacted screen snapshot captured at a point in time during demonstration recording. */
data class DemonstrationScreenFrame(
    val sequenceNumber: Int,
    val phase: DemonstrationCapturePhase,
    val timestampMillis: Long,
    val contextJson: String,
    val nodeCount: Int,
    val containsSensitiveContent: Boolean,
    val packageName: String? = null,
    val windowTitle: String? = null,
    val fingerprint: String? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("sequence_number", sequenceNumber)
        put("phase", phase.wireName)
        put("timestamp_millis", timestampMillis)
        put("node_count", nodeCount)
        put("contains_sensitive_content", containsSensitiveContent)
        put("package_name", packageName ?: JSONObject.NULL)
        put("window_title", windowTitle ?: JSONObject.NULL)
        put("fingerprint", fingerprint ?: JSONObject.NULL)
        put("context", JSONObject(contextJson))
    }

    fun toAgentScreenRecord(): AgentScreenRecord {
        return AgentScreenRecord(
            sequenceNumber = sequenceNumber,
            phase = phase.wireName,
            timestampMillis = timestampMillis,
            contextJson = contextJson,
            nodeCount = nodeCount,
            containsSensitiveContent = containsSensitiveContent,
        )
    }

    companion object {
        fun capture(
            sequenceNumber: Int,
            phase: DemonstrationCapturePhase,
            timestampMillis: Long,
            context: ScreenContext,
            fingerprint: String? = null,
        ): DemonstrationScreenFrame {
            return DemonstrationScreenFrame(
                sequenceNumber = sequenceNumber,
                phase = phase,
                timestampMillis = timestampMillis,
                contextJson = context.toRedactedJson(),
                nodeCount = context.nodes.size,
                containsSensitiveContent = context.containsSensitiveContent,
                packageName = context.packageName,
                windowTitle = context.windowTitle,
                fingerprint = fingerprint,
            )
        }

        fun fromJson(json: JSONObject): DemonstrationScreenFrame {
            val contextObj = json.getJSONObject("context")
            return DemonstrationScreenFrame(
                sequenceNumber = json.getInt("sequence_number"),
                phase = DemonstrationCapturePhase.fromWire(json.getString("phase")),
                timestampMillis = json.getLong("timestamp_millis"),
                contextJson = contextObj.toString(2),
                nodeCount = json.getInt("node_count"),
                containsSensitiveContent = json.optBoolean("contains_sensitive_content"),
                packageName = json.optString("package_name").takeIf {
                    json.has("package_name") && !json.isNull("package_name")
                },
                windowTitle = json.optString("window_title").takeIf {
                    json.has("window_title") && !json.isNull("window_title")
                },
                fingerprint = json.optString("fingerprint").takeIf {
                    json.has("fingerprint") && !json.isNull("fingerprint")
                },
            )
        }
    }
}

/** Computed delta between two screen frames for a demonstration step. */
data class DemonstrationScreenDelta(
    val addedNodeCount: Int = 0,
    val removedNodeCount: Int = 0,
    val changedNodeCount: Int = 0,
    val packageChanged: Boolean = false,
    val windowTitleChanged: Boolean = false,
    val addedTexts: List<String> = emptyList(),
    val removedTexts: List<String> = emptyList(),
    val summary: String = "",
) {
    val hasChanges: Boolean
        get() = addedNodeCount > 0 || removedNodeCount > 0 || changedNodeCount > 0 ||
            packageChanged || windowTitleChanged

    fun toJson(): JSONObject = JSONObject().apply {
        put("added_node_count", addedNodeCount)
        put("removed_node_count", removedNodeCount)
        put("changed_node_count", changedNodeCount)
        put("package_changed", packageChanged)
        put("window_title_changed", windowTitleChanged)
        put("added_texts", JSONArray().apply { addedTexts.forEach { put(it) } })
        put("removed_texts", JSONArray().apply { removedTexts.forEach { put(it) } })
        put("summary", summary)
        put("has_changes", hasChanges)
    }

    companion object {
        fun fromJson(json: JSONObject): DemonstrationScreenDelta {
            fun readTexts(key: String): List<String> {
                val array = json.optJSONArray(key) ?: JSONArray()
                return buildList {
                    for (i in 0 until array.length()) add(array.getString(i))
                }
            }
            return DemonstrationScreenDelta(
                addedNodeCount = json.optInt("added_node_count"),
                removedNodeCount = json.optInt("removed_node_count"),
                changedNodeCount = json.optInt("changed_node_count"),
                packageChanged = json.optBoolean("package_changed"),
                windowTitleChanged = json.optBoolean("window_title_changed"),
                addedTexts = readTexts("added_texts"),
                removedTexts = readTexts("removed_texts"),
                summary = json.optString("summary"),
            )
        }
    }
}

/** A single tool action captured during demonstration recording. */
data class DemonstrationToolAction(
    val tool: String,
    val args: Map<String, String>,
    val source: String,
    val succeeded: Boolean,
    val message: String,
    val resultData: Map<String, String> = emptyMap(),
    val verificationStatus: String? = null,
    val verificationReason: String? = null,
    val requiredApproval: Boolean = false,
    val approvalGranted: Boolean? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("tool", tool)
        put("source", source)
        put("args", JSONObject(args))
        put("succeeded", succeeded)
        put("message", message)
        put("result_data", JSONObject(resultData))
        put("verification_status", verificationStatus ?: JSONObject.NULL)
        put("verification_reason", verificationReason ?: JSONObject.NULL)
        put("required_approval", requiredApproval)
        put("approval_granted", approvalGranted ?: JSONObject.NULL)
    }

    companion object {
        fun fromJson(json: JSONObject): DemonstrationToolAction {
            val argsObj = json.getJSONObject("args")
            val args = buildMap {
                json.getJSONObject("args").keys().forEach { key ->
                    put(key, argsObj.getString(key))
                }
            }
            val dataObj = json.optJSONObject("result_data") ?: JSONObject()
            val resultData = buildMap {
                dataObj.keys().forEach { key -> put(key, dataObj.getString(key)) }
            }
            return DemonstrationToolAction(
                tool = json.getString("tool"),
                args = args,
                source = json.optString("source"),
                succeeded = json.getBoolean("succeeded"),
                message = json.getString("message"),
                resultData = resultData,
                verificationStatus = json.optString("verification_status").takeIf {
                    json.has("verification_status") && !json.isNull("verification_status")
                },
                verificationReason = json.optString("verification_reason").takeIf {
                    json.has("verification_reason") && !json.isNull("verification_reason")
                },
                requiredApproval = json.optBoolean("required_approval"),
                approvalGranted = json.optBoolean("approval_granted").takeIf {
                    json.has("approval_granted") && !json.isNull("approval_granted")
                },
            )
        }
    }
}

/** One recorded step: tool action plus before/after screen context. */
data class DemonstrationStep(
    val index: Int,
    val action: DemonstrationToolAction,
    val beforeFrame: DemonstrationScreenFrame,
    val afterFrame: DemonstrationScreenFrame,
    val screenDelta: DemonstrationScreenDelta? = null,
    val durationMillis: Long = 0L,
    val eventIds: List<String> = emptyList(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("index", index)
        put("action", action.toJson())
        put("before_frame", beforeFrame.toJson())
        put("after_frame", afterFrame.toJson())
        put("screen_delta", screenDelta?.toJson() ?: JSONObject.NULL)
        put("duration_millis", durationMillis)
        put("event_ids", JSONArray().apply { eventIds.forEach { put(it) } })
    }

    companion object {
        fun fromJson(json: JSONObject): DemonstrationStep {
            val eventIdsArray = json.optJSONArray("event_ids") ?: JSONArray()
            val eventIds = buildList {
                for (i in 0 until eventIdsArray.length()) add(eventIdsArray.getString(i))
            }
            val deltaJson = json.optJSONObject("screen_delta")
            return DemonstrationStep(
                index = json.getInt("index"),
                action = DemonstrationToolAction.fromJson(json.getJSONObject("action")),
                beforeFrame = DemonstrationScreenFrame.fromJson(json.getJSONObject("before_frame")),
                afterFrame = DemonstrationScreenFrame.fromJson(json.getJSONObject("after_frame")),
                screenDelta = deltaJson?.let { DemonstrationScreenDelta.fromJson(it) },
                durationMillis = json.optLong("duration_millis"),
                eventIds = eventIds,
            )
        }
    }
}

/** A complete demonstration recording session for one agent run. */
data class DemonstrationSession(
    val metadata: DemonstrationMetadata,
    val initialFrame: DemonstrationScreenFrame? = null,
    val finalFrame: DemonstrationScreenFrame? = null,
    val steps: List<DemonstrationStep> = emptyList(),
    val stopReason: String? = null,
    val errorMessage: String? = null,
) {
    val sessionId: String get() = metadata.sessionId
    val runId: String get() = metadata.runId

    val allFrames: List<DemonstrationScreenFrame>
        get() = buildList {
            initialFrame?.let { add(it) }
            steps.forEach { step ->
                add(step.beforeFrame)
                add(step.afterFrame)
            }
            finalFrame?.let { add(it) }
        }

    val containsSensitiveContent: Boolean
        get() = metadata.containsSensitiveContent ||
            allFrames.any { it.containsSensitiveContent }

    fun toJson(): JSONObject = JSONObject().apply {
        put("schema_version", SCHEMA_VERSION)
        put("metadata", metadata.toJson())
        put("initial_frame", initialFrame?.toJson() ?: JSONObject.NULL)
        put("final_frame", finalFrame?.toJson() ?: JSONObject.NULL)
        put("steps", JSONArray().apply { steps.forEach { put(it.toJson()) } })
        put("stop_reason", stopReason ?: JSONObject.NULL)
        put("error_message", errorMessage ?: JSONObject.NULL)
    }

    fun withCompleted(
        status: DemonstrationStatus,
        completedAtMillis: Long,
        stopReason: String? = null,
        errorMessage: String? = null,
        finalFrame: DemonstrationScreenFrame? = this.finalFrame,
    ): DemonstrationSession {
        return copy(
            metadata = metadata.copy(
                status = status,
                completedAtMillis = completedAtMillis,
                stepCount = steps.size,
                containsSensitiveContent = containsSensitiveContent,
            ),
            finalFrame = finalFrame,
            stopReason = stopReason,
            errorMessage = errorMessage?.let { SensitiveTextRedactor.redact(it) },
        )
    }

    companion object {
        const val SCHEMA_VERSION = 1

        fun create(
            sessionId: String,
            runId: String,
            task: String,
            startedAtMillis: Long,
            skillId: String? = null,
            providerMode: String? = null,
            initialFrame: DemonstrationScreenFrame? = null,
        ): DemonstrationSession {
            return DemonstrationSession(
                metadata = DemonstrationMetadata(
                    sessionId = sessionId,
                    runId = runId,
                    task = SensitiveTextRedactor.redact(task),
                    startedAtMillis = startedAtMillis,
                    status = DemonstrationStatus.RECORDING,
                    skillId = skillId,
                    providerMode = providerMode,
                ),
                initialFrame = initialFrame,
            )
        }

        fun fromJson(json: JSONObject): DemonstrationSession {
            val stepsArray = json.getJSONArray("steps")
            val steps = buildList {
                for (i in 0 until stepsArray.length()) {
                    add(DemonstrationStep.fromJson(stepsArray.getJSONObject(i)))
                }
            }
            val initialJson = json.optJSONObject("initial_frame")
            val finalJson = json.optJSONObject("final_frame")
            return DemonstrationSession(
                metadata = DemonstrationMetadata.fromJson(json.getJSONObject("metadata")),
                initialFrame = initialJson?.let { DemonstrationScreenFrame.fromJson(it) },
                finalFrame = finalJson?.let { DemonstrationScreenFrame.fromJson(it) },
                steps = steps,
                stopReason = json.optString("stop_reason").takeIf {
                    json.has("stop_reason") && !json.isNull("stop_reason")
                },
                errorMessage = json.optString("error_message").takeIf {
                    json.has("error_message") && !json.isNull("error_message")
                },
            )
        }

        fun from(record: AgentRunRecord, steps: List<DemonstrationStep>): DemonstrationSession? {
            if (steps.isEmpty()) return null
            val initial = record.screenRecords.firstOrNull { it.phase == "initial" }
            val final = record.screenRecords.lastOrNull { it.phase == "final" }
            return DemonstrationSession(
                metadata = DemonstrationMetadata(
                    sessionId = "demo-${record.id}",
                    runId = record.id,
                    task = SensitiveTextRedactor.redact(record.task),
                    startedAtMillis = record.startedAtMillis,
                    completedAtMillis = record.completedAtMillis,
                    status = if (record.errorMessage == null) DemonstrationStatus.COMPLETED else DemonstrationStatus.FAILED,
                    stepCount = steps.size,
                ),
                initialFrame = initial?.let {
                    DemonstrationScreenFrame(
                        sequenceNumber = it.sequenceNumber,
                        phase = DemonstrationCapturePhase.RUN_INITIAL,
                        timestampMillis = it.timestampMillis,
                        contextJson = it.contextJson,
                        nodeCount = it.nodeCount,
                        containsSensitiveContent = it.containsSensitiveContent,
                    )
                },
                finalFrame = final?.let {
                    DemonstrationScreenFrame(
                        sequenceNumber = it.sequenceNumber,
                        phase = DemonstrationCapturePhase.RUN_FINAL,
                        timestampMillis = it.timestampMillis,
                        contextJson = it.contextJson,
                        nodeCount = it.nodeCount,
                        containsSensitiveContent = it.containsSensitiveContent,
                    )
                },
                steps = steps,
                stopReason = record.result?.stopReason?.name,
                errorMessage = record.errorMessage,
            )
        }
    }
}

object DemonstrationSessionIds {
    private var sequence = 0L

    fun next(): String {
        sequence += 1
        return "demo-session-$sequence"
    }
}

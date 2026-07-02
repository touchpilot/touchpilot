package dev.touchpilot.app.memory

/**
 * Parses bundled `SKILL.md` files into structured [Skill] models.
 *
 * A file whose first non-blank line is `---` is treated as **Skills v2**
 * (docs/SKILLS.md) and validated strictly: every required field must be present,
 * `risk` must be `low`/`medium`/`high`, and every entry in `allowed_tools` must
 * name a known Android tool. Policy-sensitive problems (unknown risk, unknown or
 * empty tools, missing required fields, id mismatch) fail closed as a
 * [SkillParseResult.Invalid] rather than loading a partial skill.
 *
 * Files without front matter fall back to the legacy v1 reader (heading +
 * "Allowed initial tools:" list) so the not-yet-upgraded bundled pack keeps
 * loading until issue #229 migrates it to v2. The legacy path stays permissive
 * (it does not validate tool names) to preserve current behavior.
 *
 * The parser is pure — no Android dependency — so it is fully unit-testable.
 */
object SkillParser {
    private const val FrontMatterFence = "---"
    private val IdPattern = Regex("^[a-z0-9-]+$")
    private val RequiredScalarFields = listOf("id", "title", "description", "risk")

    fun parse(directoryId: String, markdown: String, knownTools: Set<String>): SkillParseResult {
        val normalized = markdown.replace("\r\n", "\n").replace("\r", "\n")
        return if (declaresFrontMatter(normalized)) {
            parseV2(directoryId, normalized, knownTools)
        } else {
            SkillParseResult.Valid(parseLegacy(directoryId, normalized))
        }
    }

    private fun declaresFrontMatter(markdown: String): Boolean {
        val firstNonBlank = markdown.lineSequence().firstOrNull { it.isNotBlank() } ?: return false
        return firstNonBlank.trim() == FrontMatterFence
    }

    private fun parseV2(
        directoryId: String,
        markdown: String,
        knownTools: Set<String>
    ): SkillParseResult {
        val split = splitFrontMatter(markdown)
            ?: return SkillParseResult.Invalid(
                directoryId,
                listOf("front matter must be closed with a line containing only '---'")
            )

        val (block, body) = split
        val fields = parseFrontMatter(block)
        val errors = mutableListOf<String>()

        for (field in RequiredScalarFields) {
            if (fields.scalar(field).isNullOrBlank()) {
                errors += "missing required field: $field"
            }
        }

        val id = fields.scalar("id").orEmpty().trim()
        if (id.isNotBlank()) {
            if (!IdPattern.matches(id)) {
                errors += "id '$id' must use only a-z, 0-9, and '-'"
            } else if (id != directoryId) {
                errors += "id '$id' does not match the skill directory '$directoryId'"
            }
        }

        val rawRisk = fields.scalar("risk").orEmpty().trim()
        val risk = if (rawRisk.isBlank()) null else SkillRisk.fromWire(rawRisk)
        if (rawRisk.isNotBlank() && risk == null) {
            errors += "invalid risk '$rawRisk' (allowed: low, medium, high)"
        }

        val allowedTools = fields.list("allowed_tools")
        if (allowedTools.isEmpty()) {
            errors += "missing or empty required field: allowed_tools"
        } else {
            val unknown = allowedTools.filter { it !in knownTools }
            if (unknown.isNotEmpty()) {
                errors += "unknown tool(s) in allowed_tools: ${unknown.joinToString()}"
            }
        }

        if (errors.isNotEmpty()) {
            return SkillParseResult.Invalid(directoryId, errors)
        }

        return SkillParseResult.Valid(
            Skill(
                id = directoryId,
                title = fields.scalar("title").orEmpty().trim(),
                markdown = body.trim(),
                allowedTools = allowedTools.toCollection(LinkedHashSet()),
                description = fields.scalar("description").orEmpty().trim(),
                // risk is non-null here: errors is empty, so it parsed cleanly.
                risk = risk ?: SkillRisk.LOW,
                aliases = fields.list("aliases"),
                examples = fields.list("examples"),
                successCriteria = fields.list("success_criteria"),
                format = SkillFormat.V2
            )
        )
    }

    /** Splits a v2 file into its front matter block and the Markdown body, or null if unclosed. */
    private fun splitFrontMatter(markdown: String): Pair<String, String>? {
        val lines = markdown.lines()
        val openIndex = lines.indexOfFirst { it.isNotBlank() }
        if (openIndex < 0 || lines[openIndex].trim() != FrontMatterFence) return null
        val closeIndex = (openIndex + 1 until lines.size)
            .firstOrNull { lines[it].trim() == FrontMatterFence }
            ?: return null
        val block = lines.subList(openIndex + 1, closeIndex).joinToString("\n")
        val body = lines.subList(closeIndex + 1, lines.size).joinToString("\n")
        return block to body
    }

    /**
     * Parses the front matter block as the documented YAML subset: `key: value`
     * scalars and `key:` followed by indented `- item` lists. Values may be
     * single- or double-quoted. This is intentionally not a full YAML parser.
     */
    private fun parseFrontMatter(block: String): FrontMatter {
        val values = LinkedHashMap<String, Any>()
        var pendingListKey: String? = null
        val pendingList = mutableListOf<String>()

        fun flush() {
            val key = pendingListKey ?: return
            values[key] = pendingList.toList()
            pendingList.clear()
            pendingListKey = null
        }

        for (raw in block.lines()) {
            if (raw.isBlank()) continue
            val trimmed = raw.trim()
            if (trimmed.startsWith("#")) continue
            if (trimmed.startsWith("-")) {
                if (pendingListKey == null) continue
                val item = unquote(trimmed.removePrefix("-").trim())
                if (item.isNotEmpty()) pendingList += item
                continue
            }
            val colon = trimmed.indexOf(':')
            if (colon <= 0) continue
            flush()
            val key = trimmed.substring(0, colon).trim()
            val value = trimmed.substring(colon + 1).trim()
            if (value.isEmpty()) {
                pendingListKey = key
            } else {
                values[key] = unquote(value)
            }
        }
        flush()
        return FrontMatter(values)
    }

    private fun unquote(value: String): String {
        if (value.length >= 2) {
            val first = value.first()
            val last = value.last()
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                val body = value.substring(1, value.length - 1)
                return when (first) {
                    '"' -> body.replace("\\\"", "\"").replace("\\\\", "\\")
                    '\'' -> body.replace("''", "'")
                    else -> body
                }
            }
        }
        return value
    }

    private fun parseLegacy(directoryId: String, markdown: String): Skill {
        return Skill(
            id = directoryId,
            title = parseLegacyTitle(markdown).ifBlank { directoryId },
            markdown = markdown.trim(),
            allowedTools = parseLegacyAllowedTools(markdown),
            format = SkillFormat.LEGACY_V1
        )
    }

    private fun parseLegacyTitle(markdown: String): String {
        return markdown.lineSequence()
            .firstOrNull { it.startsWith("# ") }
            ?.removePrefix("# ")
            ?.trim()
            .orEmpty()
    }

    private fun parseLegacyAllowedTools(markdown: String): Set<String> {
        val tools = linkedSetOf<String>()
        var inAllowedTools = false

        markdown.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.equals("Allowed initial tools:", ignoreCase = true)) {
                inAllowedTools = true
                return@forEach
            }
            if (inAllowedTools && trimmed.startsWith("#")) {
                inAllowedTools = false
            }
            if (inAllowedTools && trimmed.startsWith("-")) {
                Regex("`([^`]+)`").find(trimmed)?.groupValues?.getOrNull(1)?.let { tool ->
                    tools.add(tool)
                }
            }
        }
        return tools
    }

    private class FrontMatter(private val values: Map<String, Any>) {
        fun scalar(key: String): String? = values[key] as? String

        fun list(key: String): List<String> = when (val value = values[key]) {
            is List<*> -> value.filterIsInstance<String>()
            is String -> if (value.isBlank()) emptyList() else listOf(value)
            else -> emptyList()
        }
    }
}

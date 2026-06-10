package dev.touchpilot.app.memory

import android.content.Context

class SkillStore(context: Context) {
    private val assets = context.applicationContext.assets

    fun loadSkills(): List<Skill> {
        return runCatching {
            assets.list(SkillsRoot).orEmpty()
                .sorted()
                .mapNotNull { id -> loadSkill(id) }
        }.getOrDefault(emptyList())
    }

    private fun loadSkill(id: String): Skill? {
        val path = "$SkillsRoot/$id/SKILL.md"
        val markdown = runCatching {
            assets.open(path).bufferedReader().use { it.readText() }
        }.getOrNull() ?: return null

        return Skill(
            id = id,
            title = parseTitle(markdown).ifBlank { id },
            markdown = markdown.trim(),
            allowedTools = parseAllowedTools(markdown),
            aliases = parseMetadataList(markdown, "aliases", "Aliases"),
            examples = parseMetadataList(markdown, "examples", "Examples")
        )
    }

    private fun parseTitle(markdown: String): String {
        return markdown
            .lineSequence()
            .firstOrNull { it.startsWith("# ") }
            ?.removePrefix("# ")
            ?.trim()
            .orEmpty()
    }

    private fun parseAllowedTools(markdown: String): Set<String> {
        val tools = mutableSetOf<String>()
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

    private fun parseMetadataList(markdown: String, key: String, heading: String): List<String> {
        return (parseFrontMatterList(markdown, key) + parseMarkdownListSection(markdown, heading))
            .map { value -> value.cleanListValue() }
            .filter { value -> value.isNotBlank() }
            .distinct()
    }

    private fun parseFrontMatterList(markdown: String, key: String): List<String> {
        val lines = markdown.lineSequence().toList()
        if (lines.firstOrNull()?.trim() != "---") return emptyList()
        val frontMatter = lines.drop(1).takeWhile { line -> line.trim() != "---" }
        val values = mutableListOf<String>()
        var collecting = false

        frontMatter.forEach { line ->
            val trimmed = line.trim()
            if (collecting && trimmed.startsWith("-")) {
                values += trimmed.removePrefix("-")
                return@forEach
            }
            if (collecting && FrontMatterKeyPattern.matches(trimmed)) {
                collecting = false
            }

            val prefix = "$key:"
            if (trimmed.equals(prefix, ignoreCase = true)) {
                collecting = true
            } else if (trimmed.startsWith(prefix, ignoreCase = true)) {
                values += trimmed.substringAfter(":")
            }
        }

        return values
    }

    private fun parseMarkdownListSection(markdown: String, heading: String): List<String> {
        val values = mutableListOf<String>()
        var inSection = false

        markdown.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (isSectionHeading(trimmed, heading)) {
                inSection = true
                return@forEach
            }
            if (!inSection) return@forEach

            if (trimmed.startsWith("#") || (trimmed.endsWith(":") && !trimmed.startsWith("-"))) {
                inSection = false
                return@forEach
            }
            if (trimmed.startsWith("-")) {
                values += trimmed.removePrefix("-")
            }
        }

        return values
    }

    private fun isSectionHeading(line: String, heading: String): Boolean {
        return line.equals("$heading:", ignoreCase = true) ||
            line.equals("## $heading", ignoreCase = true) ||
            line.equals("### $heading", ignoreCase = true)
    }

    private fun String.cleanListValue(): String {
        return trim()
            .trim('"', '\'')
            .trim()
            .removeSurrounding("`")
            .trim()
    }

    private companion object {
        const val SkillsRoot = "skills"
        val FrontMatterKeyPattern = Regex("[A-Za-z_][A-Za-z0-9_]*:.*")
    }
}

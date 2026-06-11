package dev.touchpilot.app.memory

import dev.touchpilot.app.tools.AndroidToolCatalog
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BundledSkillsV2Test {
    @Test
    fun bundledSkillPackContainsExpectedStarterSkills() {
        val ids = skillFiles().map { file -> skillId(file) }.toSet()

        listOf("settings", "browser", "messages", "app-launch", "device-help").forEach { expected ->
            assertContains(ids, expected)
        }
    }

    @Test
    fun bundledSkillsDeclareRequiredV2Metadata() {
        skillFiles().forEach { file ->
            val frontMatter = parseFrontMatter(file)
            val id = skillId(file)

            assertEquals(id, frontMatter.scalar("id"), "$id id must match its directory")
            RequiredScalarFields.forEach { field ->
                assertTrue(frontMatter.scalar(field).isNotBlank(), "$id missing scalar field: $field")
            }
            RequiredListFields.forEach { field ->
                assertTrue(frontMatter.list(field).isNotEmpty(), "$id missing list field: $field")
            }
            assertContains(setOf("low", "medium", "high"), frontMatter.scalar("risk"), "$id has invalid risk")
        }
    }

    @Test
    fun bundledSkillAllowlistsUseKnownToolsAndRemainLegacyCompatible() {
        val catalogTools = AndroidToolCatalog.initialTools.map { tool -> tool.name }.toSet()

        skillFiles().forEach { file ->
            val id = skillId(file)
            val frontMatterTools = parseFrontMatter(file).list("allowed_tools")
            val legacyTools = parseLegacyAllowedTools(file.readText())

            assertEquals(frontMatterTools, legacyTools, "$id legacy allowlist must mirror front matter")
            frontMatterTools.forEach { tool ->
                assertContains(catalogTools, tool, "$id references unknown tool: $tool")
            }
        }
    }

    @Test
    fun messagesSkillIsHighRiskAndDraftFocused() {
        val file = skillFiles().single { skillId(it) == "messages" }
        val frontMatter = parseFrontMatter(file)
        val markdown = file.readText()

        assertEquals("high", frontMatter.scalar("risk"))
        assertTrue(frontMatter.list("aliases").any { alias -> "draft" in alias || "message" in alias })
        assertTrue(frontMatter.list("examples").any { example -> "draft" in example })
        assertTrue(frontMatter.list("success_criteria").any { criterion -> "approval" in criterion })
        assertContains(markdown, "Draft messages first")
        assertContains(markdown, "Do not tap Send")
    }

    private fun skillFiles(): List<File> {
        val root = listOf(
            File("src/main/assets/skills"),
            File("app/src/main/assets/skills")
        ).firstOrNull { candidate -> candidate.isDirectory }
        assertNotNull(root, "Could not locate bundled skill assets")

        return root.listFiles()
            .orEmpty()
            .filter { file -> file.isDirectory }
            .map { directory -> File(directory, "SKILL.md") }
            .filter { file -> file.isFile }
            .sortedBy { file -> skillId(file) }
    }

    private fun skillId(file: File): String {
        return requireNotNull(file.parentFile) { "Skill file has no parent: ${file.path}" }.name
    }

    private fun parseFrontMatter(file: File): FrontMatter {
        val lines = file.readLines()
        assertEquals("---", lines.firstOrNull(), "${file.path} must start with Skills v2 front matter")
        val closingIndex = lines.drop(1).indexOf("---")
        assertTrue(closingIndex >= 0, "${file.path} must close front matter")

        val fields = mutableMapOf<String, MutableList<String>>()
        var activeListKey: String? = null
        lines.drop(1).take(closingIndex).forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("-")) {
                val key = activeListKey
                assertNotNull(key, "List item without active key in ${file.path}: $line")
                fields.getOrPut(key) { mutableListOf() } += trimmed.removePrefix("-").trim()
                return@forEach
            }

            val key = trimmed.substringBefore(":", missingDelimiterValue = "").trim()
            if (key.isBlank()) return@forEach

            val value = trimmed.substringAfter(":", missingDelimiterValue = "").trim()
            if (value.isBlank()) {
                activeListKey = key
                fields.getOrPut(key) { mutableListOf() }
            } else {
                activeListKey = null
                fields[key] = mutableListOf(value)
            }
        }

        return FrontMatter(fields)
    }

    private fun parseLegacyAllowedTools(markdown: String): List<String> {
        val tools = mutableListOf<String>()
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
                    tools += tool
                }
            }
        }

        return tools
    }

    private data class FrontMatter(
        private val fields: Map<String, List<String>>
    ) {
        fun scalar(key: String): String = fields[key]?.singleOrNull().orEmpty()
        fun list(key: String): List<String> = fields[key].orEmpty()
    }

    private companion object {
        val RequiredScalarFields = listOf("id", "title", "description", "risk")
        val RequiredListFields = listOf("aliases", "allowed_tools", "success_criteria", "examples")
    }
}

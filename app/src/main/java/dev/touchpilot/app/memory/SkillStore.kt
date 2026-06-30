package dev.touchpilot.app.memory

import android.content.Context
import dev.touchpilot.app.tools.AndroidToolCatalog
import java.io.File

class SkillStore(
    context: Context,
    private val knownTools: Set<String> = AndroidToolCatalog.initialTools.map { it.name }.toSet()
) {
    private val assets = context.applicationContext.assets
    private val customSkillsDir = File(context.filesDir, "custom-skills")

    /** Loads valid bundled and user-defined skills. */
    fun loadSkills(): List<Skill> = load().skills

    /** Parses bundled and user-defined skills, separating valid skills from invalid files. */
    fun load(): SkillLoad {
        val results = runCatching {
            parseSkillsFromDirectory()
        }.getOrDefault(emptyList())

        val seenSkills = LinkedHashMap<String, Skill>()
        val skills = mutableListOf<Skill>()
        results.forEach { result ->
            when (result) {
                is SkillParseResult.Valid -> {
                    if (seenSkills.putIfAbsent(result.skill.id, result.skill) == null) {
                        skills += result.skill
                    }
                }
                else -> Unit
            }
        }

        return SkillLoad(
            skills = skills,
            invalid = results.filterIsInstance<SkillParseResult.Invalid>()
        )
    }

    private fun parseSkillsFromDirectory(): List<SkillParseResult> {
        return buildList {
            addAll(parseCustomSkills())
            addAll(parseBundledSkills())
        }
    }

    private fun parseBundledSkills(): List<SkillParseResult> {
        return assets.list(SkillsRoot).orEmpty()
            .sorted()
            .mapNotNull { id -> parseBundledSkill(id) }
    }

    private fun parseCustomSkills(): List<SkillParseResult> {
        val directories = customSkillsDir.listFiles()
            .orEmpty()
            .filter { it.isDirectory }
            .sortedBy { it.name }
        return directories.mapNotNull { directory ->
            parseCustomSkill(directory)
        }
    }

    private fun parseBundledSkill(id: String): SkillParseResult? {
        val markdown = runCatching {
            assets.open("$SkillsRoot/$id/SKILL.md").bufferedReader().use { it.readText() }
        }.getOrNull() ?: return null
        return SkillParser.parse(id, markdown, knownTools)
    }

    private fun parseCustomSkill(directory: File): SkillParseResult? {
        val markdown = runCatching {
            File(directory, "SKILL.md").readText()
        }.getOrNull() ?: return null
        return SkillParser.parse(directory.name, markdown, knownTools, allowDirectoryIdMismatch = true)
    }

    private companion object {
        const val SkillsRoot = "skills"
    }
}

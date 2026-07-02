package dev.touchpilot.app.memory

import dev.touchpilot.app.tools.AndroidToolCatalog
import java.io.File

data class SkillFileLoad(
    val skillIds: Set<String>,
    val load: SkillLoad,
)

/**
 * File-backed local skill storage. Generated candidates are saved here so the
 * app can reload them alongside bundled assets on the next refresh.
 */
class SkillFileStore(
    private val rootDir: File,
    private val knownTools: Set<String> = AndroidToolCatalog.initialTools.map { it.name }.toSet()
) {
    fun load(): SkillLoad {
        return loadDetailed().load
    }

    fun skillIds(): Set<String> {
        return loadDetailed().skillIds
    }

    fun loadDetailed(): SkillFileLoad {
        return scan()
    }

    fun saveIfValid(id: String, markdown: String): SkillParseResult {
        val parsed = SkillParser.parse(id, markdown, knownTools)
        if (parsed is SkillParseResult.Valid) {
            save(id, markdown)
        }
        return parsed
    }

    fun save(id: String, markdown: String): File {
        val normalizedId = id.trim()
        require(normalizedId.isNotBlank()) { "skill id cannot be blank" }
        rootDir.mkdirs()
        val skillDir = File(rootDir, normalizedId).apply { mkdirs() }
        val file = File(skillDir, SkillFileName)
        file.writeText(markdown.trimEnd() + "\n")
        return file
    }

    private fun scan(): SkillFileLoad {
        rootDir.mkdirs()
        val directories = rootDir.listFiles { file -> file.isDirectory }?.sortedBy { it.name.lowercase() }.orEmpty()
        val parsed = mutableListOf<SkillParseResult>()
        val ids = linkedSetOf<String>()
        directories.forEach { directory ->
            val skillFile = File(directory, SkillFileName)
            if (!skillFile.isFile) return@forEach
            ids += directory.name
            val markdown = runCatching { skillFile.readText() }.getOrNull() ?: return@forEach
            parsed += SkillParser.parse(directory.name, markdown, knownTools)
        }
        return SkillFileLoad(
            skillIds = ids,
            load = SkillLoad(
                skills = parsed.filterIsInstance<SkillParseResult.Valid>().map { it.skill },
                invalid = parsed.filterIsInstance<SkillParseResult.Invalid>(),
            ),
        )
    }

    private companion object {
        const val SkillFileName = "SKILL.md"
    }
}

object SkillLoadMerger {
    fun merge(bundled: SkillLoad, local: SkillLoad, shadowedIds: Set<String>): SkillLoad {
        val merged = linkedMapOf<String, Skill>()
        bundled.skills
            .filter { it.id !in shadowedIds }
            .forEach { merged[it.id] = it }
        local.skills.forEach { merged[it.id] = it }

        return SkillLoad(
            skills = merged.values.toList(),
            invalid = bundled.invalid.filter { it.id !in shadowedIds } + local.invalid
        )
    }
}

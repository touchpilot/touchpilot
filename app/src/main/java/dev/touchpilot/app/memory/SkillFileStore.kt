package dev.touchpilot.app.memory

import dev.touchpilot.app.tools.AndroidToolCatalog
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class SkillFileLoad(
    /** Valid local skill ids that should shadow bundled skills during merge. */
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
        val tempFile = File.createTempFile("skill-$normalizedId-", ".tmp", skillDir)
        try {
            tempFile.writeText(markdown.trimEnd() + "\n")
            moveReplacingExisting(tempFile, file)
        } finally {
            tempFile.delete()
        }
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
            val markdown = runCatching { skillFile.readText() }.getOrNull() ?: return@forEach
            when (val result = SkillParser.parse(directory.name, markdown, knownTools)) {
                is SkillParseResult.Valid -> {
                    ids += result.skill.id
                    parsed += result
                }
                is SkillParseResult.Invalid -> parsed += result
            }
        }
        return SkillFileLoad(
            skillIds = ids,
            load = SkillLoad(
                skills = parsed.filterIsInstance<SkillParseResult.Valid>().map { it.skill },
                invalid = parsed.filterIsInstance<SkillParseResult.Invalid>(),
            ),
        )
    }

    private fun moveReplacingExisting(source: File, target: File) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
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

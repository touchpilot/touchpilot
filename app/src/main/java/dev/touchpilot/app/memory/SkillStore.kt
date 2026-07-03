package dev.touchpilot.app.memory

import android.content.Context
import dev.touchpilot.app.tools.AndroidToolCatalog
import java.io.File

class SkillStore(
    context: Context,
    private val knownTools: Set<String> = AndroidToolCatalog.initialTools.map { it.name }.toSet()
) {
    private val assets = context.applicationContext.assets
    private val localStore = SkillFileStore(File(context.filesDir, SkillsRoot), knownTools)

    /** Valid skills only. Kept for callers that do not need diagnostics. */
    fun loadSkills(): List<Skill> = load().skills

    /** Parses bundled skills plus local overrides, separating valid and invalid files. */
    fun load(): SkillLoad {
        val bundled = runCatching {
            assets.list(SkillsRoot).orEmpty()
                .sorted()
                .mapNotNull { id -> parseSkill(id) }
        }.getOrDefault(emptyList())

        val bundledLoad = SkillLoad(
            skills = bundled.filterIsInstance<SkillParseResult.Valid>().map { it.skill },
            invalid = bundled.filterIsInstance<SkillParseResult.Invalid>()
        )
        val localLoad = localStore.loadDetailed()

        return SkillLoadMerger.merge(bundledLoad, localLoad.load, localLoad.skillIds)
    }

    private fun parseSkill(id: String): SkillParseResult? {
        val markdown = runCatching {
            assets.open("$SkillsRoot/$id/SKILL.md").bufferedReader().use { it.readText() }
        }.getOrNull() ?: return null
        return SkillParser.parse(id, markdown, knownTools)
    }

    private companion object {
        const val SkillsRoot = "skills"
    }
}

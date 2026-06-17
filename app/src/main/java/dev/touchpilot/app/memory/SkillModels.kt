package dev.touchpilot.app.memory

/**
 * Skill-level risk hint declared in Skills v2 front matter (see docs/SKILLS.md).
 * Advisory context only: it may make downstream prompt and approval copy more
 * cautious, but it never lowers the risk of an individual tool call or bypasses
 * the allowlist/policy enforcement boundaries.
 */
enum class SkillRisk {
    LOW,
    MEDIUM,
    HIGH;

    companion object {
        /** Parses a wire value (`low`/`medium`/`high`); returns null for anything else. */
        fun fromWire(value: String): SkillRisk? = when (value.trim().lowercase()) {
            "low" -> LOW
            "medium" -> MEDIUM
            "high" -> HIGH
            else -> null
        }
    }
}

/** How a bundled `SKILL.md` file was parsed. */
enum class SkillFormat {
    /** Legacy heading + "Allowed initial tools:" list. Carries no structured metadata. */
    LEGACY_V1,

    /** Skills v2 front matter (see docs/SKILLS.md). */
    V2
}

data class Skill(
    val id: String,
    val title: String,
    val markdown: String,
    val allowedTools: Set<String>,
    val description: String = "",
    val risk: SkillRisk = SkillRisk.LOW,
    val aliases: List<String> = emptyList(),
    val examples: List<String> = emptyList(),
    val successCriteria: List<String> = emptyList(),
    val format: SkillFormat = SkillFormat.LEGACY_V1
)

/** Result of parsing a single `SKILL.md` file. */
sealed class SkillParseResult {
    abstract val id: String

    data class Valid(val skill: Skill) : SkillParseResult() {
        override val id: String get() = skill.id
    }

    /**
     * The file declared Skills v2 front matter but failed validation. Carries
     * every problem found so broken skills are diagnosable from tests and
     * developer logs instead of silently losing metadata.
     */
    data class Invalid(
        override val id: String,
        val errors: List<String>
    ) : SkillParseResult()
}

/** Outcome of loading the bundled skill pack. */
data class SkillLoad(
    val skills: List<Skill>,
    val invalid: List<SkillParseResult.Invalid>
)

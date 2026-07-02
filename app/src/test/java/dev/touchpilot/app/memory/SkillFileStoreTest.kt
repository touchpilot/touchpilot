package dev.touchpilot.app.memory

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SkillFileStoreTest {
    private val knownTools = setOf("tap")

    @Test
    fun savesAndLoadsLocalSkills() {
        val root = createTempDirectory().toFile()
        try {
            val store = SkillFileStore(root, knownTools)
            val markdown = """
                ---
                id: 'demo-skill'
                title: 'Demo Skill'
                description: 'A demo skill.'
                risk: 'low'
                allowed_tools:
                  - 'tap'
                ---

                # Demo Skill

                Use the demo skill carefully.
            """.trimIndent()

            val saved = store.saveIfValid("demo-skill", markdown)
            assertIs<SkillParseResult.Valid>(saved)

            val load = store.loadDetailed()
            assertEquals(setOf("demo-skill"), load.skillIds)
            assertEquals(1, load.load.skills.size)
            assertEquals("Demo Skill", load.load.skills.first().title)
            assertTrue(load.load.invalid.isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }
}


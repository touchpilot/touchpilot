package dev.touchpilot.app.localinference

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalModelManifestTest {
    @Test
    fun bundledManifestIsValidAndComplete() {
        val manifest = LocalModelManifest.parse(readBundledManifest())

        assertTrue(manifest.isValid, manifest.validationErrors().toString())
        assertEquals("command_router", manifest.role)
        assertEquals("LiteRT", manifest.runtime)
        assertEquals("models/command_router/model.tflite", manifest.modelAsset)
        assertTrue(manifest.version.isNotBlank())
        assertEquals(LocalModelManifest.SUPPORTED_CONTRACT_VERSION, manifest.contractVersion)
    }

    @Test
    fun parseAppliesDefaultsForOmittedFields() {
        val manifest = LocalModelManifest.parse("{}")

        assertEquals(LocalModelManifest.DefaultRole, manifest.role)
        assertEquals(LocalModelManifest.DefaultRuntime, manifest.runtime)
        assertEquals(LocalModelManifest.DefaultModelAsset, manifest.modelAsset)
        assertEquals(LocalModelManifest.SUPPORTED_CONTRACT_VERSION, manifest.contractVersion)
        assertNull(manifest.tokenizerAsset)
        assertTrue(manifest.isValid)
    }

    @Test
    fun parseReadsTokenizerAndTreatsBlankAsNull() {
        assertEquals(
            "models/command_router/tokenizer.json",
            LocalModelManifest.parse(
                """{"tokenizer_asset":"models/command_router/tokenizer.json"}""",
            ).tokenizerAsset,
        )
        assertNull(LocalModelManifest.parse("""{"tokenizer_asset":""}""").tokenizerAsset)
    }

    @Test
    fun blankRequiredFieldsAreInvalid() {
        val manifest = valid().copy(role = "", runtime = "", modelAsset = "", version = "")
        val errors = manifest.validationErrors()

        assertFalse(manifest.isValid)
        assertTrue(errors.any { it.contains("role") }, errors.toString())
        assertTrue(errors.any { it.contains("runtime") }, errors.toString())
        assertTrue(errors.any { it.contains("model_asset") }, errors.toString())
        assertTrue(errors.any { it.contains("version") }, errors.toString())
    }

    @Test
    fun contractVersionMustBeSupported() {
        val tooNew = valid().copy(contractVersion = LocalModelManifest.SUPPORTED_CONTRACT_VERSION + 1)
        assertFalse(tooNew.isValid)
        assertTrue(
            tooNew.validationErrors().any { it.contains("contract_version") },
            tooNew.validationErrors().toString(),
        )

        val tooLow = valid().copy(contractVersion = 0)
        assertFalse(tooLow.isValid)
        assertTrue(
            tooLow.validationErrors().any { it.contains("contract_version") },
            tooLow.validationErrors().toString(),
        )
    }

    private fun valid() = LocalModelManifest(
        role = "command_router",
        runtime = "LiteRT",
        modelAsset = "models/command_router/model.tflite",
        tokenizerAsset = null,
        version = "tiny-router-1",
        contractVersion = LocalModelManifest.SUPPORTED_CONTRACT_VERSION,
    )

    private fun readBundledManifest(): String {
        val candidates = listOf(
            File("src/main/assets/models/command_router/manifest.json"),
            File("app/src/main/assets/models/command_router/manifest.json"),
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Missing bundled manifest.json")
        return file.readText()
    }
}

package dev.touchpilot.app.workflow

object WorkflowPreflight {
    sealed class Result {
        data object Ok : Result()

        data class Mismatch(
            val expectedPackage: String,
            val expectedLabel: String?,
            val currentPackage: String?,
            val currentLabel: String?,
        ) : Result() {
            fun userMessage(): String {
                val expected = expectedLabel?.takeIf { it.isNotBlank() } ?: expectedPackage
                val current = currentLabel?.takeIf { it.isNotBlank() }
                    ?: currentPackage?.takeIf { it.isNotBlank() }
                    ?: "an unknown app"
                return "Expected \"$expected\" in the foreground, but \"$current\" is active."
            }
        }
    }

    fun check(definition: WorkflowDefinition, live: WorkflowLivePolicyContext): Result {
        val expectedPackage = definition.expectedForegroundPackage?.takeIf { it.isNotBlank() }
            ?: return Result.Ok

        val currentPackage = live.foregroundPackage?.takeIf { it.isNotBlank() }
        if (currentPackage != null && currentPackage.equals(expectedPackage, ignoreCase = true)) {
            return Result.Ok
        }

        val currentLabel = live.foregroundAppLabel?.takeIf { it.isNotBlank() }
        return Result.Mismatch(
            expectedPackage = expectedPackage,
            expectedLabel = null,
            currentPackage = currentPackage,
            currentLabel = currentLabel,
        )
    }
}

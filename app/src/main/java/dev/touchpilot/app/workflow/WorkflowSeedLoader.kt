package dev.touchpilot.app.workflow

import android.content.Context

object WorkflowSeedLoader {
    fun load(context: Context): List<WorkflowDefinition> {
        val assetManager = context.applicationContext.assets
        return assetManager.list(Root)
            .orEmpty()
            .sorted()
            .mapNotNull { workflowId ->
                val json = runCatching {
                    assetManager.open("$Root/$workflowId/workflow.json").bufferedReader().use { it.readText() }
                }.getOrNull() ?: return@mapNotNull null
                when (val parsed = WorkflowDefinitionParser.parse(json)) {
                    is WorkflowParseResult.Valid -> parsed.definition
                    is WorkflowParseResult.Invalid -> null
                }
            }
    }

    private const val Root = "workflows"
}

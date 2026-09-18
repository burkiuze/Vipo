package com.example.data.model

/**
 * A plugin is a behaviour module for the local model: when it is on, its instruction block is
 * merged into the system prompt used for every generation in the app. No network, no extra
 * binaries - everything stays on device.
 */
data class VipoPlugin(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val iconKey: String,
    val instruction: String
)

object PluginRegistry {

    const val CATEGORY_STYLE = "Response style"
    const val CATEGORY_SKILLS = "Skills"
    const val CATEGORY_WORKFLOW = "Workflow"

    val all: List<VipoPlugin> = listOf(
        VipoPlugin(
            id = "markdown",
            name = "Markdown formatting",
            description = "Answers use headings, lists and code blocks instead of one long paragraph.",
            category = CATEGORY_STYLE,
            iconKey = "format",
            instruction = "Format answers in Markdown: short paragraphs, bullet lists for enumerations " +
                "and fenced code blocks with a language tag for code."
        ),
        VipoPlugin(
            id = "concise",
            name = "Concise answers",
            description = "Keeps replies short and to the point - useful on slow devices.",
            category = CATEGORY_STYLE,
            iconKey = "short",
            instruction = "Answer as briefly as the question allows. Lead with the answer, skip preamble " +
                "and repetition, and stop once the question is answered."
        ),
        VipoPlugin(
            id = "language",
            name = "Match my language",
            description = "Replies in the same language the message was written in.",
            category = CATEGORY_STYLE,
            iconKey = "language",
            instruction = "Always reply in the same language the user wrote in, including technical terms " +
                "where a natural translation exists."
        ),
        VipoPlugin(
            id = "coding",
            name = "Coding assistant",
            description = "Complete, runnable code with the reasoning kept brief.",
            category = CATEGORY_SKILLS,
            iconKey = "code",
            instruction = "For programming questions: give complete, runnable code in a fenced block with the " +
                "language tag, note the assumptions you made, and keep the prose around the code short."
        ),
        VipoPlugin(
            id = "stepbystep",
            name = "Step by step",
            description = "Works through problems in numbered steps before the final answer.",
            category = CATEGORY_SKILLS,
            iconKey = "steps",
            instruction = "For maths, logic or multi-part questions, work through the problem in short " +
                "numbered steps, then state the final answer on its own line prefixed with 'Answer:'."
        ),
        VipoPlugin(
            id = "summarize",
            name = "Summarizer",
            description = "Long input is condensed into key points first.",
            category = CATEGORY_SKILLS,
            iconKey = "summary",
            instruction = "When the user pastes a long text, start with a 3-5 bullet summary of the key " +
                "points before answering anything else about it."
        ),
        VipoPlugin(
            id = "teacher",
            name = "Explain simply",
            description = "Explains with everyday analogies and no unexplained jargon.",
            category = CATEGORY_SKILLS,
            iconKey = "school",
            instruction = "Explain concepts in plain language with a concrete everyday analogy, and define " +
                "any technical term the first time you use it."
        ),
        VipoPlugin(
            id = "followups",
            name = "Follow-up suggestions",
            description = "Ends answers with two suggested next questions.",
            category = CATEGORY_WORKFLOW,
            iconKey = "followup",
            instruction = "End each answer with a line 'Next:' followed by two short follow-up questions the " +
                "user could ask."
        ),
        VipoPlugin(
            id = "nofluff",
            name = "No disclaimers",
            description = "Drops apologies and 'as an AI' boilerplate.",
            category = CATEGORY_WORKFLOW,
            iconKey = "clean",
            instruction = "Never open with apologies, self-description or disclaimers about being an AI. " +
                "If something is uncertain, say so in one short clause and continue."
        )
    )

    val defaultEnabledIds: Set<String> = setOf("markdown", "language")

    fun byId(id: String): VipoPlugin? = all.firstOrNull { it.id == id }

    fun categories(): List<String> = all.map { it.category }.distinct()

    /**
     * Merges the conversation's own system prompt with the instruction blocks of every enabled
     * plugin. Order is stable so the model sees the same prompt for the same plugin set.
     */
    fun buildSystemPrompt(basePrompt: String, enabledIds: Set<String>): String {
        val active = all.filter { enabledIds.contains(it.id) }
        if (active.isEmpty()) return basePrompt

        val builder = StringBuilder()
        if (basePrompt.isNotBlank()) {
            builder.append(basePrompt.trim())
            builder.append("\n\n")
        }
        builder.append("Active plugins:")
        active.forEach { plugin ->
            builder.append("\n- ${plugin.name}: ${plugin.instruction}")
        }
        return builder.toString()
    }
}

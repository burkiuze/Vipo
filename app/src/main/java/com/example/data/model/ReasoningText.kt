package com.example.data.model

/** An assistant message split into its reasoning block and the answer itself. */
data class ReasonedMessage(
    val thinking: String,
    val answer: String,
    val isThinkingOpen: Boolean
) {
    val hasThinking: Boolean get() = thinking.isNotBlank() || isThinkingOpen
}

/**
 * Reasoning models (DeepSeek-R1, Qwen 3, ...) wrap their scratchpad in `<think>` tags. The raw
 * text is stored as the model produced it; this splits it for display and for the history that is
 * fed back into the model.
 */
object ReasoningText {

    private val tagPairs = listOf(
        "<think>" to "</think>",
        "<thinking>" to "</thinking>",
        "<reasoning>" to "</reasoning>"
    )

    fun split(raw: String): ReasonedMessage {
        for ((open, close) in tagPairs) {
            val start = raw.indexOf(open, ignoreCase = true)
            if (start < 0) continue

            val contentStart = start + open.length
            val end = raw.indexOf(close, startIndex = contentStart, ignoreCase = true)

            return if (end < 0) {
                ReasonedMessage(
                    thinking = raw.substring(contentStart).trim(),
                    answer = raw.substring(0, start).trim(),
                    isThinkingOpen = true
                )
            } else {
                val before = raw.substring(0, start)
                val after = raw.substring(end + close.length)
                ReasonedMessage(
                    thinking = raw.substring(contentStart, end).trim(),
                    answer = (before + after).trim(),
                    isThinkingOpen = false
                )
            }
        }
        return ReasonedMessage(thinking = "", answer = raw.trim(), isThinkingOpen = false)
    }

    /** The answer alone - what gets copied, and what goes back to the model as history. */
    fun answerOf(raw: String): String = split(raw).answer
}

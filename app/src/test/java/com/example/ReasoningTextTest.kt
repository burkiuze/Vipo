package com.example

import com.example.data.model.ReasoningText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReasoningTextTest {

    @Test
    fun `plain answers have no thinking block`() {
        val parsed = ReasoningText.split("Merhaba, nasil yardimci olabilirim?")
        assertEquals("Merhaba, nasil yardimci olabilirim?", parsed.answer)
        assertTrue(parsed.thinking.isEmpty())
        assertFalse(parsed.hasThinking)
    }

    @Test
    fun `closed think block is split from the answer`() {
        val parsed = ReasoningText.split("<think>The user greets me.</think>Hello!")
        assertEquals("The user greets me.", parsed.thinking)
        assertEquals("Hello!", parsed.answer)
        assertFalse(parsed.isThinkingOpen)
    }

    @Test
    fun `an unfinished block still streams as thinking`() {
        val parsed = ReasoningText.split("<think>Let me work this out")
        assertEquals("Let me work this out", parsed.thinking)
        assertEquals("", parsed.answer)
        assertTrue(parsed.isThinkingOpen)
        assertTrue(parsed.hasThinking)
    }

    @Test
    fun `only the answer is fed back into the model`() {
        assertEquals("42", ReasoningText.answerOf("<think>6 times 7</think>42"))
    }
}

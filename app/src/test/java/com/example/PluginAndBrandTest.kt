package com.example

import com.example.data.model.PluginRegistry
import com.example.ui.components.ModelBrand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginAndBrandTest {

    @Test
    fun `enabled plugins are merged into the system prompt`() {
        val base = "You are Vipo."
        val prompt = PluginRegistry.buildSystemPrompt(base, setOf("concise", "coding"))

        assertTrue("base prompt is kept", prompt.startsWith(base))
        assertTrue("enabled plugin is present", prompt.contains("Concise answers"))
        assertTrue("enabled plugin is present", prompt.contains("Coding assistant"))
        assertFalse("disabled plugin is absent", prompt.contains("Summarizer"))
    }

    @Test
    fun `no plugins leaves the prompt untouched`() {
        val base = "You are Vipo."
        assertEquals(base, PluginRegistry.buildSystemPrompt(base, emptySet()))
    }

    @Test
    fun `default plugins exist in the registry`() {
        PluginRegistry.defaultEnabledIds.forEach { id ->
            assertNotNull("default plugin $id is registered", PluginRegistry.byId(id))
        }
        assertEquals(
            "plugin ids are unique",
            PluginRegistry.all.size,
            PluginRegistry.all.map { it.id }.toSet().size
        )
    }

    @Test
    fun `model families resolve from catalog fields`() {
        assertEquals(ModelBrand.META, ModelBrand.of("Llama 3.2 1B Instruct", "Meta", "llama"))
        assertEquals(ModelBrand.QWEN, ModelBrand.of("Qwen 2.5 1.5B Instruct", "Alibaba Cloud", "qwen2"))
        assertEquals(ModelBrand.GEMMA, ModelBrand.of("Gemma 2 2B IT", "Google", "gemma2"))
        assertEquals(ModelBrand.PHI, ModelBrand.of("Phi 3.5 Mini Instruct", "Microsoft", "phi3"))
        assertEquals(ModelBrand.SMOLLM, ModelBrand.of("SmolLM2 360M Instruct", "Hugging Face", "llama"))
        assertEquals(ModelBrand.TINYLLAMA, ModelBrand.of("TinyLlama 1.1B Chat", "TinyLlama Project", "llama"))
        // DeepSeek distills carry another family's architecture, the name has to win.
        assertEquals(ModelBrand.DEEPSEEK, ModelBrand.of("DeepSeek R1 Distill Qwen 1.5B", "DeepSeek", "qwen2"))
        assertEquals(ModelBrand.GENERIC, ModelBrand.of("my-custom-model", null, null))
    }
}

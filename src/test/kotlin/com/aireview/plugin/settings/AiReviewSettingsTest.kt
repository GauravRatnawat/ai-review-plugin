package com.aireview.plugin.settings

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AiReviewSettingsTest {

    @Test
    fun `default state has correct values`() {
        val state = AiReviewSettings.State()
        assertEquals(AiProvider.GITHUB_MODELS.name, state.provider)
        assertEquals("", state.apiKey)
        assertEquals(AiProvider.GITHUB_MODELS.defaultModel, state.modelName)
        assertTrue(state.enabled)
        assertEquals(500, state.maxDiffLines)
        assertTrue(state.reviewOnSave)
    }

    @Test
    fun `loadState applies new state`() {
        val settings = AiReviewSettings()
        val newState = AiReviewSettings.State(
            provider = AiProvider.CLAUDE.name,
            modelName = "claude-opus-4-5",
            enabled = false,
            maxDiffLines = 1000,
            reviewOnSave = false,
        )

        settings.loadState(newState)

        assertEquals(AiProvider.CLAUDE, settings.provider)
        assertEquals("claude-opus-4-5", settings.modelName)
        assertFalse(settings.isEnabled)
        assertEquals(1000, settings.maxDiffLines)
        assertFalse(settings.reviewOnSave)
    }

    @Test
    fun `provider falls back to GITHUB_MODELS for invalid value`() {
        val settings = AiReviewSettings()
        settings.loadState(AiReviewSettings.State(provider = "INVALID_PROVIDER"))

        assertEquals(AiProvider.GITHUB_MODELS, settings.provider)
    }

    @Test
    fun `getState returns current state`() {
        val settings = AiReviewSettings()
        val state = settings.state
        assertNotNull(state)
        assertEquals(AiProvider.GITHUB_MODELS.name, state.provider)
    }

    @Test
    fun `state round-trip preserves values`() {
        val settings = AiReviewSettings()
        val original = AiReviewSettings.State(
            provider = AiProvider.GITHUB_MODELS_MINI.name,
            modelName = "openai/gpt-4.1-mini",
            enabled = true,
            maxDiffLines = 250,
            reviewOnSave = false,
        )

        settings.loadState(original)
        val restored = settings.state

        assertEquals(original.provider, restored.provider)
        assertEquals(original.modelName, restored.modelName)
        assertEquals(original.enabled, restored.enabled)
        assertEquals(original.maxDiffLines, restored.maxDiffLines)
        assertEquals(original.reviewOnSave, restored.reviewOnSave)
    }

    // --- AiProvider enum tests ---

    @Test
    fun `AiProvider has three entries`() {
        assertEquals(3, AiProvider.entries.size)
    }

    @Test
    fun `AiProvider displayName is human readable`() {
        assertTrue(AiProvider.GITHUB_MODELS.displayName.contains("GitHub"))
        assertTrue(AiProvider.CLAUDE.displayName.contains("Claude"))
    }

    @Test
    fun `AiProvider toString returns displayName`() {
        assertEquals(AiProvider.GITHUB_MODELS.displayName, AiProvider.GITHUB_MODELS.toString())
    }

    @Test
    fun `AiProvider each has non-empty available models`() {
        for (provider in AiProvider.entries) {
            assertTrue(provider.availableModels.isNotEmpty(), "${provider.name} has no models")
            assertTrue(provider.defaultModel in provider.availableModels,
                "${provider.name} default model not in available models")
        }
    }
}

package com.aireview.plugin.settings

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

enum class AiProvider(val displayName: String, val defaultModel: String, val availableModels: List<String>) {
    GITHUB_MODELS(
        displayName = "GitHub Models (GPT-4.1)",
        defaultModel = "openai/gpt-4.1",
        availableModels = listOf(
            "openai/gpt-4.1",
            "openai/gpt-4.1-mini",
            "openai/gpt-4o",
            "openai/gpt-4o-mini",
            "openai/o3",
            "openai/o3-mini",
            "meta/meta-llama-3.1-405b-instruct",
            "mistral-ai/mistral-medium-2505",
            "deepseek/deepseek-r1-0528",
            "xai/grok-3",
        ),
    ),
    GITHUB_MODELS_MINI(
        displayName = "GitHub Models (GPT-4.1-mini)",
        defaultModel = "openai/gpt-4.1-mini",
        availableModels = listOf(
            "openai/gpt-4.1-mini",
            "openai/gpt-4.1-nano",
            "openai/gpt-4o-mini",
            "openai/o3-mini",
            "openai/o4-mini",
            "mistral-ai/mistral-small-2503",
            "xai/grok-3-mini",
        ),
    ),
    CLAUDE(
        displayName = "Claude (Anthropic Direct)",
        defaultModel = "claude-sonnet-4-5-20250514",
        availableModels = listOf(
            "claude-sonnet-4-5-20250514",
            "claude-opus-4-5",
            "claude-3-7-sonnet-20250219",
            "claude-3-5-sonnet-20241022",
        ),
    );

    override fun toString() = displayName
}

@Service
@State(
    name = "AiReviewSettings",
    storages = [Storage("ai-review-plugin.xml")],
)
class AiReviewSettings : PersistentStateComponent<AiReviewSettings.State> {

    data class State(
        var provider: String = AiProvider.GITHUB_MODELS.name,
        @Deprecated("Migrated to PasswordSafe. Kept for one-time migration only.")
        var apiKey: String = "",
        var modelName: String = AiProvider.GITHUB_MODELS.defaultModel,
        var enabled: Boolean = true,
        var maxDiffLines: Int = 500,
        var reviewOnSave: Boolean = true,
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
        migrateApiKeyToPasswordSafe()
    }

    val provider: AiProvider
        get() = try {
            AiProvider.valueOf(myState.provider)
        } catch (_: Exception) {
            AiProvider.GITHUB_MODELS
        }

    /** Retrieves the API key from PasswordSafe (secure OS credential store). */
    val apiKey: String
        get() = PasswordSafe.instance.getPassword(credentialAttributes) ?: ""

    val modelName: String get() = myState.modelName
    val isEnabled: Boolean get() = myState.enabled
    val maxDiffLines: Int get() = myState.maxDiffLines
    val reviewOnSave: Boolean get() = myState.reviewOnSave

    /** Stores the API key securely in PasswordSafe. */
    fun setApiKey(key: String) {
        PasswordSafe.instance.setPassword(credentialAttributes, key.ifBlank { null })
    }

    /**
     * One-time migration: if an API key was stored in plain-text XML (pre-PasswordSafe),
     * move it to PasswordSafe and clear it from the XML state.
     */
    @Suppress("DEPRECATION")
    private fun migrateApiKeyToPasswordSafe() {
        val legacyKey = myState.apiKey
        if (legacyKey.isNotBlank()) {
            PasswordSafe.instance.setPassword(credentialAttributes, legacyKey)
            myState.apiKey = ""
        }
    }

    companion object {
        private const val CREDENTIAL_SERVICE = "AiCodeReviewPlugin"
        private const val CREDENTIAL_KEY = "apiKey"

        private val credentialAttributes = CredentialAttributes(
            generateServiceName(CREDENTIAL_SERVICE, CREDENTIAL_KEY),
        )

        fun getInstance(): AiReviewSettings =
            ApplicationManager.getApplication().getService(AiReviewSettings::class.java)
    }
}

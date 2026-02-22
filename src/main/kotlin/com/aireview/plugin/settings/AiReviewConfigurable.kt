package com.aireview.plugin.settings

import com.intellij.openapi.options.Configurable
import javax.swing.*
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets

class AiReviewConfigurable : Configurable {

    private var panel: JPanel? = null
    private var providerComboBox: JComboBox<AiProvider>? = null
    private var apiKeyField: JPasswordField? = null
    private var apiKeyLabel: JLabel? = null
    private var modelComboBox: JComboBox<String>? = null
    private var enabledCheckbox: JCheckBox? = null
    private var maxDiffLinesSpinner: JSpinner? = null
    private var reviewOnSaveCheckbox: JCheckBox? = null
    private var apiKeyHintLabel: JLabel? = null

    override fun getDisplayName(): String = "AI Code Review"

    override fun createComponent(): JComponent {
        val settings = AiReviewSettings.getInstance()

        panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            insets = Insets(4, 4, 4, 4)
            anchor = GridBagConstraints.WEST
        }

        // Enabled checkbox
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2
        enabledCheckbox = JCheckBox("Enable AI Code Review", settings.isEnabled)
        panel!!.add(enabledCheckbox, gbc)

        // Review on save
        gbc.gridy = 1
        reviewOnSaveCheckbox = JCheckBox("Review on file save", settings.reviewOnSave)
        panel!!.add(reviewOnSaveCheckbox, gbc)

        // Provider
        gbc.gridwidth = 1
        gbc.gridy = 2; gbc.gridx = 0; gbc.weightx = 0.0
        panel!!.add(JLabel("AI Provider:"), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        providerComboBox = JComboBox(AiProvider.entries.toTypedArray())
        providerComboBox!!.selectedItem = settings.provider
        providerComboBox!!.addActionListener { onProviderChanged() }
        panel!!.add(providerComboBox, gbc)

        // API Key label + field
        gbc.gridy = 3; gbc.gridx = 0; gbc.weightx = 0.0
        apiKeyLabel = JLabel(apiKeyLabelText(settings.provider))
        panel!!.add(apiKeyLabel, gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        apiKeyField = JPasswordField(settings.apiKey, 40)
        panel!!.add(apiKeyField, gbc)

        // API Key hint
        gbc.gridy = 4; gbc.gridx = 1; gbc.weightx = 1.0
        apiKeyHintLabel = JLabel(apiKeyHintText(settings.provider))
        apiKeyHintLabel!!.font = apiKeyHintLabel!!.font.deriveFont(10f)
        apiKeyHintLabel!!.foreground = java.awt.Color.GRAY
        panel!!.add(apiKeyHintLabel, gbc)

        // Model
        gbc.gridy = 5; gbc.gridx = 0; gbc.weightx = 0.0
        panel!!.add(JLabel("Model:"), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        modelComboBox = JComboBox(settings.provider.availableModels.toTypedArray())
        modelComboBox!!.isEditable = true  // allow custom model names too
        modelComboBox!!.selectedItem = settings.modelName
        panel!!.add(modelComboBox, gbc)

        // Max diff lines
        gbc.gridy = 6; gbc.gridx = 0; gbc.weightx = 0.0
        panel!!.add(JLabel("Max diff lines:"), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        maxDiffLinesSpinner = JSpinner(SpinnerNumberModel(settings.maxDiffLines, 50, 5000, 50))
        panel!!.add(maxDiffLinesSpinner, gbc)

        // Spacer
        gbc.gridy = 7; gbc.gridx = 0; gbc.weighty = 1.0; gbc.gridwidth = 2
        panel!!.add(JPanel(), gbc)

        return panel!!
    }

    private fun onProviderChanged() {
        val selected = providerComboBox!!.selectedItem as AiProvider
        apiKeyLabel!!.text = apiKeyLabelText(selected)
        apiKeyHintLabel!!.text = apiKeyHintText(selected)

        // Rebuild model dropdown for the selected provider
        val currentModel = modelComboBox!!.selectedItem?.toString() ?: ""
        modelComboBox!!.removeAllItems()
        selected.availableModels.forEach { modelComboBox!!.addItem(it) }

        // Keep current selection if it's valid for the new provider, else use default
        if (currentModel in selected.availableModels) {
            modelComboBox!!.selectedItem = currentModel
        } else {
            modelComboBox!!.selectedItem = selected.defaultModel
        }
    }

    private fun apiKeyLabelText(provider: AiProvider): String = when (provider) {
        AiProvider.GITHUB_MODELS, AiProvider.GITHUB_MODELS_MINI -> "GitHub Token:"
        AiProvider.CLAUDE -> "Anthropic API Key:"
    }

    private fun apiKeyHintText(provider: AiProvider): String = when (provider) {
        AiProvider.GITHUB_MODELS, AiProvider.GITHUB_MODELS_MINI ->
            "Settings → Developer settings → Personal access tokens (models:read scope)"

        AiProvider.CLAUDE ->
            "Get your key at: https://console.anthropic.com/settings/keys"
    }

    override fun isModified(): Boolean {
        val settings = AiReviewSettings.getInstance()
        return (providerComboBox!!.selectedItem as AiProvider) != settings.provider
            || String(apiKeyField!!.password) != settings.apiKey
            || modelComboBox!!.selectedItem?.toString() != settings.modelName
            || enabledCheckbox!!.isSelected != settings.isEnabled
            || (maxDiffLinesSpinner!!.value as Int) != settings.maxDiffLines
            || reviewOnSaveCheckbox!!.isSelected != settings.reviewOnSave
    }

    override fun apply() {
        val settings = AiReviewSettings.getInstance()
        val state = settings.state
        state.provider = (providerComboBox!!.selectedItem as AiProvider).name
        state.apiKey = String(apiKeyField!!.password)
        state.modelName =
            modelComboBox!!.selectedItem?.toString() ?: (providerComboBox!!.selectedItem as AiProvider).defaultModel
        state.enabled = enabledCheckbox!!.isSelected
        state.maxDiffLines = maxDiffLinesSpinner!!.value as Int
        state.reviewOnSave = reviewOnSaveCheckbox!!.isSelected
        settings.loadState(state)
    }

    override fun reset() {
        val settings = AiReviewSettings.getInstance()
        providerComboBox!!.selectedItem = settings.provider
        onProviderChanged()  // rebuilds model dropdown first
        apiKeyField!!.text = settings.apiKey
        modelComboBox!!.selectedItem = settings.modelName
        enabledCheckbox!!.isSelected = settings.isEnabled
        maxDiffLinesSpinner!!.value = settings.maxDiffLines
        reviewOnSaveCheckbox!!.isSelected = settings.reviewOnSave
    }

    override fun disposeUIResources() {
        panel = null
        providerComboBox = null
        apiKeyField = null
        apiKeyLabel = null
        apiKeyHintLabel = null
        modelComboBox = null
        enabledCheckbox = null
        maxDiffLinesSpinner = null
        reviewOnSaveCheckbox = null
    }
}

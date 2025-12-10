package com.github.alphapaca.claudeclient.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.alphapaca.claudeclient.domain.model.LLMModel
import com.github.alphapaca.claudeclient.domain.usecase.GetMaxTokensUseCase
import com.github.alphapaca.claudeclient.domain.usecase.GetModelUseCase
import com.github.alphapaca.claudeclient.domain.usecase.GetSystemPromptUseCase
import com.github.alphapaca.claudeclient.domain.usecase.GetTemperatureUseCase
import com.github.alphapaca.claudeclient.domain.usecase.SetMaxTokensUseCase
import com.github.alphapaca.claudeclient.domain.usecase.SetModelUseCase
import com.github.alphapaca.claudeclient.domain.usecase.SetSystemPromptUseCase
import com.github.alphapaca.claudeclient.domain.usecase.SetTemperatureUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val getSystemPromptUseCase: GetSystemPromptUseCase,
    private val setSystemPromptUseCase: SetSystemPromptUseCase,
    private val getTemperatureUseCase: GetTemperatureUseCase,
    private val setTemperatureUseCase: SetTemperatureUseCase,
    private val getModelUseCase: GetModelUseCase,
    private val setModelUseCase: SetModelUseCase,
    private val getMaxTokensUseCase: GetMaxTokensUseCase,
    private val setMaxTokensUseCase: SetMaxTokensUseCase,
) : ViewModel() {

    private val _systemPrompt = MutableStateFlow("")
    val systemPrompt: StateFlow<String> = _systemPrompt

    private val _temperature = MutableStateFlow("")
    val temperature: StateFlow<String> = _temperature

    private val _maxTokens = MutableStateFlow("")
    val maxTokens: StateFlow<String> = _maxTokens

    private val _selectedModel = MutableStateFlow(LLMModel.DEFAULT)
    val selectedModel: StateFlow<LLMModel> = _selectedModel

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _systemPrompt.value = getSystemPromptUseCase()
            _temperature.value = getTemperatureUseCase()?.toString().orEmpty()
            _maxTokens.value = getMaxTokensUseCase().toString()
            _selectedModel.value = getModelUseCase()
        }
    }

    fun onSystemPromptChange(newPrompt: String) {
        _systemPrompt.value = newPrompt
    }

    fun onTemperatureChange(newTemperature: String) {
        _temperature.value = newTemperature
    }

    fun onMaxTokensChange(newMaxTokens: String) {
        _maxTokens.value = newMaxTokens
    }

    fun onModelChange(model: LLMModel) {
        _selectedModel.value = model
    }

    fun saveSettings() {
        viewModelScope.launch {
            setSystemPromptUseCase(_systemPrompt.value)
            setTemperatureUseCase(_temperature.value.toDoubleOrNull()?.coerceIn(0.0, 1.0))
            setMaxTokensUseCase(_maxTokens.value.toIntOrNull()?.coerceIn(1, 8192) ?: 1024)
            setModelUseCase(_selectedModel.value)
            _temperature.value = getTemperatureUseCase()?.toString().orEmpty()
            _maxTokens.value = getMaxTokensUseCase().toString()
        }
    }
}
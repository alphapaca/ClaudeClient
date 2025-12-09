package com.github.alphapaca.claudeclient.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.alphapaca.claudeclient.domain.usecase.GetSystemPromptUseCase
import com.github.alphapaca.claudeclient.domain.usecase.GetTemperatureUseCase
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
) : ViewModel() {

    private val _systemPrompt = MutableStateFlow("")
    val systemPrompt: StateFlow<String> = _systemPrompt

    private val _temperature = MutableStateFlow<Double?>(null)
    val temperature: StateFlow<Double?> = _temperature

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _systemPrompt.value = getSystemPromptUseCase()
            _temperature.value = getTemperatureUseCase()
        }
    }

    fun onSystemPromptChange(newPrompt: String) {
        _systemPrompt.value = newPrompt
    }

    fun onTemperatureChange(newTemperature: String) {
        _temperature.value = newTemperature.toDoubleOrNull()
    }

    fun saveSystemPrompt() {
        viewModelScope.launch {
            _temperature.value = _temperature.value?.coerceIn(0.0, 1.0)
            setSystemPromptUseCase(_systemPrompt.value)
            setTemperatureUseCase(_temperature.value)
        }
    }
}
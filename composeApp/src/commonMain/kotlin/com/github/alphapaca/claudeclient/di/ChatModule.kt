package com.github.alphapaca.claudeclient.di

import com.github.alphapaca.claudeclient.data.api.ClaudeApiClientFactory
import com.github.alphapaca.claudeclient.data.mapper.ConversationApiMapper
import com.github.alphapaca.claudeclient.data.repository.ConversationRepository
import com.github.alphapaca.claudeclient.data.repository.SettingsRepository
import com.github.alphapaca.claudeclient.domain.usecase.GetABikeUseCase
import com.github.alphapaca.claudeclient.domain.usecase.GetConversationUseCase
import com.github.alphapaca.claudeclient.domain.usecase.GetSystemPromptUseCase
import com.github.alphapaca.claudeclient.domain.usecase.GetTemperatureUseCase
import com.github.alphapaca.claudeclient.domain.usecase.GetWeatherUseCase
import com.github.alphapaca.claudeclient.domain.usecase.SendMessageUseCase
import com.github.alphapaca.claudeclient.domain.usecase.SetSystemPromptUseCase
import com.github.alphapaca.claudeclient.domain.usecase.SetTemperatureUseCase
import com.github.alphapaca.claudeclient.presentation.chat.ChatViewModel
import com.github.alphapaca.claudeclient.presentation.settings.SettingsViewModel
import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    factory<Json> {
        Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        }
    }
    factory { ConversationApiMapper(get()) }
    single<HttpClient> { ClaudeApiClientFactory.create(get()) }
    single<ConversationRepository> { ConversationRepository(get(), get()) }
    factory { GetWeatherUseCase(get()) }
    factory { SendMessageUseCase(get(), get()) }
    factory { GetConversationUseCase(get()) }
    factory { GetABikeUseCase(get()) }
    factory { SettingsRepository(get()) }
    factory { SetSystemPromptUseCase(get()) }
    factory { GetSystemPromptUseCase(get()) }
    factory { SetTemperatureUseCase(get()) }
    factory { GetTemperatureUseCase(get()) }
    viewModel<ChatViewModel> { ChatViewModel(get(), get(), get(), get(), get()) }
    viewModel<SettingsViewModel> { SettingsViewModel(get(), get(), get(), get()) }
}

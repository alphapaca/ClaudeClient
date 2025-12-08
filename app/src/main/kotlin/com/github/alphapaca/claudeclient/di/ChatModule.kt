package com.github.alphapaca.claudeclient.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.github.alphapaca.claudeclient.data.api.ClaudeApiClientFactory
import com.github.alphapaca.claudeclient.data.mapper.ConversationApiMapper
import com.github.alphapaca.claudeclient.data.repository.ConversationRepository
import com.github.alphapaca.claudeclient.data.repository.SettingsRepository
import com.github.alphapaca.claudeclient.domain.usecase.GetABikeUseCase
import com.github.alphapaca.claudeclient.domain.usecase.GetConversationUseCase
import com.github.alphapaca.claudeclient.domain.usecase.GetSystemPromptUseCase
import com.github.alphapaca.claudeclient.domain.usecase.GetWeatherUseCase
import com.github.alphapaca.claudeclient.domain.usecase.SendMessageUseCase
import com.github.alphapaca.claudeclient.domain.usecase.SetSystemPromptUseCase
import com.github.alphapaca.claudeclient.presentation.chat.ChatViewModel
import com.github.alphapaca.claudeclient.presentation.settings.SettingsViewModel
import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

val chatModule = module {
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
    factory { SettingsRepository(get<Context>().dataStore) }
    factory { SetSystemPromptUseCase(get()) }
    factory { GetSystemPromptUseCase(get()) }
    viewModel<ChatViewModel> { ChatViewModel(get(), get(), get(), get()) }
    viewModel<SettingsViewModel> { SettingsViewModel(get(), get()) }
}

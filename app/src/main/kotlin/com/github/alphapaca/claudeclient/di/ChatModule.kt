package com.github.alphapaca.claudeclient.di

import com.github.alphapaca.claudeclient.data.api.ClaudeApiClientFactory
import com.github.alphapaca.claudeclient.data.mapper.ConversationApiMapper
import com.github.alphapaca.claudeclient.data.repository.ConversationRepository
import com.github.alphapaca.claudeclient.domain.usecase.GetABikeUseCase
import com.github.alphapaca.claudeclient.domain.usecase.GetConversationUseCase
import com.github.alphapaca.claudeclient.domain.usecase.GetWeatherUseCase
import com.github.alphapaca.claudeclient.domain.usecase.SendMessageUseCase
import com.github.alphapaca.claudeclient.presentation.chat.ChatViewModel
import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

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
    factory { SendMessageUseCase(get()) }
    factory { GetConversationUseCase(get()) }
    factory { GetABikeUseCase(get()) }
    viewModel<ChatViewModel> { ChatViewModel(get(), get(), get(), get()) }
}

package com.github.alphapaca.claudeclient.di

import com.github.alphapaca.claudeclient.data.api.ClaudeApiClientFactory
import com.github.alphapaca.claudeclient.data.repository.ClaudeRepository
import com.github.alphapaca.claudeclient.presentation.chat.ChatViewModel
import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val claudeModule = module {
    factory<Json> {
        Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        }
    }
    single<HttpClient> { ClaudeApiClientFactory.create(get()) }
    factory<ClaudeRepository> { ClaudeRepository(get()) }
    viewModel<ChatViewModel> { ChatViewModel(get(), get()) }
}

package com.github.alphapaca.claudeclient.di

import com.github.alphapaca.claudeclient.data.api.ClaudeApiClientFactory
import com.github.alphapaca.claudeclient.data.repository.ClaudeRepository
import com.github.alphapaca.claudeclient.presentation.chat.ChatViewModel
import io.ktor.client.HttpClient
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val claudeModule = module {
    single<HttpClient> { ClaudeApiClientFactory.create() }
    factory<ClaudeRepository> { ClaudeRepository(get()) }
    viewModel<ChatViewModel> { ChatViewModel(get()) }
}

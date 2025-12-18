package com.github.alphapaca.claudeclient.di

import com.github.alphapaca.claudeclient.data.api.ClaudeApiClientFactory
import com.github.alphapaca.claudeclient.data.api.DeepSeekApiClientFactory
import com.github.alphapaca.claudeclient.data.local.ConversationLocalDataSource
import com.github.alphapaca.claudeclient.data.mcp.MCPClientManager
import com.github.alphapaca.claudeclient.data.mcp.createMCPClientManager
import com.github.alphapaca.claudeclient.data.notification.NotificationService
import com.github.alphapaca.claudeclient.data.notification.createNotificationService
import com.github.alphapaca.claudeclient.data.parser.ContentBlockParser
import com.github.alphapaca.claudeclient.data.repository.ConversationRepository
import com.github.alphapaca.claudeclient.data.repository.SettingsRepository
import com.github.alphapaca.claudeclient.data.service.ClaudeService
import com.github.alphapaca.claudeclient.data.service.DeepSeekService
import com.github.alphapaca.claudeclient.data.service.LLMService
import com.github.alphapaca.claudeclient.domain.usecase.AutoConnectMCPServerUseCase
import com.github.alphapaca.claudeclient.domain.usecase.CallMCPToolUseCase
import com.github.alphapaca.claudeclient.domain.usecase.ClearConversationUseCase
import com.github.alphapaca.claudeclient.domain.usecase.CompactConversationUseCase
import com.github.alphapaca.claudeclient.domain.usecase.ConnectMCPServerUseCase
import com.github.alphapaca.claudeclient.domain.usecase.DeleteConversationUseCase
import com.github.alphapaca.claudeclient.domain.usecase.DisconnectMCPServerUseCase
import com.github.alphapaca.claudeclient.domain.usecase.GetABikeUseCase
import com.github.alphapaca.claudeclient.domain.usecase.GetAllConversationsUseCase
import com.github.alphapaca.claudeclient.domain.usecase.GetConversationUseCase
import com.github.alphapaca.claudeclient.domain.usecase.GetMCPConnectionStateUseCase
import com.github.alphapaca.claudeclient.domain.usecase.GetMCPToolsUseCase
import com.github.alphapaca.claudeclient.domain.usecase.GetMaxTokensUseCase
import com.github.alphapaca.claudeclient.domain.usecase.GetMcpServerCommandUseCase
import com.github.alphapaca.claudeclient.domain.usecase.GetModelUseCase
import com.github.alphapaca.claudeclient.domain.usecase.GetMostRecentConversationIdUseCase
import com.github.alphapaca.claudeclient.domain.usecase.GetSystemPromptUseCase
import com.github.alphapaca.claudeclient.domain.usecase.GetTemperatureFlowUseCase
import com.github.alphapaca.claudeclient.domain.usecase.GetTemperatureUseCase
import com.github.alphapaca.claudeclient.domain.usecase.GetWeatherUseCase
import com.github.alphapaca.claudeclient.domain.usecase.ObserveReminderNotificationsUseCase
import com.github.alphapaca.claudeclient.domain.usecase.ResetUnreadCountUseCase
import com.github.alphapaca.claudeclient.domain.usecase.SendMessageUseCase
import com.github.alphapaca.claudeclient.domain.usecase.SetMaxTokensUseCase
import com.github.alphapaca.claudeclient.domain.usecase.SetMcpServerCommandUseCase
import com.github.alphapaca.claudeclient.domain.usecase.SetModelUseCase
import com.github.alphapaca.claudeclient.domain.usecase.SetSystemPromptUseCase
import com.github.alphapaca.claudeclient.domain.usecase.SetTemperatureUseCase
import com.github.alphapaca.claudeclient.presentation.chat.ChatViewModel
import com.github.alphapaca.claudeclient.presentation.settings.SettingsViewModel
import kotlinx.serialization.json.Json
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

val appModule = module {
    factory<Json> {
        Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        }
    }

    // HTTP Clients
    single(named("claude")) { ClaudeApiClientFactory.create(get()) }
    single(named("deepseek")) { DeepSeekApiClientFactory.create(get()) }

    // MCP Client
    single<MCPClientManager> { createMCPClientManager() }

    // Notification Service
    single<NotificationService> { createNotificationService() }

    // LLM Services
    single<LLMService>(named("claude")) { ClaudeService(get(named("claude")), get(), get()) }
    single<LLMService>(named("deepseek")) { DeepSeekService(get(named("deepseek")), get()) }
    single<List<LLMService>> { listOf(get(named("claude")), get(named("deepseek"))) }

    // Parsers
    factory { ContentBlockParser(get()) }

    // Local Data Sources
    single { ConversationLocalDataSource(get(), get()) }

    // Repositories
    single<ConversationRepository> { ConversationRepository(get(), get(), get()) }
    single { SettingsRepository(get()) }

    // Use Cases
    factory { GetWeatherUseCase(get()) }
    factory { SendMessageUseCase(get(), get(), get()) }
    factory { GetConversationUseCase(get()) }
    factory { ClearConversationUseCase(get()) }
    factory { CompactConversationUseCase(get(), get()) }
    factory { GetABikeUseCase(get()) }
    factory { SetSystemPromptUseCase(get()) }
    factory { GetSystemPromptUseCase(get()) }
    factory { SetTemperatureUseCase(get()) }
    factory { GetTemperatureUseCase(get()) }
    factory { GetTemperatureFlowUseCase(get()) }
    factory { GetModelUseCase(get()) }
    factory { SetModelUseCase(get()) }
    factory { GetMaxTokensUseCase(get()) }
    factory { SetMaxTokensUseCase(get()) }
    factory { GetAllConversationsUseCase(get()) }
    factory { DeleteConversationUseCase(get()) }
    factory { GetMostRecentConversationIdUseCase(get()) }
    factory { GetMCPToolsUseCase(get()) }
    factory { CallMCPToolUseCase(get()) }
    factory { ConnectMCPServerUseCase(get()) }
    factory { DisconnectMCPServerUseCase(get()) }
    factory { GetMcpServerCommandUseCase(get()) }
    factory { SetMcpServerCommandUseCase(get()) }
    factory { GetMCPConnectionStateUseCase(get()) }
    factory { AutoConnectMCPServerUseCase(get(), get()) }
    factory { ResetUnreadCountUseCase(get()) }
    factory { ObserveReminderNotificationsUseCase(get(), get()) }

    // ViewModels
    viewModel<ChatViewModel> { ChatViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel<SettingsViewModel> {
        SettingsViewModel(
            get(), get(), get(), get(), get(), get(), get(), get(), get(), get(),
            get(), get(), get(), get()
        )
    }
}

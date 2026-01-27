package com.github.alphapaca.claudeclient.di

import com.github.alphapaca.claudeclient.data.indexer.VoyageAIService
import com.github.alphapaca.claudeclient.data.local.CodeSessionLocalDataSource
import com.github.alphapaca.claudeclient.data.repository.CodeSessionRepository
import com.github.alphapaca.claudeclient.data.service.CodeSessionService
import com.github.alphapaca.claudeclient.domain.usecase.IndexCodeSessionUseCase
import com.github.alphapaca.claudeclient.presentation.codesession.CodeSessionViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.io.File
import java.util.Properties

/**
 * Reads VOYAGEAI_API_KEY from local.properties or environment variable.
 */
private fun getVoyageAIApiKey(): String? {
    // First try environment variable
    System.getenv("VOYAGEAI_API_KEY")?.takeIf { it.isNotBlank() }?.let { return it }

    // Then try local.properties in project root
    try {
        // Look for local.properties in the current working directory or parent directories
        var dir = File(System.getProperty("user.dir"))
        repeat(5) {
            val localProperties = File(dir, "local.properties")
            if (localProperties.exists()) {
                val props = Properties().apply { load(localProperties.inputStream()) }
                props.getProperty("VOYAGEAI_API_KEY")?.takeIf { it.isNotBlank() }?.let { return it }
            }
            dir = dir.parentFile ?: return@repeat
        }
    } catch (e: Exception) {
        // Ignore file reading errors
    }

    return null
}

/**
 * DI module for code session feature (JVM only).
 */
val codeSessionModule = module {
    // VoyageAI Service - only register if API key is available
    // Koin's single<T?> doesn't handle null values well, so we conditionally register
    getVoyageAIApiKey()?.let { apiKey ->
        single { VoyageAIService(apiKey) }
    }

    // Code Session Service
    single { CodeSessionService(get(named("claude"))) }

    // Local Data Source
    single { CodeSessionLocalDataSource(get()) }

    // Repository - pass nullable VoyageAIService (will be null if not registered above)
    single { CodeSessionRepository(get(), getOrNull<VoyageAIService>(), get(), get()) }

    // Use Cases - pass nullable VoyageAIService
    factory { IndexCodeSessionUseCase(getOrNull<VoyageAIService>(), get()) }

    // ViewModel
    viewModel { CodeSessionViewModel(get(), get()) }
}

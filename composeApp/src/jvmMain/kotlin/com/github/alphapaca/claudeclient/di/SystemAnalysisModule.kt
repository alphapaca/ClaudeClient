package com.github.alphapaca.claudeclient.di

import com.github.alphapaca.claudeclient.data.local.SystemAnalysisLocalDataSource
import com.github.alphapaca.claudeclient.data.repository.SystemAnalysisRepository
import com.github.alphapaca.claudeclient.data.service.SystemAnalysisService
import com.github.alphapaca.claudeclient.presentation.systemanalysis.SystemAnalysisViewModel
import kotlinx.serialization.json.Json
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * DI module for system analysis feature (JVM only).
 * Uses local LLM (Ollama) for privacy-focused system data analysis.
 */
val systemAnalysisModule = module {
    // JSON for serialization
    single(named("systemAnalysisJson")) {
        Json {
            ignoreUnknownKeys = true
            prettyPrint = false
        }
    }

    // System Analysis Service - uses local Ollama
    single {
        SystemAnalysisService(
            client = get(named("ollama")),
            json = get(named("systemAnalysisJson")),
            settingsRepository = get(),
        )
    }

    // Local Data Source
    single { SystemAnalysisLocalDataSource(get()) }

    // Repository
    single {
        SystemAnalysisRepository(
            localDataSource = get(),
            systemAnalysisService = get(),
            mcpClientManager = get(),
            settingsRepository = get(),
        )
    }

    // ViewModel
    viewModel { SystemAnalysisViewModel(get()) }
}

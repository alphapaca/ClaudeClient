# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

ClaudeClient is a Kotlin Multiplatform chat application that integrates with the Anthropic Claude API. The app targets Android and Desktop (JVM) platforms using Compose Multiplatform for the UI.

## Build and Run Commands

### Android
```bash
# Build debug APK
./gradlew :composeApp:assembleDebug

# Install and run on connected device
./gradlew :composeApp:installDebug
```

### Desktop (JVM)
```bash
# Run desktop application
./gradlew :composeApp:run

# Create distributable packages (DMG, MSI, Deb)
./gradlew :composeApp:packageDistributionForCurrentOS
```

### Testing
```bash
# Run all tests
./gradlew test

# Run common tests only
./gradlew :composeApp:testDebugUnitTest
```

## API Key Configuration

The project requires an Anthropic API key configured in `local.properties`:
```
ANTHROPIC_API_KEY=your_key_here
```

This key is accessed via BuildConfig and injected into the Ktor HTTP client at runtime. Never commit `local.properties` to version control.

## Architecture

### Multiplatform Source Sets

- **commonMain**: Shared code for all platforms (UI, business logic, API layer)
- **androidMain**: Android-specific implementations (Activity, DataStore initialization)
- **jvmMain**: Desktop-specific implementations (Window setup, DataStore initialization)

Platform-specific code is minimal and limited to entry points and platform APIs (DataStore initialization).

### Clean Architecture Layers

The codebase follows a clean architecture pattern with clear separation of concerns:

1. **Presentation Layer** (`presentation/`): Compose UI screens, ViewModels using Compose navigation3
   - `ChatScreen` and `ChatViewModel`: Main chat interface
   - `SettingsScreen` and `SettingsViewModel`: Configuration UI for system prompts
   - Navigation uses `NavDisplay` with `ChatKey` and `SettingsKey`

2. **Domain Layer** (`domain/`): Use cases and domain models
   - Use cases are single-responsibility classes with `invoke()` operators
   - Domain models like `Conversation` and `ConversationItem` represent business entities
   - Example use cases: `SendMessageUseCase`, `GetWeatherUseCase`, `GetABikeUseCase`

3. **Data Layer** (`data/`): Repositories, API clients, mappers
   - `ConversationRepository`: Manages conversation state via `MutableStateFlow`, handles API calls
   - `SettingsRepository`: Persists user settings using AndroidX DataStore
   - `ClaudeApiClientFactory`: Creates configured Ktor HttpClient for Claude API
   - `ConversationApiMapper`: Converts between domain models and API DTOs

### Dependency Injection

The app uses Koin for dependency injection with two modules:
- `appModule` (in `di/ChatModule.kt`): Common dependencies including repositories, use cases, ViewModels
- Platform-specific modules: Provided by Android/Desktop entry points for DataStore initialization

ViewModels are created using Koin's `viewModel` DSL and injected into Composables via `koinViewModel()`.

### API Integration

The Claude API integration uses:
- **Ktor Client** with OkHttp engine for HTTP communication
- **Kotlinx Serialization** for JSON serialization/deserialization
- API version: `2023-06-01`
- Model: `claude-sonnet-4-5`
- Default max tokens: 1024

The `ConversationRepository` maintains conversation history in-memory and sends the full context with each request. Token usage (input/output) is tracked per conversation.

### State Management

- ViewModels expose `StateFlow` for UI state
- `ConversationRepository` maintains a single conversation flow that ViewModels observe
- DataStore is used for persistent settings (system prompt)
- Conversation history is not persisted across app restarts

### Tool Use Pattern

The domain layer includes use cases for external tools (`GetWeatherUseCase`, `GetABikeUseCase`) that are integrated into the conversation flow. These represent the tool-calling capability of Claude, though the actual implementation details should be verified in the use case files.

## Key Dependencies

- Compose Multiplatform 1.10.0-rc01
- Kotlin 2.2.21
- Ktor 3.3.3 (HTTP client)
- Koin 4.1.1 (DI)
- AndroidX Navigation3 1.0.0-alpha06
- AndroidX DataStore 1.2.0
- Kermit 2.0.8 (logging)

## Development Notes

- The project uses Gradle version catalogs (`libs.versions.toml`) for dependency management
- Hot reload is configured via `composeHotReload` plugin for faster development
- Target SDK: 36, Min SDK: 24 (Android)
- JVM target: 11
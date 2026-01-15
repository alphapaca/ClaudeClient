# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

ClaudeClient is a Kotlin Multiplatform chat application that integrates with multiple LLM APIs (Anthropic Claude, DeepSeek). The app targets Android and Desktop (JVM) platforms using Compose Multiplatform for the UI. The project also includes several standalone modules for MCP servers, embeddings, and code review.

## Build and Run Commands

### Desktop (JVM)
```bash
./gradlew :composeApp:run                          # Run desktop application
./gradlew :composeApp:packageDistributionForCurrentOS  # Create distributable
```

### Android
```bash
./gradlew :composeApp:assembleDebug    # Build debug APK
./gradlew :composeApp:installDebug     # Install on connected device
```

### Testing
```bash
./gradlew test                              # Run all tests
./gradlew :composeApp:testDebugUnitTest     # Run common tests only
```

### Standalone Modules
```bash
./gradlew :mcp-server:jar                          # Build MCP server JAR
./gradlew :recipe-book-mcp:jar                     # Build recipe book MCP JAR
./gradlew :embedding-indexer:runCodeIndexer        # Index Kotlin code
./gradlew :mcp-foundation-search:jar               # Build search MCP JAR
./gradlew :review-agent:jvmJar                     # Build review agent JAR
```

## API Key Configuration

Configure API keys in `local.properties` (never commit this file):
```
ANTHROPIC_API_KEY=your_key_here
DEEPSEEK_API_KEY=your_key_here
VOYAGE_API_KEY=your_key_here
```

## Architecture

### Project Modules

- **composeApp**: Main chat application (Android + Desktop)
- **mcp-server**: MCP server with Hacker News API and reminders
- **recipe-book-mcp**: MCP server for recipe management
- **embedding-indexer**: Vector embeddings indexer using VoyageAI
- **mcp-foundation-search**: MCP server for semantic code search
- **review-agent**: AI-powered PR code review agent

### Source Sets (composeApp)

- **commonMain**: Shared code (UI, business logic, API, database)
- **androidMain**: Android entry point, SQLite driver
- **jvmMain**: Desktop entry point, SQLite driver, code sessions

### Clean Architecture Layers

```
presentation/          # Compose UI, ViewModels
  chat/               # ChatScreen, ChatViewModel
  codesession/        # CodeSessionScreen, CodeSessionViewModel (JVM only)
  settings/           # SettingsScreen, SettingsViewModel

domain/               # Business logic
  model/              # Conversation, ConversationItem, ConversationInfo, LLMModel, CodeSession
  usecase/            # Single-responsibility use cases with invoke() operators

data/
  api/                # HTTP clients, request/response DTOs
    claude/           # Claude API models
    deepseek/         # DeepSeek API models
  local/              # ConversationLocalDataSource, CodeSessionLocalDataSource (SQLDelight)
  mcp/                # MCPClientManager, MCPTool (MCP integration)
  repository/         # ConversationRepository, SettingsRepository, CodeSessionRepository
  service/            # LLMService interface, ClaudeService, DeepSeekService, CodeSessionService
  parser/             # ContentBlockParser (JSON widgets in responses)
```

### Database (SQLDelight)

Conversations are persisted locally using SQLDelight with async coroutines API:
- Schema: `commonMain/sqldelight/com/github/alphapaca/claudeclient/data/db/Conversation.sq`
- Tables: `Conversation` (id, name, timestamps), `Message` (content, tokens, model, etc.), `CodeSession`, `CodeSessionMessage`
- All database operations use `suspend` functions with `withContext(Dispatchers.IO)`
- Flows for reactive queries: `asFlow().mapToList(Dispatchers.IO)`

**Key pattern**: Conversations are created lazily - only when the first message is sent, not when "New conversation" is clicked. This ensures no empty conversations exist in the database.

### State Management

- `ChatViewModel` holds `currentConversationId: StateFlow<Long>` (or `NEW_CONVERSATION_ID = -1L` for unsaved)
- Uses `flatMapLatest` to reactively switch between conversations
- `ConversationRepository` is stateless - just provides data access methods
- Settings persisted via AndroidX DataStore

### LLM Services

Multiple LLM providers supported via `LLMService` interface:
- `ClaudeService`: Anthropic Claude API (Sonnet 4, Opus 4)
- `DeepSeekService`: DeepSeek API (V3.2)

Services log responses using Kermit (`Logger.d` for metadata, `Logger.v` for content).

### MCP Integration

The app supports Model Context Protocol (MCP) for tool use:
- `MCPClientManager`: Manages connections to MCP servers
- `MCPTool`: Represents a tool exposed by an MCP server
- `ClaudeService` handles automatic tool execution loops (max 10 iterations)
- Tool results are collected and passed back to Claude for processing

### Widget System

Assistant responses can contain JSON widgets that are parsed and rendered as custom UI:
- `ContentBlockParser` extracts JSON from response text
- Widget types: `WeatherData`, `BikeData`
- Widgets serialized back to JSON for persistence (`rawContent` property)

### Dependency Injection (Koin)

Modules in `di/`:
- `ChatModule.kt`: Chat repositories, use cases, ViewModels
- `CodeSessionModule.kt`: Code session components (JVM only)

Pattern:
- Repositories as `single`
- Use cases as `factory`
- ViewModels via `viewModel` DSL, injected with `koinViewModel()`

## Key Dependencies

- Compose Multiplatform 1.10.0-rc01
- Kotlin 2.2.21
- Ktor 3.3.3 (HTTP client)
- SQLDelight 2.2.1 (database)
- Koin 4.1.1 (DI)
- Kermit 2.0.8 (logging)
- AndroidX DataStore 1.2.0
- MCP Kotlin SDK 0.8.1 (Model Context Protocol)
- Kotlinx Serialization 1.9.0

## Development Notes

- Gradle version catalogs: `libs.versions.toml`
- Hot reload: `composeHotReload` plugin
- Target SDK: 36, Min SDK: 24 (Android)
- JVM target: 11
- Standalone modules produce fat JARs with all dependencies included

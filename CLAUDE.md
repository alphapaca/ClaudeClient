# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build the project
./gradlew build

# Assemble debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.github.alphapaca.claudeclient.ExampleTest"

# Run Android instrumented tests
./gradlew connectedAndroidTest

# Clean build
./gradlew clean
```

## Configuration

Add your Anthropic API key to `local.properties`:
```properties
ANTHROPIC_API_KEY=sk-ant-api03-...
```

## Architecture

This is an Android Claude API chat client built with Clean Architecture principles:

**Layers:**
- `presentation/` - UI layer with Jetpack Compose screens and ViewModels
- `domain/usecase/` - Business logic encapsulated in use cases (invoked via `operator fun invoke()`)
- `data/` - API clients, repositories, and mappers

**Key Components:**

- **ConversationRepository** - Single source of truth for chat conversation, holds in-memory state via `MutableStateFlow<List<ConversationItem>>`
- **ConversationApiMapper** - Parses Claude responses, extracting JSON widgets from markdown code blocks
- **ConversationItem** - Sealed interface representing chat items: `Text`, `Widget` (Weather/Bike data), or `Composed` (mixed content)

**Widget System:**
The app supports structured data widgets that Claude returns as JSON within code blocks. Widgets are defined as `@Serializable` sealed subtypes of `ConversationItem.Widget` with a `@SerialName("type")` discriminator. The mapper extracts these from responses and renders them as rich UI cards.

**DI:** Koin modules defined in `di/ChatModule.kt`, initialized in `App.kt`

**Networking:** Ktor client with OkHttp engine, configured in `ClaudeApiClientFactory.kt`. Uses `claude-sonnet-4-5` model.

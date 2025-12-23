## ClaudeClient

Простейший клиент для Anthropic API.

Для работы нужно поместить свой `API_KEY` в `local.properties`:
```properties
ANTHROPIC_API_KEY=sk-ant-api03-...
DEEPSEEK_API_KEY=sk-...
```

Для сборки вместе с mcp серверами которые будут по дефолту использоваться в app:
```shell
./gradlew :composeApp:run
```

Для запуска `embedding-indexer` нужно поместить VoyageAI api key в `local.properties`:
```properties
VOYAGEAI_API_KEY=pa-...
```

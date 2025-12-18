## ClaudeClient

Простейший клиент для Anthropic API.

Для работы нужно поместить свой `API_KEY` в `local.properties`:
```properties
ANTHROPIC_API_KEY=sk-ant-api03-...
DEEPSEEK_API_KEY=sk-...
```

Для сборки вместе с mcp сервером который будет по дефолту использоваться в app:
```shell
./gradlew :composeApp:run
```
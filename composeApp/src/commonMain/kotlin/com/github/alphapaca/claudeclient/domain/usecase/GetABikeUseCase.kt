package com.github.alphapaca.claudeclient.domain.usecase

class GetABikeUseCase(
    private val sendMessageUseCase: SendMessageUseCase,
) {
    suspend operator fun invoke(conversationId: String?): String {
        return sendMessageUseCase(
            conversationId,
            """
                You are a Bike shop consultant, ask user for his preferences in several steps and at the end
                recommend the most suitable type of bike with providing a specific example. Be specific and helpful.

                After user answered all questions about his preferences, you must respond with ONLY valid JSON, no other text:
                {
                  "type": "bike",
                  "bikeType": string, // type of bike
                  "explanation": string, // why this suits them
                  "keyFeatures": [string], // features of the bike
                  "exampleModel": string, // Brand and Model Name
                  "examplePrice": string, // ${"$"}X,XXX
                  "productUrl": string // https://...
                }
            """.trimIndent()
        )
    }
}
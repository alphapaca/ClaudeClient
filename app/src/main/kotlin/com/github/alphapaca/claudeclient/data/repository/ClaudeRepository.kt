package com.github.alphapaca.claudeclient.data.repository

import com.github.alphapaca.claudeclient.data.api.MessageRequest
import com.github.alphapaca.claudeclient.data.api.MessageResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody

class ClaudeRepository(
    private val client: HttpClient,
) {

    suspend fun sendMessage(request: MessageRequest): Result<MessageResponse> {
        return try {
            val response: MessageResponse = client.post("v1/messages") {
                setBody(request)
            }.body()
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
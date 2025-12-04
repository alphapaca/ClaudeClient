package com.github.alphapaca.claudeclient.utils

import kotlinx.coroutines.CancellationException

fun <T> runCatchingSuspend(block: () -> T): Result<T> {
    return try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
package com.chotobela.core.common

sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>
    data class Error(val error: AppError) : AppResult<Nothing>

    fun getOrNull(): T? = (this as? Success)?.value

    companion object {
        inline fun <T> of(block: () -> T): AppResult<T> = try {
            Success(block())
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            Error(AppError.Unexpected(t.message ?: "Unknown error", t))
        }
    }
}

sealed interface AppError {
    data class Network(val message: String) : AppError
    data class Storage(val message: String) : AppError
    data class Engine(val message: String, val code: Int) : AppError
    data class Auth(val message: String) : AppError
    data class Download(val message: String) : AppError
    data class Unexpected(val message: String, val cause: Throwable? = null) : AppError
}

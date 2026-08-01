package com.example.cnv.core.common

/**
 * Lightweight success / failure wrapper for Core and features.
 */
sealed class Result<out T> {
    data class Success<T>(val value: T) : Result<T>()
    data class Failure(val message: String, val cause: Throwable? = null) : Result<Nothing>()

    val isSuccess: Boolean get() = this is Success
}

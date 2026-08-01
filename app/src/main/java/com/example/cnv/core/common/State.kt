package com.example.cnv.core.common

/**
 * Generic feature state for ViewModels / repositories.
 */
sealed class State<out T> {
    data object Idle : State<Nothing>()
    data object Loading : State<Nothing>()
    data class Ready<T>(val data: T) : State<T>()
    data class Error(val message: String) : State<Nothing>()
}

package com.dlab.sirinium.data.repository

// Sealed класс для представления состояний сетевого запроса
sealed class NetworkResult<out T> {
    data class Success<out T>(val data: T, val isStale: Boolean = false) : NetworkResult<T>()
    data class Error(val message: String, val code: Int? = null) : NetworkResult<Nothing>()
    object Loading : NetworkResult<Nothing>()
}

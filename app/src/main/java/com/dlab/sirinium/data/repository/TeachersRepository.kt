package com.dlab.sirinium.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.dlab.sirinium.data.remote.ApiService
import com.dlab.sirinium.data.model.TeacherInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class TeachersRepository(
    private val apiService: ApiService,
    private val context: Context
) {
    
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            else -> false
        }
    }

    fun getTeachers(): Flow<NetworkResult<List<TeacherInfo>>> = flow {
        Log.i("TeachersRepository", "getTeachers ENTRY")
        
        emit(NetworkResult.Loading)
        
        if (!isNetworkAvailable()) {
            Log.w("TeachersRepository", "Network not available")
            emit(NetworkResult.Error("Нет подключения к интернету", 0))
            return@flow
        }
        
        try {
            Log.i("TeachersRepository", "Attempting to fetch teachers from network...")
            val response = apiService.getTeachers()
            
            if (response.isSuccessful) {
                val teachersMap = response.body() ?: emptyMap()
                val teachersList = teachersMap.map { (id, name) ->
                    TeacherInfo(id = id, name = name)
                }
                
                Log.i("TeachersRepository", "Network success. Fetched ${teachersList.size} teachers")
                emit(NetworkResult.Success(teachersList))
            } else {
                Log.e("TeachersRepository", "Network error: ${response.code()} - ${response.message()}")
                emit(NetworkResult.Error("Ошибка загрузки данных: ${response.code()}", response.code()))
            }
        } catch (e: Exception) {
            Log.e("TeachersRepository", "Exception during network call: ${e.message}", e)
            emit(NetworkResult.Error("Ошибка сети: ${e.message}", 0))
        }
    }.catch { e ->
        Log.e("TeachersRepository", "Flow error: ${e.message}", e)
        emit(NetworkResult.Error("Неожиданная ошибка: ${e.message}", 0))
    }.flowOn(Dispatchers.IO)
}



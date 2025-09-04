package com.dlab.sirinium.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.dlab.sirinium.data.remote.ApiService
import com.dlab.sirinium.data.model.GroupInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class GroupsRepository(
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

    fun getGroups(): Flow<NetworkResult<List<GroupInfo>>> = flow {
        Log.i("GroupsRepository", "getGroups ENTRY")
        
        emit(NetworkResult.Loading)
        
        if (!isNetworkAvailable()) {
            Log.w("GroupsRepository", "Network not available")
            emit(NetworkResult.Error("Нет подключения к интернету", 0))
            return@flow
        }
        
        try {
            Log.i("GroupsRepository", "Attempting to fetch groups from network...")
            val response = apiService.getGroups()
            
            if (response.isSuccessful) {
                val groupsList = response.body()?.map { groupName ->
                    GroupInfo(name = groupName)
                } ?: emptyList()
                
                Log.i("GroupsRepository", "Network success. Fetched ${groupsList.size} groups")
                emit(NetworkResult.Success(groupsList))
            } else {
                Log.e("GroupsRepository", "Network error: ${response.code()} - ${response.message()}")
                emit(NetworkResult.Error("Ошибка загрузки данных: ${response.code()}", response.code()))
            }
        } catch (e: Exception) {
            Log.e("GroupsRepository", "Exception during network call: ${e.message}", e)
            emit(NetworkResult.Error("Ошибка сети: ${e.message}", 0))
        }
    }.catch { e ->
        Log.e("GroupsRepository", "Flow error: ${e.message}", e)
        emit(NetworkResult.Error("Неожиданная ошибка: ${e.message}", 0))
    }.flowOn(Dispatchers.IO)
}



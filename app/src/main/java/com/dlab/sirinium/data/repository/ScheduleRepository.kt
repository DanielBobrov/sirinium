package com.dlab.sirinium.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.dlab.sirinium.data.local.ScheduleDao
import com.dlab.sirinium.data.model.ScheduleItem
import com.dlab.sirinium.data.remote.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch // Import the catch operator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class ScheduleRepository(
    private val apiService: ApiService,
    private val scheduleDao: ScheduleDao,
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

    fun getSchedule(
        group: String,
        week: Int,
        forceNetwork: Boolean = false
    ): Flow<NetworkResult<List<ScheduleItem>>> =
        flow { // Start of the main flow builder
            val weekIdentifier = "${group}_offset${week}"
            Log.i(
                "ScheduleRepository",
                "getSchedule ENTRY for weekIdentifier: $weekIdentifier, forceNetwork: $forceNetwork, isNetworkAvailable: ${isNetworkAvailable()}"
            )

            emit(NetworkResult.Loading)

            var cachedItems: List<ScheduleItem> = emptyList() // Declare here to be accessible in catch
            try {
                cachedItems = scheduleDao.getScheduleForWeek(weekIdentifier).first()
            } catch (e: Exception) {
                Log.e(
                    "ScheduleRepository",
                    "Error reading cache for $weekIdentifier: ${e.message}",
                    e
                )
                // Do not emit here, let the main catch operator handle errors
                // If cache read fails, cachedItems remains emptyList(), which is fine
            }
            Log.d(
                "ScheduleRepository",
                "Cache check for $weekIdentifier: ${cachedItems.size} items found."
            )

            val shouldFetchFromNetwork = isNetworkAvailable() && (forceNetwork || cachedItems.isEmpty() || weekOffsetRequiresRefresh(
                week
            ))
            Log.d(
                "ScheduleRepository",
                "Decision to fetch from network for $weekIdentifier: $shouldFetchFromNetwork (force: $forceNetwork, cacheEmpty: ${cachedItems.isEmpty()}, weekRefresh: ${
                    weekOffsetRequiresRefresh(
                        week
                    )
                })"
            )

            if (shouldFetchFromNetwork) {
                Log.i("ScheduleRepository", "Attempting to fetch from network for $weekIdentifier...")
                // Network call is now directly in the flow builder,
                // so exceptions will be caught by the .catch operator
                val response = apiService.getSchedule(group, week) // This might throw an exception

                if (response.isSuccessful) {
                    val networkItems = response.body() ?: emptyList()
                    Log.i(
                        "ScheduleRepository",
                        "Network success for $weekIdentifier. Fetched ${networkItems.size} items. Code: ${response.code()}"
                    )

                    val itemsToCache = networkItems.map { item ->
                        val hasStart = item.startTime.isNotBlank()
                        val hasEnd = item.endTime.isNotBlank()

                        val (computedStart, computedEnd) = if (!hasStart || !hasEnd) {
                            computeStartEndTimesByPair(item.numberPair)
                        } else {
                            item.startTime to item.endTime
                        }

                        item.copy(
                            weekIdentifier = weekIdentifier,
                            teacherDetails = item.teachers?.entries?.firstOrNull()?.value,
                            startTime = computedStart,
                            endTime = computedEnd
                        )
                    }

                    if (itemsToCache.isNotEmpty()) {
                        scheduleDao.clearAndInsertForWeek(weekIdentifier, itemsToCache)
                        Log.i(
                            "ScheduleRepository",
                            "Successfully cached ${itemsToCache.size} items for $weekIdentifier."
                        )
                    } else if (networkItems.isEmpty() && response.isSuccessful) {
                        scheduleDao.clearAndInsertForWeek(weekIdentifier, emptyList())
                        Log.i(
                            "ScheduleRepository",
                            "Network success with 0 items for $weekIdentifier. Cache cleared for this week."
                        )
                    }
                    emit(NetworkResult.Success(itemsToCache, isStale = false))
                } else {
                    // Handle API errors (non-2xx responses)
                    val errorBody = response.errorBody()?.string() ?: "No error body"
                    Log.e(
                        "ScheduleRepository",
                        "API Error for $weekIdentifier: Code ${response.code()} - ${response.message()}. ErrorBody: $errorBody"
                    )
                    // If API error occurs, but we have cache, emit cached data as stale
                    // Otherwise, emit an API error. The .catch operator below will not be triggered for this specific case.
                    if (cachedItems.isNotEmpty()) {
                        Log.w(
                            "ScheduleRepository",
                            "API error, but serving ${cachedItems.size} items from cache for $weekIdentifier."
                        )
                        emit(NetworkResult.Success(processCachedItems(cachedItems), isStale = true))
                    } else {
                        emit(
                            NetworkResult.Error(
                                "Ошибка API: ${response.code()} - ${response.message()}",
                                response.code()
                            )
                        )
                    }
                }
            } else { // Not fetching from network
                if (cachedItems.isNotEmpty()) {
                    Log.i(
                        "ScheduleRepository",
                        "Network unavailable or no refresh needed. Serving ${cachedItems.size} items from cache for $weekIdentifier."
                    )
                    emit(
                        NetworkResult.Success(
                            processCachedItems(cachedItems),
                            isStale = !isNetworkAvailable()
                        )
                    )
                } else {
                    Log.w(
                        "ScheduleRepository",
                        "No network and no cache for $weekIdentifier. Emitting error."
                    )
                    emit(
                        NetworkResult.Error(
                            if (!isNetworkAvailable()) "Нет подключения к сети и нет данных в кеше" else "Нет данных в кеше"
                        )
                    )
                }
            }
        }.catch { e -> // This catches exceptions from the flow builder above (e.g., network issues, db issues not caught internally)
            Log.e(
                "ScheduleRepository",
                "Network/Flow Exception during getSchedule: ${e.javaClass.simpleName} - ${e.message}",
                e
            )
            // Attempt to read cache again within the catch block as a last resort,
            // or use the 'cachedItems' variable if it was populated before the exception.
            // For simplicity here, we'll try to re-fetch or use what we might have.
            // A more robust way would be to ensure cachedItems is accurately reflecting state before exception.
            // For this example, let's assume if an exception occurs during network fetch, we check cache.
            // The existing `cachedItems` variable might already hold this if DB read was successful earlier.
            val weekIdentifier = "${group}_offset${week}" // Re-declare for context if needed, or pass
            var fallbackCache: List<ScheduleItem> = emptyList()
            try {
                // Try to read cache again in case it's a network error and cache is still desired.
                // This specific re-read might be redundant if `cachedItems` from the flow block is reliable.
                fallbackCache = scheduleDao.getScheduleForWeek(weekIdentifier).first()
            } catch (dbException: Exception) {
                Log.e("ScheduleRepository", "Error reading cache within CATCH block for $weekIdentifier: ${dbException.message}", dbException)
            }

            if (fallbackCache.isNotEmpty()) {
                Log.w(
                    "ScheduleRepository",
                    "Exception occurred, serving ${fallbackCache.size} items from cache for $weekIdentifier as fallback."
                )
                emit(NetworkResult.Success(processCachedItems(fallbackCache), isStale = true))
            } else {
                emit(NetworkResult.Error("Сетевая ошибка или ошибка данных: ${e.localizedMessage ?: "Неизвестная ошибка"}"))
            }
        }.flowOn(Dispatchers.IO) // Apply flowOn to the entire chain

    /**
     * Получает расписание для конкретного преподавателя
     */
    fun getTeacherSchedule(
        teacherId: String,
        week: Int,
        forceNetwork: Boolean = false
    ): Flow<NetworkResult<List<ScheduleItem>>> =
        flow {
            val weekIdentifier = "teacher_${teacherId}_offset${week}"
            Log.i(
                "ScheduleRepository",
                "getTeacherSchedule ENTRY for weekIdentifier: $weekIdentifier, forceNetwork: $forceNetwork, isNetworkAvailable: ${isNetworkAvailable()}"
            )

            emit(NetworkResult.Loading)

            var cachedItems: List<ScheduleItem> = emptyList()
            try {
                cachedItems = scheduleDao.getScheduleForWeek(weekIdentifier).first()
            } catch (e: Exception) {
                Log.e(
                    "ScheduleRepository",
                    "Error reading cache for $weekIdentifier: ${e.message}",
                    e
                )
            }

            val shouldFetchFromNetwork = isNetworkAvailable() && (forceNetwork || cachedItems.isEmpty() || weekOffsetRequiresRefresh(week))
            
            if (shouldFetchFromNetwork) {
                Log.i("ScheduleRepository", "Attempting to fetch teacher schedule from network for $weekIdentifier...")
                val response = apiService.getTeacherSchedule(teacherId, week)

                if (response.isSuccessful) {
                    val networkItems = response.body() ?: emptyList()
                    Log.i(
                        "ScheduleRepository",
                        "Network success for teacher $weekIdentifier. Fetched ${networkItems.size} items."
                    )

                    val itemsToCache = networkItems.map { item ->
                        val hasStart = item.startTime.isNotBlank()
                        val hasEnd = item.endTime.isNotBlank()

                        val (computedStart, computedEnd) = if (!hasStart || !hasEnd) {
                            computeStartEndTimesByPair(item.numberPair)
                        } else {
                            item.startTime to item.endTime
                        }

                        item.copy(
                            weekIdentifier = weekIdentifier,
                            teacherDetails = item.teachers?.entries?.firstOrNull()?.value,
                            startTime = computedStart,
                            endTime = computedEnd
                        )
                    }

                    if (itemsToCache.isNotEmpty()) {
                        scheduleDao.clearAndInsertForWeek(weekIdentifier, itemsToCache)
                        Log.i(
                            "ScheduleRepository",
                            "Successfully cached ${itemsToCache.size} items for teacher $weekIdentifier."
                        )
                    } else if (networkItems.isEmpty() && response.isSuccessful) {
                        scheduleDao.clearAndInsertForWeek(weekIdentifier, emptyList())
                        Log.i(
                            "ScheduleRepository",
                            "Network success with 0 items for teacher $weekIdentifier. Cache cleared for this week."
                        )
                    }
                    emit(NetworkResult.Success(itemsToCache, isStale = false))
                } else {
                    val errorBody = response.errorBody()?.string() ?: "No error body"
                    Log.e(
                        "ScheduleRepository",
                        "API Error for teacher $weekIdentifier: Code ${response.code()} - ${response.message()}. ErrorBody: $errorBody"
                    )
                    
                    if (cachedItems.isNotEmpty()) {
                        Log.w(
                            "ScheduleRepository",
                            "API error, but serving ${cachedItems.size} items from cache for teacher $weekIdentifier."
                        )
                        emit(NetworkResult.Success(processCachedItems(cachedItems), isStale = true))
                    } else {
                        emit(
                            NetworkResult.Error(
                                "Ошибка API: ${response.code()} - ${response.message()}",
                                response.code()
                            )
                        )
                    }
                }
            } else {
                if (cachedItems.isNotEmpty()) {
                    Log.i(
                        "ScheduleRepository",
                        "Network unavailable or no refresh needed. Serving ${cachedItems.size} items from cache for teacher $weekIdentifier."
                    )
                    emit(
                        NetworkResult.Success(
                            processCachedItems(cachedItems),
                            isStale = !isNetworkAvailable()
                        )
                    )
                } else {
                    Log.w(
                        "ScheduleRepository",
                        "No network and no cache for teacher $weekIdentifier. Emitting error."
                    )
                    emit(
                        NetworkResult.Error(
                            if (!isNetworkAvailable()) "Нет подключения к сети и нет данных в кеше" else "Нет данных в кеше"
                        )
                    )
                }
            }
        }.catch { e ->
            Log.e(
                "ScheduleRepository",
                "Network/Flow Exception during getTeacherSchedule: ${e.javaClass.simpleName} - ${e.message}",
                e
            )
            
            val weekIdentifier = "teacher_${teacherId}_offset${week}"
            var fallbackCache: List<ScheduleItem> = emptyList()
            try {
                fallbackCache = scheduleDao.getScheduleForWeek(weekIdentifier).first()
            } catch (dbException: Exception) {
                Log.e("ScheduleRepository", "Error reading cache within CATCH block for teacher $weekIdentifier: ${dbException.message}", dbException)
            }

            if (fallbackCache.isNotEmpty()) {
                Log.w(
                    "ScheduleRepository",
                    "Exception occurred, serving ${fallbackCache.size} items from cache for teacher $weekIdentifier as fallback."
                )
                emit(NetworkResult.Success(processCachedItems(fallbackCache), isStale = true))
            } else {
                emit(NetworkResult.Error("Сетевая ошибка или ошибка данных: ${e.localizedMessage ?: "Неизвестная ошибка"}"))
            }
        }.flowOn(Dispatchers.IO)


    private fun weekOffsetRequiresRefresh(week: Int): Boolean {
        val shouldRefresh = week == 0 || week == 1
        Log.d("ScheduleRepo", "weekOffsetRequiresRefresh for week $week: $shouldRefresh")
        return shouldRefresh
    }

    private fun processCachedItems(items: List<ScheduleItem>): List<ScheduleItem> {
        return items.map { item ->
            if (item.teacherDetails == null && item.teachers?.isNotEmpty() == true) {
                item.copy(teacherDetails = item.teachers.entries.firstOrNull()?.value)
            } else {
                item
            }
        }
    }

    // Новая схема времени дня: начало 08:45, пара 1:20, перерыв 0:15, 8 пар в день
    private fun computeStartEndTimesByPair(numberPair: Int): Pair<String, String> {
        val dayStart = LocalTime.of(8, 45)
        val lessonMinutes = 80 // 1:20
        val breakMinutes = 15

        // Пара нумеруется с 1. Смещение: (pair-1) * (пара + перерыв)
        val totalOffsetMinutes = (numberPair - 1).coerceAtLeast(0) * (lessonMinutes + breakMinutes)
        val startTime = dayStart.plusMinutes(totalOffsetMinutes.toLong())
        val endTime = startTime.plusMinutes(lessonMinutes.toLong())

        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        return formatter.format(startTime) to formatter.format(endTime)
    }
}
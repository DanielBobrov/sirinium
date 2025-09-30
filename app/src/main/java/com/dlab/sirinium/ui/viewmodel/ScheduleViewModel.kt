package com.dlab.sirinium.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dlab.sirinium.data.local.AppDatabase
import com.dlab.sirinium.data.model.ScheduleItem
import com.dlab.sirinium.data.remote.RetrofitClient
import com.dlab.sirinium.data.repository.NetworkResult
import com.dlab.sirinium.data.repository.ScheduleRepository
import kotlinx.coroutines.channels.Channel // kotlinx.coroutines.channels.Channel нужен
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

// DailySchedule data class
data class DailySchedule(
    val date: LocalDate,
    val dayOfWeekFullName: String,
    val items: List<ScheduleItem>
)

// Событие для UI, как обсуждали ранее
sealed class ViewModelEvent {
    data class ShowUserMessage(val message: String) : ViewModelEvent()
    object SuggestAppRestart : ViewModelEvent()
}

class ScheduleViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val scheduleDao = AppDatabase.getDatabase(application).scheduleDao()
    private val repository: ScheduleRepository =
        ScheduleRepository(RetrofitClient.instance, scheduleDao, application)

    private val _scheduleState = MutableStateFlow<NetworkResult<List<DailySchedule>>>(NetworkResult.Loading)
    val scheduleState: StateFlow<NetworkResult<List<DailySchedule>>> = _scheduleState.asStateFlow()

    private val _currentGroup = MutableStateFlow<String?>(null)
    val currentGroup: StateFlow<String?> = _currentGroup.asStateFlow()

    private val _currentWeekOffset = MutableStateFlow(0)
    val currentWeekOffset: StateFlow<Int> = _currentWeekOffset.asStateFlow()

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _isOfflineDataDisplayed = MutableStateFlow(false)
    val isOfflineDataDisplayed: StateFlow<Boolean> = _isOfflineDataDisplayed.asStateFlow()

    // Channel для отправки одноразовых событий в UI
    private val _viewModelEvents = Channel<ViewModelEvent>(Channel.BUFFERED)
    val viewModelEvents: Flow<ViewModelEvent> = _viewModelEvents.receiveAsFlow()

    private val apiDateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale("ru"))

    private val connectivityManager =
        application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    // Флаги для логики "предложения о перезапуске"
    private var wasOffline = !isNetworkOnlineInternal() // Инициализируем на основе текущего состояния сети
    private var restartSuggestionShown = false

    private val networkAvailabilityFlow: Flow<Boolean> = callbackFlow {
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d("ScheduleVM_Network", "Network available")
                trySend(true)
            }

            override fun onLost(network: Network) {
                Log.d("ScheduleVM_Network", "Network lost")
                trySend(false)
            }

            override fun onUnavailable() {
                Log.d("ScheduleVM_Network", "Network unavailable on start check")
                trySend(false)
            }
        }

        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val initiallyAvailable = isNetworkOnlineInternal() // Используем внутренний метод для консистентности
        Log.d("ScheduleVM_Network", "Initial network check: $initiallyAvailable")
        trySend(initiallyAvailable)
        wasOffline = !initiallyAvailable // Обновляем wasOffline после первой проверки

        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)

        awaitClose {
            Log.d("ScheduleVM_Network", "Unregistering network callback")
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }
    }.distinctUntilChanged()


    init {
        Log.d("ScheduleVM_Lifecycle", "ViewModel initialized. wasOffline initially: $wasOffline")
        _currentWeekOffset.value = calculateWeekOffsetForDate(LocalDate.now())
        Log.d("ScheduleVM_Init", "init: _selectedDate=${_selectedDate.value}, _currentWeekOffset=${_currentWeekOffset.value}")

        viewModelScope.launch {
            networkAvailabilityFlow.collect { isNetworkNowAvailable ->
                Log.i("ScheduleVM_Network", "Network status changed: Available = $isNetworkNowAvailable. WasOffline: $wasOffline, RestartSuggestionShown: $restartSuggestionShown. Current selectedDate: ${_selectedDate.value}")
                val currentScheduleResult = _scheduleState.value
                val group = _currentGroup.value

                if (isNetworkNowAvailable) {
                    if (wasOffline && !restartSuggestionShown) {
                        Log.i("ScheduleVM_Network", "Network re-established. Suggesting app restart.")
                        _viewModelEvents.trySend(ViewModelEvent.SuggestAppRestart)
                        restartSuggestionShown = true
                        // Не пытаемся автоматически обновить данные здесь, так как просим перезапуск
                    } else if (restartSuggestionShown) {
                        Log.d("ScheduleVM_Network", "Network is available, but restart suggestion was already shown. No automatic action.")
                    } else if (!wasOffline && group != null) { // Сеть была и осталась, не было предложения о перезапуске
                        val needsRefreshDueToStaleData = currentScheduleResult is NetworkResult.Success && currentScheduleResult.isStale
                        val needsRefreshDueToError = currentScheduleResult is NetworkResult.Error
                        if (needsRefreshDueToStaleData || needsRefreshDueToError) {
                            Log.i("ScheduleVM_Network", "Network was already available. Data is stale or error. Forcing refresh for group: $group, date: ${_selectedDate.value}")
                            // Используем новый параметр triggeredByNetworkChange
                            onDateSelected(_selectedDate.value, forceNetworkFetch = true, triggeredByNetworkChange = true)
                        }
                    }
                    wasOffline = false
                } else { // Сеть пропала
                    Log.i("ScheduleVM_Network", "Network became unavailable.")
                    wasOffline = true
                    if (currentScheduleResult is NetworkResult.Success) {
                        // Используем внутренний метод isNetworkOnlineInternal() для проверки
                        val newOfflineState = currentScheduleResult.isStale
                        if (_isOfflineDataDisplayed.value != newOfflineState) {
                            _isOfflineDataDisplayed.value = newOfflineState
                            Log.d("ScheduleVM_Network", "Network lost. Updated _isOfflineDataDisplayed to: $newOfflineState")
                        }
                    }
                }
            }
        }
    }

    // Переименовал ваш isNetworkOnline в isNetworkOnlineInternal, чтобы избежать путаницы с параметром в collect
    private fun isNetworkOnlineInternal(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) // Добавил проверку NET_CAPABILITY_VALIDATED
    }

    fun setCurrentGroup(group: String?) {
        Log.d("ScheduleVM_Group", "setCurrentGroup called with group: '$group'")
        Log.d("ScheduleVM_Group", "Current group: '${_currentGroup.value}', current state: ${_scheduleState.value::class.java.simpleName}")
        
        if (_currentGroup.value != group) { // Если группа действительно меняется
            restartSuggestionShown = false // Сбрасываем флаг для новой группы
            Log.d("ScheduleVM_Group", "Group will change from '${_currentGroup.value}' to '$group'. RestartSuggestionShown reset.")
        }

        if (group.isNullOrBlank()) {
            _currentGroup.value = null
            _scheduleState.value = NetworkResult.Error("Номер группы не указан.")
            _selectedDate.value = LocalDate.now()
            _currentWeekOffset.value = calculateWeekOffsetForDate(LocalDate.now())
            _isOfflineDataDisplayed.value = false
            Log.d("ScheduleVM_Group", "setCurrentGroup: Group cleared. Resetting state.")
            return
        }

        val previousGroup = _currentGroup.value
        val currentState = _scheduleState.value
        val shouldFetchDueToGroupChangeOrBadState = previousGroup != group ||
                currentState !is NetworkResult.Success ||
                (currentState is NetworkResult.Success && currentState.data.isEmpty() && group == previousGroup)

        Log.d("ScheduleVM_Group", "shouldFetchDueToGroupChangeOrBadState: $shouldFetchDueToGroupChangeOrBadState")
        Log.d("ScheduleVM_Group", "  - previousGroup != group: ${previousGroup != group}")
        Log.d("ScheduleVM_Group", "  - currentState !is NetworkResult.Success: ${currentState !is NetworkResult.Success}")
        Log.d("ScheduleVM_Group", "  - currentState is Success but empty: ${currentState is NetworkResult.Success && currentState.data.isEmpty() && group == previousGroup}")

        if (shouldFetchDueToGroupChangeOrBadState) {
            _currentGroup.value = group
            val today = LocalDate.now()
            Log.d("ScheduleVM_Group", "setCurrentGroup: Group changed to '$group' or initial/empty. Fetching for today ($today) with force.")
            onDateSelected(today, forceNetworkFetch = true) // triggeredByNetworkChange здесь false по умолчанию
        } else {
            _currentGroup.value = group
            Log.d("ScheduleVM_Group", "setCurrentGroup: Group '$group' is same and data likely exists. No forced fetch by setCurrentGroup itself.")
            if (currentState is NetworkResult.Success) {
                _isOfflineDataDisplayed.value = currentState.isStale
            }
        }
    }

    // Добавляем параметр triggeredByNetworkChange с значением по умолчанию
    private fun fetchScheduleDataForCurrentGroup(
        weekOffsetToLoad: Int,
        forceNetwork: Boolean,
        triggeredByNetworkChange: Boolean = false // <-- Новый параметр
    ) {
        Log.d("ScheduleVM_Fetch", "fetchScheduleDataForCurrentGroup called with weekOffsetToLoad: $weekOffsetToLoad, forceNetwork: $forceNetwork, triggeredByNetworkChange: $triggeredByNetworkChange")
        
        // Если было предложено перезапустить приложение и это авто-загрузка из-за сети
        if (restartSuggestionShown && triggeredByNetworkChange) {
            Log.i("ScheduleVM_Fetch", "Skipping fetch for week $weekOffsetToLoad because app restart was suggested and this was triggered by network change.")
            // Можно отправить сообщение еще раз, если это нужно, но обычно одного раза достаточно
            // _viewModelEvents.trySend(ViewModelEvent.ShowUserMessage("Пожалуйста, перезапустите приложение для обновления данных."))
            return
        }

        val groupToLoad = _currentGroup.value
        if (groupToLoad.isNullOrBlank()) {
            _scheduleState.value = NetworkResult.Error("Номер группы не установлен для загрузки.")
            Log.w("ScheduleVM_Fetch", "fetchScheduleDataForCurrentGroup: Group is null or blank.")
            return
        }
        Log.i("ScheduleVM_Fetch", "FETCHING schedule for group: $groupToLoad, week offset: $weekOffsetToLoad. ForceNetwork: $forceNetwork, TriggeredByNetworkChange: $triggeredByNetworkChange. Current _selectedDate: ${_selectedDate.value}")

        viewModelScope.launch {
            // Определяем, является ли текущий выбор группой или преподавателем
            val isTeacher = !(groupToLoad.startsWith("К") || groupToLoad.startsWith("И"))
            val scheduleFlow = if (isTeacher) {
                Log.d("ScheduleVM_Fetch", "Fetching teacher schedule for ID: $groupToLoad")
                repository.getTeacherSchedule(groupToLoad, weekOffsetToLoad, forceNetwork)
            } else {
                Log.d("ScheduleVM_Fetch", "Fetching group schedule for group: $groupToLoad")
                repository.getSchedule(groupToLoad, weekOffsetToLoad, forceNetwork)
            }
            
            scheduleFlow
                .onStart {
                    Log.d("ScheduleVM_Fetch", "Loading schedule for $groupToLoad, week $weekOffsetToLoad")
                    val showLoading = _scheduleState.value !is NetworkResult.Success ||
                            (_scheduleState.value as? NetworkResult.Success)?.data.isNullOrEmpty() ||
                            forceNetwork
                    if (showLoading) {
                        _scheduleState.value = NetworkResult.Loading
                    }
                }
                .catch { e ->
                    Log.e("ScheduleVM_Fetch", "Exception for $groupToLoad, week $weekOffsetToLoad: ${e.message}", e)
                    val currentData = (_scheduleState.value as? NetworkResult.Success<List<DailySchedule>>)?.data
                    if (currentData != null && currentData.isNotEmpty()) {
                        _scheduleState.value = NetworkResult.Success(currentData, true)
                        _isOfflineDataDisplayed.value = true
                        _viewModelEvents.trySend(ViewModelEvent.ShowUserMessage("Ошибка обновления: ${e.localizedMessage}. Показаны сохраненные данные."))
                    } else {
                        _scheduleState.value = NetworkResult.Error("Ошибка при загрузке: ${e.localizedMessage}")
                        _isOfflineDataDisplayed.value = false
                    }
                }
                .collect { result ->
                    Log.d("ScheduleVM_Fetch", "Collected result for $groupToLoad, week $weekOffsetToLoad: ${result::class.java.simpleName}")
                    when (result) {
                        is NetworkResult.Success -> {
                            val groupedData = groupScheduleByDate(result.data)
                            _scheduleState.value = NetworkResult.Success(groupedData, result.isStale)
                            _isOfflineDataDisplayed.value = result.isStale
                            Log.i("ScheduleVM_Fetch", "Success for $groupToLoad, week $weekOffsetToLoad. Stale: ${result.isStale}, Offline Display: ${_isOfflineDataDisplayed.value}. Grouped data size: ${groupedData.size}.")

                            if (!result.isStale && (forceNetwork || triggeredByNetworkChange) ) { // Если данные успешно обновлены и это был форсированный запрос
                                if (restartSuggestionShown) {
                                    Log.d("ScheduleVM_Fetch", "Data successfully refreshed after forced/network-triggered action. Resetting restartSuggestionShown.")
                                    restartSuggestionShown = false // Сбрасываем флаг, так как данные актуальны
                                    _viewModelEvents.trySend(ViewModelEvent.ShowUserMessage("Данные успешно обновлены!"))
                                }
                            } else if (result.isStale && (forceNetwork || triggeredByNetworkChange) && isNetworkOnlineInternal() && !restartSuggestionShown) {
                                _viewModelEvents.trySend(ViewModelEvent.ShowUserMessage("Не удалось получить свежие данные. Показаны последние доступные."))
                            }
                        }
                        is NetworkResult.Error -> {
                            val currentData = (_scheduleState.value as? NetworkResult.Success<List<DailySchedule>>)?.data
                            if (currentData != null && currentData.isNotEmpty()) {
                                _scheduleState.value = NetworkResult.Success(currentData, true)
                                _isOfflineDataDisplayed.value = true
                                if (forceNetwork || triggeredByNetworkChange) _viewModelEvents.trySend(ViewModelEvent.ShowUserMessage("Ошибка: ${result.message}. Показаны сохраненные данные."))
                            } else {
                                _scheduleState.value = result
                                _isOfflineDataDisplayed.value = false
                            }
                            Log.e("ScheduleVM_Fetch", "Error state for $groupToLoad, week $weekOffsetToLoad: ${result.message}")
                        }
                        is NetworkResult.Loading -> {
                            if (_scheduleState.value !is NetworkResult.Success) {
                                _scheduleState.value = NetworkResult.Loading
                            }
                            Log.d("ScheduleVM_Fetch", "Received Loading state from repository for $groupToLoad, week $weekOffsetToLoad (in collect)")
                        }
                    }
                }
        }
    }

    private fun groupScheduleByDate(items: List<ScheduleItem>): List<DailySchedule> {
        // Ваш код groupScheduleByDate выглядит нормально. Оставляем его.
        // Убедитесь, что items.isNullOrEmpty() корректно обрабатывает nullable, если это возможно.
        // В вашем коде items не nullable, так что items.isEmpty() достаточно.
        if (items.isEmpty()) return emptyList()
        return items
            .groupBy { item ->
                try {
                    LocalDate.parse(item.date, apiDateFormatter)
                } catch (e: Exception) {
                    Log.e("ScheduleVM_Parser", "Error parsing date: ${item.date} for item ${item.discipline ?: "N/A"} - ${e.message}")
                    null
                }
            }
            .filterKeys { it != null }
            .mapNotNull { (date, scheduleItems) -> // date здесь уже не null
                DailySchedule(
                    date = date!!,
                    dayOfWeekFullName = date.dayOfWeek.getDisplayName(TextStyle.FULL_STANDALONE, Locale("ru"))
                        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("ru")) else it.toString() },
                    items = scheduleItems.sortedBy { it.startTime }
                )
            }
            .sortedBy { it.date }
    }

    fun onRefresh() {
        val today = LocalDate.now()
        Log.d("ScheduleVM_UserAction", "onRefresh called. Forcing network fetch for today: $today")
        _selectedDate.value = today
        // При ручном обновлении triggeredByNetworkChange = false
        onDateSelected(today, forceNetworkFetch = true, triggeredByNetworkChange = false)
    }

    fun onNextDay() {
        val newDate = _selectedDate.value.plusDays(1)
        Log.d("ScheduleVM_UserAction", "onNextDay called, newDate will be: $newDate")
        onDateSelected(newDate) // triggeredByNetworkChange = false по умолчанию
    }

    fun onPreviousDay() {
        val newDate = _selectedDate.value.minusDays(1)
        Log.d("ScheduleVM_UserAction", "onPreviousDay called, newDate will be: $newDate")
        onDateSelected(newDate) // triggeredByNetworkChange = false по умолчанию
    }

    private fun calculateWeekOffsetForDate(date: LocalDate): Int {
        // Ваш код calculateWeekOffsetForDate выглядит нормально.
        val today = LocalDate.now()
        val startOfTodayCalendarWeek = today.with(DayOfWeek.MONDAY)
        val startOfTargetCalendarWeek = date.with(DayOfWeek.MONDAY)
        val weeksBetween = ChronoUnit.WEEKS.between(startOfTodayCalendarWeek, startOfTargetCalendarWeek)
        return weeksBetween.toInt()
    }

    // Добавляем параметр triggeredByNetworkChange с значением по умолчанию
    fun onDateSelected(
        date: LocalDate,
        forceNetworkFetch: Boolean = false,
        triggeredByNetworkChange: Boolean = false // <-- Новый параметр
    ) {
        Log.d("ScheduleVM_DateSelect", "onDateSelected called with date: $date, forceNetworkFetch: $forceNetworkFetch, triggeredByNetworkChange: $triggeredByNetworkChange")
        
        // Если было предложено перезапустить приложение и это авто-вызов из-за сети
        if (restartSuggestionShown && triggeredByNetworkChange) {
            Log.i("ScheduleVM_DateSelect", "Skipping onDateSelected logic for $date because app restart was suggested and this was triggered by network change.")
            return
        }

        Log.i("ScheduleVM_DateSelect", "onDateSelected called with date: $date. Force: $forceNetworkFetch, TriggeredByNetworkChange: $triggeredByNetworkChange. Current _selectedDate: ${_selectedDate.value}, current _currentWeekOffset: ${_currentWeekOffset.value}")

        val previousSelectedDate = _selectedDate.value
        _selectedDate.value = date

        val targetWeekOffset = calculateWeekOffsetForDate(date)
        Log.d("ScheduleVM_DateSelect", "Calculated targetWeekOffset for $date is: $targetWeekOffset.")

        val currentState = _scheduleState.value
        var needsFetch = false
        var reasonForFetch = ""

        if (_currentGroup.value.isNullOrBlank()) {
            Log.w("ScheduleVM_DateSelect", "onDateSelected: No group selected. Aborting fetch.")
            _scheduleState.value = NetworkResult.Error("Для загрузки расписания выберите группу.")
            return
        }

        if (forceNetworkFetch) {
            needsFetch = true
            reasonForFetch = "Force network fetch requested."
        } else {
            when (currentState) {
                is NetworkResult.Success -> {
                    val dateFoundInCurrentData = currentState.data.any { dailySchedule -> dailySchedule.date == date }
                    if (targetWeekOffset != _currentWeekOffset.value) {
                        needsFetch = true
                        reasonForFetch = "Target week offset changed ($targetWeekOffset from ${_currentWeekOffset.value})."
                    } else if (!dateFoundInCurrentData) {
                        needsFetch = true
                        reasonForFetch = "Selected date $date NOT FOUND in current data for week offset ${_currentWeekOffset.value}. Attempting fetch."
                    } else {
                        _isOfflineDataDisplayed.value = currentState.isStale
                        reasonForFetch = "Selected date $date found in current data for current week. isOfflineDisplay: ${_isOfflineDataDisplayed.value}"
                    }
                }
                is NetworkResult.Error -> {
                    needsFetch = true
                    reasonForFetch = "Current state is Error. Attempting to reload."
                }
                is NetworkResult.Loading -> {
                    if (targetWeekOffset != _currentWeekOffset.value) {
                        needsFetch = true
                        reasonForFetch = "Target week offset ($targetWeekOffset) changed while current week (${_currentWeekOffset.value}) is Loading."
                    } else {
                        reasonForFetch = "Already loading for target week ${_currentWeekOffset.value}."
                    }
                }
            }
        }

        if (needsFetch) {
            // Дополнительная проверка перед вызовом fetch, если это авто-событие и перезапуск предложен
            if (restartSuggestionShown && triggeredByNetworkChange) {
                Log.i("ScheduleVM_DateSelect", "Preventing fetch operation in onDateSelected for $date due to pending restart suggestion and network-triggered event.")
            } else {
                Log.i("ScheduleVM_DateSelect", "NEEDS FETCH for $date. Target offset: $targetWeekOffset. Reason: $reasonForFetch")
                val oldWeekOffset = _currentWeekOffset.value
                _currentWeekOffset.value = targetWeekOffset

                val actualForceForRepo = forceNetworkFetch || (targetWeekOffset != oldWeekOffset) || (currentState is NetworkResult.Error)
                // Передаем triggeredByNetworkChange в fetchScheduleDataForCurrentGroup
                fetchScheduleDataForCurrentGroup(targetWeekOffset, actualForceForRepo, triggeredByNetworkChange)
            }
        } else {
            Log.i("ScheduleVM_DateSelect", "NO FETCH NEEDED for $date. Reason: $reasonForFetch")
            if (currentState is NetworkResult.Success) {
                _isOfflineDataDisplayed.value = currentState.isStale
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d("ScheduleVM_Lifecycle", "ViewModel cleared. Network callback should be unregistered by awaitClose.")
    }
}
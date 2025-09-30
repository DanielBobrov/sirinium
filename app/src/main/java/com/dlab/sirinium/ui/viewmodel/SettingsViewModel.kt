package com.dlab.sirinium.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.annotation.StringRes // Необходим импорт
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.dlab.sirinium.R // Необходим импорт R вашего проекта
import com.dlab.sirinium.data.UserPreferencesRepository
import com.dlab.sirinium.workers.ScheduleWorker
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import java.net.URL
import org.json.JSONObject
import org.json.JSONArray
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

// Enum для выбора темы - ИЗМЕНЕН
enum class ThemeSetting(@StringRes val displayNameResId: Int) {
    LIGHT(R.string.theme_light),
    DARK(R.string.theme_dark),
    SYSTEM(R.string.theme_system)
}

private const val TAG_SETTINGS_VM = "SettingsViewModel"
private const val AUTO_UPDATE_WORK_NAME = "SiriniumScheduleAutoUpdateWork"

class SettingsViewModel(
    private val app: Application // Сделаем 'app' свойством для доступа к контексту
) : AndroidViewModel(app) { // Передаем app в родительский конструктор

    private val userPreferencesRepository = UserPreferencesRepository(app) // Используем app
    private val workManager = WorkManager.getInstance(app) // Используем app

    private val _groupSuffix = MutableStateFlow("")
    val groupSuffix: StateFlow<String> = _groupSuffix.asStateFlow()
    
    // Новые состояния для выбора группы и преподавателя
    private val _availableGroups = MutableStateFlow<List<String>>(emptyList())
    val availableGroups: StateFlow<List<String>> = _availableGroups.asStateFlow()
    
    private val _availableTeachers = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val availableTeachers: StateFlow<List<Pair<String, String>>> = _availableTeachers.asStateFlow()
    
    private val _isGroupSelectionDialogVisible = MutableStateFlow(false)
    val isGroupSelectionDialogVisible: StateFlow<Boolean> = _isGroupSelectionDialogVisible.asStateFlow()
    
    private val _isLoadingGroups = MutableStateFlow(false)
    val isLoadingGroups: StateFlow<Boolean> = _isLoadingGroups.asStateFlow()

    private val _groupSavedEvent = MutableStateFlow<String?>(null)
    val groupSavedEvent: StateFlow<String?> = _groupSavedEvent.asStateFlow()

    private val _themeSetting = MutableStateFlow(ThemeSetting.LIGHT)
    val themeSetting: StateFlow<ThemeSetting> = _themeSetting.asStateFlow()

    private val _autoUpdateEnabled = MutableStateFlow(true)
    val autoUpdateEnabled: StateFlow<Boolean> = _autoUpdateEnabled.asStateFlow()

    private val _autoUpdateIntervalMinutes = MutableStateFlow("30")
    val autoUpdateIntervalMinutes: StateFlow<String> = _autoUpdateIntervalMinutes.asStateFlow()

    private val _notificationsEnabled = MutableStateFlow(false)
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    private val _notificationLeadTimeMinutes = MutableStateFlow("15")
    val notificationLeadTimeMinutes: StateFlow<String> = _notificationLeadTimeMinutes.asStateFlow()

    // Используем appName и appVersion как и раньше, если они не должны браться из BuildConfig/strings.xml
    // Если должны, то нужно применить предыдущие рекомендации по BuildConfig.VERSION_NAME и app.getString(R.string.app_name)
    val appName: String = "Sirinium" // Если нужно из strings.xml: app.getString(R.string.app_name)
    val appVersion: String = "2.0.0"      // Если нужно из BuildConfig: BuildConfig.VERSION_NAME
    val developerName: String = "d.lab"

    init {
        Log.d(TAG_SETTINGS_VM, "Initializing and loading preferences...")
        // ... остальная часть init блока без изменений ...
        viewModelScope.launch {
            userPreferencesRepository.groupSuffixFlow.collect { suffix ->
                _groupSuffix.value = suffix
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.themeSettingFlow.collect { theme ->
                _themeSetting.value = theme
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.autoUpdateEnabledFlow.collect { enabled ->
                _autoUpdateEnabled.value = enabled
                if (enabled) {
                    val interval = _autoUpdateIntervalMinutes.value.toLongOrNull() ?: 30L
                    scheduleAutoUpdateWorker(interval)
                } else {
                    cancelAutoUpdateWorker()
                }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.autoUpdateIntervalFlow.collect { interval ->
                _autoUpdateIntervalMinutes.value = interval.toString()
                if (_autoUpdateEnabled.value) {
                    scheduleAutoUpdateWorker(interval.toLong())
                }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.notificationsEnabledFlow.collect { enabled ->
                _notificationsEnabled.value = enabled
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.notificationLeadTimeFlow.collect { leadTime ->
                _notificationLeadTimeMinutes.value = leadTime.toString()
            }
        }
        
        // Загружаем группы и преподавателей при инициализации
        Log.d(TAG_SETTINGS_VM, "Starting initial load of groups and teachers...")
        loadAvailableGroups()
    }

    /**
     * Возвращает список всех доступных тем с их локализованными отображаемыми именами.
     * Используется для построения UI выбора темы.
     */
    
         /**
      * API endpoints:
      * - Группы: https://eralas.ru/api/groups (возвращает JSON массив названий групп)
      * - Преподаватели: https://eralas.ru/api/teachers (возвращает JSON объект id:ФИО)
      * - Расписание группы: https://eralas.ru/api/schedule?group=К{group}&week={week}
      * - Расписание преподавателя: https://eralas.ru/api/teacherschedule?id={id}&week={week}
      * 
      * Примечание: ID преподавателей используются только внутренне для API запросов,
      * в UI отображаются только ФИО преподавателей.
      */
    fun getAvailableThemeOptions(): List<Pair<ThemeSetting, String>> {
        return ThemeSetting.values().map { theme ->
            theme to app.getString(theme.displayNameResId)
        }
    }

    // ... остальной код ViewModel без изменений ...
    fun onGroupSuffixChange(newSuffix: String) {
        val filteredSuffix = newSuffix.filter { it.isDigit() || it == '-' || it == '/' }
        _groupSuffix.value = filteredSuffix
    }
    
    fun showGroupSelectionDialog() {
        Log.d(TAG_SETTINGS_VM, "showGroupSelectionDialog called")
        _isGroupSelectionDialogVisible.value = true
        Log.d(TAG_SETTINGS_VM, "_isGroupSelectionDialogVisible set to true")
        // Всегда перезагружаем данные при открытии диалога
        Log.d(TAG_SETTINGS_VM, "Dialog opened, reloading data...")
        loadAvailableGroups()
    }
    
    fun hideGroupSelectionDialog() {
        Log.d(TAG_SETTINGS_VM, "hideGroupSelectionDialog called")
        _isGroupSelectionDialogVisible.value = false
        Log.d(TAG_SETTINGS_VM, "_isGroupSelectionDialogVisible set to false")
    }
    
    fun selectGroup(selection: String) {
        Log.d(TAG_SETTINGS_VM, "selectGroup called with selection: $selection")
        
        // Проверяем, является ли выбор группой или преподавателем
        // API требует префикс "К" для групп, поэтому сохраняем полное название группы
        if (selection.startsWith("К") || selection.startsWith("И")) {
            // Это группа - сохраняем полное название как есть
            _groupSuffix.value = selection
            Log.d(TAG_SETTINGS_VM, "Selected group: $selection, saving full group name")
        } else {
            // Это преподаватель - используем ID как есть
            _groupSuffix.value = selection
            Log.d(TAG_SETTINGS_VM, "Selected teacher with ID: $selection")
        }
        
        hideGroupSelectionDialog()
        Log.d(TAG_SETTINGS_VM, "Calling onSaveGroupClicked() to save and update schedule")
        onSaveGroupClicked()
    }
    
    private fun loadAvailableGroups() {
        viewModelScope.launch {
            try {
                _isLoadingGroups.value = true
                Log.d(TAG_SETTINGS_VM, "Starting to load groups and teachers from API...")
                
                // Проверяем разрешения на интернет
                if (ContextCompat.checkSelfPermission(app, Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG_SETTINGS_VM, "Internet permission not granted")
                }
                
                // Загружаем группы и преподавателей в IO диспетчере с retry
                val (groups, teachers) = withContext(Dispatchers.IO) {
                    fetchGroupsAndTeachersWithRetry(maxRetries = 3)
                }
                
                // Обновляем UI состояния в главном потоке
                _availableGroups.value = groups
                _availableTeachers.value = teachers
                
                Log.d(TAG_SETTINGS_VM, "Final result: ${groups.size} groups and ${teachers.size} teachers")
                
            } catch (e: Exception) {
                Log.e(TAG_SETTINGS_VM, "Error loading groups and teachers", e)
                Log.e(TAG_SETTINGS_VM, "Error details: ${e.message}")
                e.printStackTrace()
                
                // В случае ошибки загружаем статические данные
                val fallbackGroups = listOf(
                    "К20-1", "К20-2", "К20-3", "К21-1", "К21-2", "К21-3",
                    "К22-1", "К22-2", "К22-3", "К23-1", "К23-2", "К23-3"
                )
                _availableGroups.value = fallbackGroups
                _availableTeachers.value = emptyList()
                Log.d(TAG_SETTINGS_VM, "Using fallback data: ${fallbackGroups.size} groups")
            } finally {
                _isLoadingGroups.value = false
                Log.d(TAG_SETTINGS_VM, "Loading completed. Available groups: ${_availableGroups.value.size}, teachers: ${_availableTeachers.value.size}")
            }
        }
    }

    fun onSaveGroupClicked() {
        val suffixToSave = _groupSuffix.value
        Log.d(TAG_SETTINGS_VM, "onSaveGroupClicked called with suffix: $suffixToSave")
        
        if (suffixToSave.isBlank()) {
            Log.w(TAG_SETTINGS_VM, "Attempted to save a blank group suffix.")
            return
        }
        
        viewModelScope.launch {
            Log.d(TAG_SETTINGS_VM, "Saving group suffix to UserPreferencesRepository: $suffixToSave")
            userPreferencesRepository.saveGroupSuffix(suffixToSave)
            
            val fullGroupNumber = getFullGroupNumberForApi(suffixToSave)
            Log.d(TAG_SETTINGS_VM, "Emitting groupSavedEvent with full group number: $fullGroupNumber")
            _groupSavedEvent.emit(fullGroupNumber)
        }
    }

    fun consumeGroupSavedEvent() {
        viewModelScope.launch {
            _groupSavedEvent.emit(null)
        }
    }

    fun onThemeChange(newTheme: ThemeSetting) {
        viewModelScope.launch {
            userPreferencesRepository.saveThemeSetting(newTheme)
        }
    }

    fun onAutoUpdateEnabledChange(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.saveAutoUpdateEnabled(enabled)
            if (enabled) {
                val interval = _autoUpdateIntervalMinutes.value.toLongOrNull() ?: 30L
                scheduleAutoUpdateWorker(interval)
            } else {
                cancelAutoUpdateWorker()
            }
        }
    }

    fun onAutoUpdateIntervalChange(minutesString: String) {
        val newInterval = minutesString.toLongOrNull()
        if (newInterval != null && newInterval >= 15) {
            _autoUpdateIntervalMinutes.value = minutesString
            viewModelScope.launch {
                userPreferencesRepository.saveAutoUpdateInterval(newInterval.toInt())
                if (_autoUpdateEnabled.value) {
                    scheduleAutoUpdateWorker(newInterval)
                }
            }
        } else if (minutesString.isEmpty()){
            _autoUpdateIntervalMinutes.value = ""
            viewModelScope.launch {
                val defaultInterval = 30
                userPreferencesRepository.saveAutoUpdateInterval(defaultInterval)
                if (_autoUpdateEnabled.value) {
                    scheduleAutoUpdateWorker(defaultInterval.toLong())
                }
            }
        } else {
            Log.w(TAG_SETTINGS_VM, "Invalid autoUpdateInterval input (must be >= 15): $minutesString")
            val currentValidInterval = (_autoUpdateIntervalMinutes.value.toLongOrNull() ?: 30L).coerceAtLeast(15L)
            _autoUpdateIntervalMinutes.value = currentValidInterval.toString()
        }
    }

    fun onNotificationsEnabledChange(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.saveNotificationsEnabled(enabled)
        }
    }

    fun onNotificationLeadTimeChange(minutesString: String) {
        val newLeadTime = minutesString.toIntOrNull()
        if (newLeadTime != null && newLeadTime >= 0) {
            _notificationLeadTimeMinutes.value = minutesString
            viewModelScope.launch {
                userPreferencesRepository.saveNotificationLeadTime(newLeadTime)
            }
        } else if (minutesString.isEmpty()){
            _notificationLeadTimeMinutes.value = ""
            viewModelScope.launch {
                val defaultLeadTime = 15
                userPreferencesRepository.saveNotificationLeadTime(defaultLeadTime)
            }
        } else {
            Log.w(TAG_SETTINGS_VM, "Invalid notificationLeadTime input: $minutesString")
        }
    }

    fun updateThemeSetting(themeSetting: ThemeSetting) {
        viewModelScope.launch {
            userPreferencesRepository.saveThemeSetting(themeSetting)
        }
    }

    private fun scheduleAutoUpdateWorker(intervalMinutes: Long) {
        val safeInterval = intervalMinutes.coerceAtLeast(15L)
        Log.i(TAG_SETTINGS_VM, "Scheduling auto-update worker with interval: $safeInterval minutes.")
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicWorkRequest = PeriodicWorkRequestBuilder<ScheduleWorker>(
            safeInterval, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            AUTO_UPDATE_WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            periodicWorkRequest
        )
    }

    private fun cancelAutoUpdateWorker() {
        Log.i(TAG_SETTINGS_VM, "Cancelling auto-update worker.")
        workManager.cancelUniqueWork(AUTO_UPDATE_WORK_NAME)
    }

    fun getCurrentFullGroupNumberForApi(): String? {
        return if (_groupSuffix.value.isNotBlank()) {
            // Если суффикс уже начинается с "К", то это группа - возвращаем как есть
            // Если нет - то это преподаватель или группа без префикса, добавляем "К"
            if (_groupSuffix.value.startsWith("К") || _groupSuffix.value.startsWith("И")) {
                _groupSuffix.value
            } else {
                "К${_groupSuffix.value}"
            }
        } else {
            null
        }
    }

    private fun getFullGroupNumberForApi(suffix: String): String {
        // Если суффикс уже начинается с "К", то это группа - возвращаем как есть
        // Если нет - то это преподаватель или группа без префикса, добавляем "К"
        return if (suffix.startsWith("К") || suffix.startsWith("И")) {
            suffix
        } else {
            "К$suffix"
        }
    }

    /**
     * Загружает группы и преподавателей с механизмом повторных попыток
     */
    private suspend fun fetchGroupsAndTeachersWithRetry(maxRetries: Int): Pair<List<String>, List<Pair<String, String>>> {
        var lastException: Exception? = null
        
        repeat(maxRetries) { attempt ->
            try {
                Log.d(TAG_SETTINGS_VM, "Attempt ${attempt + 1}/$maxRetries to fetch groups and teachers")
                
                // Загружаем группы
                Log.d(TAG_SETTINGS_VM, "Loading groups from: https://eralas.ru/api/groups")
                val groupsUrl = URL("https://eralas.ru/api/groups")
                val connection = groupsUrl.openConnection()
                connection.connectTimeout = 30000 // 30 секунд таймаут
                connection.readTimeout = 30000
                
                Log.d(TAG_SETTINGS_VM, "Connecting to groups API...")
                val groupsResponse = connection.getInputStream().bufferedReader().use { it.readText() }
                Log.d(TAG_SETTINGS_VM, "Groups API response length: ${groupsResponse.length}")
                
                if (groupsResponse.isBlank()) {
                    throw Exception("Empty response from groups API")
                }
                
                val groupsArray = JSONArray(groupsResponse)
                Log.d(TAG_SETTINGS_VM, "Parsed groups JSON array with ${groupsArray.length()} items")
                val groups = mutableListOf<String>()
                groups.add("ИОП-ИТ-24/1")
                groups.add("ИОП-ИТ-24/2")
                groups.add("ИОП-ИТ-25/1")
                groups.add("ИОП-ИТ-25/2")
                
                for (i in 0 until groupsArray.length()) {
                    val group = groupsArray.getString(i)
                    groups.add(group)
                }

                for (i in groups) {
//                    val group = groupsArray.getString(i)
                    Log.d(TAG_SETTINGS_VM, "Successfully loaded group ${i}")
                }


                Log.d(TAG_SETTINGS_VM, "Successfully loaded ${groups.size} groups")
                
                // Загружаем преподавателей
                Log.d(TAG_SETTINGS_VM, "Loading teachers from: https://eralas.ru/api/teachers")
                val teachersUrl = URL("https://eralas.ru/api/teachers")
                val teachersConnection = teachersUrl.openConnection()
                teachersConnection.connectTimeout = 30000
                teachersConnection.readTimeout = 30000
                
                Log.d(TAG_SETTINGS_VM, "Connecting to teachers API...")
                val teachersResponse = teachersConnection.getInputStream().bufferedReader().use { it.readText() }
                Log.d(TAG_SETTINGS_VM, "Teachers API response length: ${teachersResponse.length}")
                
                if (teachersResponse.isBlank()) {
                    throw Exception("Empty response from teachers API")
                }
                
                val teachersObject = JSONObject(teachersResponse)
                Log.d(TAG_SETTINGS_VM, "Parsed teachers JSON object with ${teachersObject.length()} keys")
                val teachers = mutableListOf<Pair<String, String>>()
                
                val keys = teachersObject.keys()
                while (keys.hasNext()) {
                    val id = keys.next()
                    val name = teachersObject.getString(id)
                    teachers.add(id to name)
                }
                Log.d(TAG_SETTINGS_VM, "Successfully loaded ${teachers.size} teachers")
                
                Log.d(TAG_SETTINGS_VM, "Network operations completed successfully on attempt ${attempt + 1}")
                return groups to teachers
                
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG_SETTINGS_VM, "Attempt ${attempt + 1} failed: ${e.message}")
                
                if (attempt < maxRetries - 1) {
                    val delayMs = 1000L * (attempt + 1) // Экспоненциальная задержка: 1s, 2s, 3s
                    Log.d(TAG_SETTINGS_VM, "Waiting ${delayMs}ms before retry...")
                    kotlinx.coroutines.delay(delayMs)
                }
            }
        }
        
        // Все попытки исчерпаны
        Log.e(TAG_SETTINGS_VM, "All $maxRetries attempts failed. Last error: ${lastException?.message}")
        throw lastException ?: Exception("Failed to fetch groups and teachers after $maxRetries attempts")
    }
}
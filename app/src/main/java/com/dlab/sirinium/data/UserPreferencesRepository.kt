package com.dlab.sirinium.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dlab.sirinium.ui.viewmodel.ThemeSetting // ИСПРАВЛЕНО: Убедимся, что импорт правильный
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private const val USER_PREFERENCES_NAME = "user_settings"
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = USER_PREFERENCES_NAME)
private const val TAG = "UserPrefsRepository"

class UserPreferencesRepository(private val context: Context) {

    private object PreferencesKeys {
        val GROUP_SUFFIX = stringPreferencesKey("group_suffix")
        val THEME_SETTING = stringPreferencesKey("theme_setting")
        val AUTO_UPDATE_ENABLED = booleanPreferencesKey("auto_update_enabled")
        val AUTO_UPDATE_INTERVAL = intPreferencesKey("auto_update_interval")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val NOTIFICATION_LEAD_TIME = intPreferencesKey("notification_lead_time")
        val IS_FIRST_LAUNCH_COMPLETED = booleanPreferencesKey("is_first_launch_completed")
    }

    val isFirstLaunchCompletedFlow: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            Log.e(TAG, "Error reading is_first_launch_completed preferences.", exception)
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val completed = preferences[PreferencesKeys.IS_FIRST_LAUNCH_COMPLETED] ?: false
            Log.d(TAG, "Is first launch completed loaded from DataStore: $completed")
            completed
        }

    suspend fun setFirstLaunchCompleted() {
        try {
            context.dataStore.edit { preferences ->
                preferences[PreferencesKeys.IS_FIRST_LAUNCH_COMPLETED] = true
                Log.d(TAG, "First launch completed flag set to true in DataStore.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving first_launch_completed flag to DataStore.", e)
        }
    }


    val groupSuffixFlow: Flow<String> = context.dataStore.data
        .catch { exception ->
            Log.e(TAG, "Error reading group_suffix preferences.", exception)
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val suffix = preferences[PreferencesKeys.GROUP_SUFFIX] ?: ""
            Log.d(TAG, "Group suffix loaded from DataStore: '$suffix'")
            suffix
        }

    suspend fun saveGroupSuffix(groupSuffix: String) {
        try {
            context.dataStore.edit { preferences ->
                preferences[PreferencesKeys.GROUP_SUFFIX] = groupSuffix
                Log.d(TAG, "Group suffix saved to DataStore: '$groupSuffix'")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving group_suffix to DataStore.", e)
        }
    }

    val themeSettingFlow: Flow<ThemeSetting> = context.dataStore.data
        .catch { exception ->
            Log.e(TAG, "Error reading theme_setting preferences.", exception)
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val themeName = preferences[PreferencesKeys.THEME_SETTING] ?: ThemeSetting.SYSTEM.name
            val theme = try {
                ThemeSetting.valueOf(themeName)
            } catch (e: IllegalArgumentException) {
                ThemeSetting.SYSTEM
            }
            Log.d(TAG, "Theme loaded from DataStore: '$themeName', parsed as: $theme")
            theme
        }

    suspend fun saveThemeSetting(themeSetting: ThemeSetting) {
        try {
            context.dataStore.edit { preferences ->
                preferences[PreferencesKeys.THEME_SETTING] = themeSetting.name
                Log.d(TAG, "Theme setting saved to DataStore: '${themeSetting.name}'")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving theme_setting to DataStore.", e)
        }
    }

    val autoUpdateEnabledFlow: Flow<Boolean> = context.dataStore.data
        .catch { exception -> Log.e(TAG, "Error reading auto_update_enabled", exception); emit(emptyPreferences()) }
        .map { preferences ->
            val enabled = preferences[PreferencesKeys.AUTO_UPDATE_ENABLED] ?: true
            Log.d(TAG, "Auto update enabled loaded: $enabled")
            enabled
        }

    suspend fun saveAutoUpdateEnabled(enabled: Boolean) {
        try {
            context.dataStore.edit { preferences ->
                preferences[PreferencesKeys.AUTO_UPDATE_ENABLED] = enabled
                Log.d(TAG, "Auto update enabled saved: $enabled")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving auto_update_enabled to DataStore.", e)
        }
    }

    val autoUpdateIntervalFlow: Flow<Int> = context.dataStore.data
        .catch { exception -> Log.e(TAG, "Error reading auto_update_interval", exception); emit(emptyPreferences()) }
        .map { preferences ->
            val interval = preferences[PreferencesKeys.AUTO_UPDATE_INTERVAL] ?: 30
            Log.d(TAG, "Auto update interval loaded: $interval")
            interval
        }

    suspend fun saveAutoUpdateInterval(minutes: Int) {
        try {
            context.dataStore.edit { preferences ->
                preferences[PreferencesKeys.AUTO_UPDATE_INTERVAL] = minutes
                Log.d(TAG, "Auto update interval saved: $minutes")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving auto_update_interval to DataStore.", e)
        }
    }

    val notificationsEnabledFlow: Flow<Boolean> = context.dataStore.data
        .catch { exception -> Log.e(TAG, "Error reading notifications_enabled", exception); emit(emptyPreferences()) }
        .map { preferences ->
            val enabled = preferences[PreferencesKeys.NOTIFICATIONS_ENABLED] ?: false
            Log.d(TAG, "Notifications enabled loaded: $enabled")
            enabled
        }

    suspend fun saveNotificationsEnabled(enabled: Boolean) {
        try {
            context.dataStore.edit { preferences ->
                preferences[PreferencesKeys.NOTIFICATIONS_ENABLED] = enabled
                Log.d(TAG, "Notifications enabled saved: $enabled")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving notifications_enabled to DataStore.", e)
        }
    }

    val notificationLeadTimeFlow: Flow<Int> = context.dataStore.data
        .catch { exception -> Log.e(TAG, "Error reading notification_lead_time", exception); emit(emptyPreferences()) }
        .map { preferences ->
            val leadTime = preferences[PreferencesKeys.NOTIFICATION_LEAD_TIME] ?: 15
            Log.d(TAG, "Notification lead time loaded: $leadTime")
            leadTime
        }

    suspend fun saveNotificationLeadTime(minutes: Int) {
        try {
            context.dataStore.edit { preferences ->
                preferences[PreferencesKeys.NOTIFICATION_LEAD_TIME] = minutes
                Log.d(TAG, "Notification lead time saved: $minutes")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving notification_lead_time to DataStore.", e)
        }
    }
}

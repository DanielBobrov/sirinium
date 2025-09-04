package com.dlab.sirinium

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dlab.sirinium.data.UserPreferencesRepository
import com.dlab.sirinium.ui.screens.MainScreen
import com.dlab.sirinium.ui.screens.SettingsScreen
import com.dlab.sirinium.ui.theme.SiriniumScheduleTheme
import com.dlab.sirinium.ui.viewmodel.ScheduleViewModel
import com.dlab.sirinium.ui.viewmodel.SettingsViewModel
import com.dlab.sirinium.ui.viewmodel.ThemeSetting
import kotlinx.coroutines.launch

object AppDestinations {
    const val MAIN_SCREEN = "main"
    const val SETTINGS_SCREEN = "settings"
}

private const val TAG = "MainActivityLifecycle"

class MainActivity : ComponentActivity() {

    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private lateinit var settingsViewModelInstance: SettingsViewModel

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.i(TAG, "Notification permission granted by user.")
                if (::settingsViewModelInstance.isInitialized) {
                    settingsViewModelInstance.onNotificationsEnabledChange(true)
                }
            } else {
                Log.w(TAG, "Notification permission denied by user.")
                if (::settingsViewModelInstance.isInitialized) {
                    settingsViewModelInstance.onNotificationsEnabledChange(false)
                }
            }
        }

    fun askNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    Log.d(TAG, "Notification permission already granted.")
                    if (::settingsViewModelInstance.isInitialized && !settingsViewModelInstance.notificationsEnabled.value) {
                        // settingsViewModelInstance.onNotificationsEnabledChange(true)
                    }
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    Log.i(TAG, "Showing rationale for notification permission.")
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                else -> {
                    Log.i(TAG, "Requesting notification permission.")
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            if (::settingsViewModelInstance.isInitialized && !settingsViewModelInstance.notificationsEnabled.value) {
                // settingsViewModelInstance.onNotificationsEnabledChange(true)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        userPreferencesRepository = UserPreferencesRepository(applicationContext)

        setContent {
            val application = LocalContext.current.applicationContext as Application
            settingsViewModelInstance = viewModel(
                factory = SettingsViewModelFactory(application)
            )

            val lifecycleOwner = LocalLifecycleOwner.current

            val isFirstLaunchCompleted by userPreferencesRepository.isFirstLaunchCompletedFlow.collectAsStateWithLifecycle(
                initialValue = true,
                lifecycle = lifecycleOwner.lifecycle,
                minActiveState = Lifecycle.State.STARTED
            )
            var showNotificationDialog by remember(isFirstLaunchCompleted) { mutableStateOf(!isFirstLaunchCompleted) }


            LaunchedEffect(isFirstLaunchCompleted) {
                if (!isFirstLaunchCompleted) {
                    showNotificationDialog = true
                }
            }

            val notificationsEnabled by settingsViewModelInstance.notificationsEnabled.collectAsStateWithLifecycle(
                lifecycle = lifecycleOwner.lifecycle,
                minActiveState = Lifecycle.State.STARTED
            )
            LaunchedEffect(notificationsEnabled) {
                if (notificationsEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        askNotificationPermissionIfNeeded()
                    }
                }
            }

            SiriniumApp(
                settingsViewModelInstance, 
                userPreferencesRepository, 
                ::askNotificationPermissionIfNeeded,
                showNotificationDialog,
                onDismissNotificationDialog = {
                    showNotificationDialog = false
                    lifecycleScope.launch { userPreferencesRepository.setFirstLaunchCompleted() }
                },
                onAcceptNotificationDialog = {
                    showNotificationDialog = false
                    lifecycleScope.launch { userPreferencesRepository.setFirstLaunchCompleted() }
                    askNotificationPermissionIfNeeded()
                }
            )
        }
    }
}

@Composable
fun NotificationPermissionDialog(
    onDismiss: () -> Unit,
    onAccept: () -> Unit
) {
    Log.d("NotificationDialog", "DIALOG COMPOSABLE RENDERING")
    var showTimeSelection by remember { mutableStateOf(false) }
    var selectedTime by remember { mutableStateOf(15) }
    
    if (showTimeSelection) {
        AlertDialog(
            onDismissRequest = { showTimeSelection = false },
            title = {
                Text(
                    text = "Время уведомления",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "За сколько минут до начала занятия уведомлять?",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(5, 10, 15, 30).forEach { time ->
                            FilterChip(
                                onClick = { selectedTime = time },
                                label = {
                                    Text("$time минут")
                                },
                                selected = selectedTime == time,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showTimeSelection = false
                        onAccept()
                    }
                ) {
                    Text("Подтвердить")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showTimeSelection = false }
                ) {
                    Text("Отмена")
                }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = "Уведомления о занятиях",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Приложение может уведомлять вас о предстоящих занятиях. Разрешить уведомления?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = { showTimeSelection = true }
                ) {
                    Text("Разрешить")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = onDismiss
                ) {
                    Text("Позже")
                }
            }
        )
    }
}

class SettingsViewModelFactory(private val application: Application) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

@Composable
fun SiriniumApp(
    settingsViewModel: SettingsViewModel,
    userPreferencesRepository: UserPreferencesRepository,
    askNotificationPermission: () -> Unit,
    showNotificationDialog: Boolean,
    onDismissNotificationDialog: () -> Unit,
    onAcceptNotificationDialog: () -> Unit
) {
    val currentLifecycleOwner = LocalLifecycleOwner.current
    val themeSetting by settingsViewModel.themeSetting.collectAsStateWithLifecycle(
        lifecycle = currentLifecycleOwner.lifecycle,
        minActiveState = Lifecycle.State.STARTED
    )
    val useDarkTheme = when (themeSetting) {
        ThemeSetting.LIGHT -> false
        ThemeSetting.DARK -> true
        ThemeSetting.SYSTEM -> isSystemInDarkTheme()
    }

    SiriniumScheduleTheme(darkTheme = useDarkTheme) {
        val navController: NavHostController = rememberNavController()
        val scheduleViewModel: ScheduleViewModel = viewModel()

        // Показываем диалог уведомлений здесь, где применяется тема
        if (showNotificationDialog) {
            Log.d(TAG, "DIALOG SHOULD BE VISIBLE NOW")
            NotificationPermissionDialog(
                onDismiss = onDismissNotificationDialog,
                onAccept = onAcceptNotificationDialog
            )
        }

        // Collect one-shot events from StateFlow
        LaunchedEffect(Unit) {
            settingsViewModel.groupSavedEvent.collect { savedGroup ->
                savedGroup?.let {
                Log.d(TAG, "Group saved event received: $savedGroup. Updating ScheduleViewModel.")
                scheduleViewModel.setCurrentGroup(savedGroup)
                    Log.d(TAG, "ScheduleViewModel updated with group: $savedGroup")
                    // Consume the event after processing
                settingsViewModel.consumeGroupSavedEvent()
                    Log.d(TAG, "Group saved event consumed")
                }
            }
        }

        val currentGroupSuffix by settingsViewModel.groupSuffix.collectAsStateWithLifecycle(
            lifecycle = currentLifecycleOwner.lifecycle,
            minActiveState = Lifecycle.State.STARTED
        )
        
        // Reactive group setup - runs when currentGroupSuffix changes
        LaunchedEffect(currentGroupSuffix) {
            val groupToSet = if (currentGroupSuffix.isNotBlank()) {
                // Проверяем, является ли это группой или преподавателем
                if (currentGroupSuffix.startsWith("К")) {
                    // Это группа с префиксом "К" - используем как есть
                    currentGroupSuffix
                } else if (currentGroupSuffix.matches(Regex("^\\d+[-/]\\d+$")) || 
                          currentGroupSuffix.matches(Regex("^\\d+[А-Яа-я]$")) ||
                          currentGroupSuffix.matches(Regex("^\\d+$"))) {
                    // Это группа без префикса - добавляем префикс "К"
                "К$currentGroupSuffix"
                } else {
                    // Это преподаватель - используем ID как есть (для API запросов)
                    currentGroupSuffix
                }
            } else {
                null
            }
            Log.d(TAG, "Updating ScheduleViewModel with group (from currentGroupSuffix): $groupToSet")
            if (groupToSet != null) {
            scheduleViewModel.setCurrentGroup(groupToSet)
            }
        }

        Surface(modifier = Modifier.fillMaxSize()) {
            NavHost(navController = navController, startDestination = AppDestinations.MAIN_SCREEN) {
                composable(AppDestinations.MAIN_SCREEN) { navBackStackEntry ->
                    val lifecycleOwnerFromNavHost = navBackStackEntry
                    MainScreen(
                        viewModel = scheduleViewModel,
                        lifecycleOwner = lifecycleOwnerFromNavHost,
                        onNavigateToSettings = {
                            navController.navigate(AppDestinations.SETTINGS_SCREEN)
                        }
                    )
                }
                composable(AppDestinations.SETTINGS_SCREEN) { navBackStackEntry ->
                    val lifecycleOwnerFromNavHost = navBackStackEntry
                    SettingsScreen(
                        settingsViewModel = settingsViewModel,
                        lifecycleOwner = lifecycleOwnerFromNavHost,
                        onNavigateBack = {
                            navController.popBackStack()
                        }
                    )
                }
            }
        }
    }
}


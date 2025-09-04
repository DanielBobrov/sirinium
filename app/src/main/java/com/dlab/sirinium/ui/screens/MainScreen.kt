package com.dlab.sirinium.ui.screens

import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.* // Import all Material 3 components
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dlab.sirinium.data.repository.NetworkResult
import com.dlab.sirinium.ui.composables.ModernDateNavigationBar
import com.dlab.sirinium.ui.composables.ModernDailyScheduleContent
import com.dlab.sirinium.ui.composables.ModernScheduleAppBar
import com.dlab.sirinium.ui.composables.MainScreenSettings
import com.dlab.sirinium.ui.viewmodel.ScheduleViewModel
import com.dlab.sirinium.ui.viewmodel.SettingsViewModel
import com.dlab.sirinium.ui.viewmodel.ThemeSetting
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import java.time.ZoneId // Ensure this is imported for LocalDate to Long conversion
import java.util.Date // Ensure this is imported for Long to Date conversion

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    viewModel: ScheduleViewModel,
    lifecycleOwner: LifecycleOwner,
    onNavigateToSettings: () -> Unit
) {
    val scheduleStateValue by viewModel.scheduleState.collectAsStateWithLifecycle(
        lifecycleOwner = lifecycleOwner,
        minActiveState = Lifecycle.State.STARTED
    )
    val currentGroupValue by viewModel.currentGroup.collectAsStateWithLifecycle(
        lifecycleOwner = lifecycleOwner,
        minActiveState = Lifecycle.State.STARTED
    )
    
    // Получаем доступ к SettingsViewModel для получения списка преподавателей
    val settingsViewModel: SettingsViewModel = viewModel()
    val availableTeachers by settingsViewModel.availableTeachers.collectAsStateWithLifecycle(
        lifecycle = lifecycleOwner.lifecycle,
        minActiveState = Lifecycle.State.STARTED
    )
    
    // Функция для получения отображаемого имени группы/преподавателя
    val displayName = remember(currentGroupValue, availableTeachers) {
        val groupValue = currentGroupValue
        if (groupValue.isNullOrBlank()) {
            null
        } else if (groupValue.startsWith("К")) {
            // Это группа - отображаем как есть
            groupValue
        } else {
            // Это преподаватель - ищем его имя по ID
            availableTeachers.find { it.first == groupValue }?.second ?: groupValue
        }
    }
    val selectedDateValue by viewModel.selectedDate.collectAsStateWithLifecycle(
        lifecycleOwner = lifecycleOwner,
        minActiveState = Lifecycle.State.STARTED
    )
    val isOfflineDataDisplayed by viewModel.isOfflineDataDisplayed.collectAsStateWithLifecycle(
        lifecycleOwner = lifecycleOwner,
        minActiveState = Lifecycle.State.STARTED
    )

    val context = LocalContext.current
    val density = LocalDensity.current

    // State to control the visibility of the Material 3 DatePickerDialog
    var showMaterial3DatePickerDialog by remember { mutableStateOf(false) }
    
    // State to control the visibility of settings
    var showSettings by remember { mutableStateOf(false) }
    

    
    // Получаем текущую тему из SettingsViewModel
    val currentThemeSetting by settingsViewModel.themeSetting.collectAsStateWithLifecycle(
        lifecycle = lifecycleOwner.lifecycle,
        minActiveState = Lifecycle.State.STARTED
    )
    
    // Конвертируем ThemeSetting в строку для отображения
    val selectedTheme = when (currentThemeSetting) {
        ThemeSetting.LIGHT -> "light"
        ThemeSetting.DARK -> "dark"
        ThemeSetting.SYSTEM -> "system"
    }
    

    
    // Pull to refresh state
    val isRefreshing = scheduleStateValue is NetworkResult.Loading
    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing = isRefreshing)

    Scaffold(
        topBar = {
            ModernScheduleAppBar(
                groupName = displayName,
                onSettingsClick = { showSettings = true },
                isOfflineData = isOfflineDataDisplayed
            )
        },
        bottomBar = { /* Убираем bottomBar, чтобы расписание было видно полностью */ }
    ) { paddingValues ->
        var accumulatedDrag by remember { mutableFloatStateOf(0f) }

        SwipeRefresh(
            state = swipeRefreshState,
            onRefresh = {
                Log.d("MainScreen", "Pull to refresh triggered")
                viewModel.onRefresh()
            }
        ) {
            Box(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
            ) {
                // Основной контент с расписанием
                AnimatedContent(
                    targetState = selectedDateValue, // Animation by selectedDateValue
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) { // Unit key means that `pointerInput` will not restart on recomposition due to a change in this key
                    detectHorizontalDragGestures(
                        onDragStart = {
                            accumulatedDrag = 0f
                            Log.v("MainScreen", "DragStart")
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            accumulatedDrag += dragAmount
                            // Log.v("MainScreen", "HorizontalDrag: $dragAmount, accumulated: $accumulatedDrag") // Too many logs
                        },
                        onDragEnd = {
                            val swipeThresholdPx = with(density) { 75.dp.toPx() } // Swipe threshold
                            Log.d("MainScreen", "DragEnd. AccumulatedDrag: $accumulatedDrag, Threshold: $swipeThresholdPx")
                            if (accumulatedDrag > swipeThresholdPx) { // Swipe right (previous day)
                                Log.i("MainScreen", "Swipe right detected, calling onPreviousDay.")
                                viewModel.onPreviousDay()
                            } else if (accumulatedDrag < -swipeThresholdPx) { // Swipe left (next day)
                                Log.i("MainScreen", "Swipe left detected, calling onNextDay.")
                                viewModel.onNextDay()
                            }
                            accumulatedDrag = 0f // Reset after processing
                        }
                    )
                },
            transitionSpec = {
                // Determine animation direction
                val goingForwards = targetState.isAfter(initialState) // New date > old date (e.g., next day)
                val goingBackwards = targetState.isBefore(initialState) // New date < old date (e.g., previous day)

                if (goingForwards) { // Swipe left or select next date
                    Log.v("MainScreenAnim", "Transition: Forwards ($initialState -> $targetState)")
                    slideInHorizontally { fullWidth -> fullWidth } + fadeIn() togetherWith
                            slideOutHorizontally { fullWidth -> -fullWidth } + fadeOut()
                } else if (goingBackwards) { // Swipe right or select previous date
                    Log.v("MainScreenAnim", "Transition: Backwards ($initialState -> $targetState)")
                    slideInHorizontally { fullWidth -> -fullWidth } + fadeIn() togetherWith
                            slideOutHorizontally { fullWidth -> fullWidth } + fadeOut()
                } else { // Dates are the same (e.g., on first load or if selectedDate hasn't changed)
                    Log.v("MainScreenAnim", "Transition: FadeIn/Out ($initialState -> $targetState)")
                    fadeIn() togetherWith fadeOut()
                }
            },
            label = "ScheduleContentAnimation"
        ) { targetAnimatedDate -> // targetAnimatedDate is selectedDateValue for the current animation "page"
            Log.d("MainScreenAnim", "AnimatedContent displaying for targetAnimatedDate: $targetAnimatedDate")
            val scheduleResult = scheduleStateValue

            // Data to display on the current animation "page"
            val scheduleForAnimatedDay = when (scheduleResult) {
                is NetworkResult.Success -> scheduleResult.data.find { it.date == targetAnimatedDate }
                else -> null // If Loading or Error, we don't have specific DailySchedule for targetAnimatedDate from this source
            }

            ModernDailyScheduleContent(
                dailySchedule = scheduleForAnimatedDay,
                isLoading = scheduleResult is NetworkResult.Loading && scheduleForAnimatedDay == null, // Show loading if globally loading and no data for THIS date yet
                error = (scheduleResult as? NetworkResult.Error)?.message,
                currentGroup = currentGroupValue,
                isOfflineData = isOfflineDataDisplayed,
                modifier = Modifier.fillMaxSize() // Important to pass Modifier
            )
        }
                
                // Панель с датами поверх контента
                val scheduleError = scheduleStateValue as? NetworkResult.Error
                val isGroupNotSetError = scheduleError?.message?.contains("Номер группы не указан", ignoreCase = true) == true ||
                        scheduleError?.message?.contains("Номер группы не установлен", ignoreCase = true) == true

                val showDatePanel = !currentGroupValue.isNullOrBlank() && !isGroupNotSetError

                AnimatedVisibility(
                    visible = showDatePanel,
                    enter = fadeIn() + slideInHorizontally { it / 2 },
                    exit = fadeOut() + slideOutHorizontally { -it / 2 },
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    ModernDateNavigationBar(
                        selectedDate = selectedDateValue,
                        onDateSelected = { date ->
                            Log.d("MainScreen", "ModernDateNavigationBar onDateSelected callback invoked with: $date")
                            viewModel.onDateSelected(date)
                        },
                        onCalendarIconClick = {
                            Log.d("MainScreen", "Calendar icon clicked, showing Material 3 DatePickerDialog for $selectedDateValue")
                            showMaterial3DatePickerDialog = true
                        }
                    )
                }
            }
        }
    }

    // Material 3 DatePickerDialog
    if (showMaterial3DatePickerDialog) {
        // Convert LocalDate to milliseconds for initialSelectedDateMillis
        val initialMillis = selectedDateValue.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

        DatePickerDialog(
            onDismissRequest = {
                showMaterial3DatePickerDialog = false
            },
            confirmButton = {
                Button(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        // Convert selected milliseconds back to LocalDate
                        val newSelectedDate = Date(millis).toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
                        Log.d("MainScreen", "Material 3 DatePickerDialog selected: $newSelectedDate")
                        viewModel.onDateSelected(newSelectedDate)
                    }
                    showMaterial3DatePickerDialog = false
                }) {
                    Text("ОК")
                }
            },
            dismissButton = {
                Button(onClick = {
                    showMaterial3DatePickerDialog = false
                }) {
                    Text("Отмена")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
    
    // Настройки
    MainScreenSettings(
        isVisible = showSettings,
        onDismiss = { showSettings = false },
        onThemeChange = { theme ->
            val themeSetting = when (theme) {
                "light" -> ThemeSetting.LIGHT
                "dark" -> ThemeSetting.DARK
                "system" -> ThemeSetting.SYSTEM
                else -> ThemeSetting.SYSTEM
            }
            settingsViewModel.updateThemeSetting(themeSetting)
        },
        onGroupSelection = {
            showSettings = false
            onNavigateToSettings()
        },
        onMainSettingsClick = {
            showSettings = false
            onNavigateToSettings()
        },
        currentTheme = selectedTheme,
        currentGroup = currentGroupValue ?: "",
        availableTeachers = availableTeachers,
        settingsViewModel = settingsViewModel
    )
}

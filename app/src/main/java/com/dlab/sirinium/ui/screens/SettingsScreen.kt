package com.dlab.sirinium.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource // <--- ДОБАВИТЬ ЭТОТ ИМПОРТ
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dlab.sirinium.R // <--- УБЕДИТЕСЬ, ЧТО ЭТОТ ИМПОРТ ЕСТЬ И ПРАВИЛЬНЫЙ
import com.dlab.sirinium.ui.viewmodel.SettingsViewModel
import com.dlab.sirinium.ui.viewmodel.ThemeSetting
import android.util.Log
// import java.util.Locale // Locale больше не нужен здесь для форматирования имени

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel = viewModel(),
    lifecycleOwner: LifecycleOwner,
    onNavigateBack: () -> Unit
) {
    val groupSuffixState by settingsViewModel.groupSuffix.collectAsStateWithLifecycle(
        lifecycle = lifecycleOwner.lifecycle,
        minActiveState = Lifecycle.State.STARTED
    )
    val isGroupSelectionDialogVisible by settingsViewModel.isGroupSelectionDialogVisible.collectAsStateWithLifecycle(
        lifecycle = lifecycleOwner.lifecycle,
        minActiveState = Lifecycle.State.STARTED
    )
    val isLoadingGroups by settingsViewModel.isLoadingGroups.collectAsStateWithLifecycle(
        lifecycle = lifecycleOwner.lifecycle,
        minActiveState = Lifecycle.State.STARTED
    )
    val themeSettingState by settingsViewModel.themeSetting.collectAsStateWithLifecycle(
        lifecycle = lifecycleOwner.lifecycle,
        minActiveState = Lifecycle.State.STARTED
    )
    // ... остальные state ...
    val autoUpdateEnabled by settingsViewModel.autoUpdateEnabled.collectAsStateWithLifecycle(
        lifecycle = lifecycleOwner.lifecycle,
        minActiveState = Lifecycle.State.STARTED
    )
    val autoUpdateInterval by settingsViewModel.autoUpdateIntervalMinutes.collectAsStateWithLifecycle(
        lifecycle = lifecycleOwner.lifecycle,
        minActiveState = Lifecycle.State.STARTED
    )
    val notificationsEnabled by settingsViewModel.notificationsEnabled.collectAsStateWithLifecycle(
        lifecycle = lifecycleOwner.lifecycle,
        minActiveState = Lifecycle.State.STARTED
    )
    val notificationLeadTime by settingsViewModel.notificationLeadTimeMinutes.collectAsStateWithLifecycle(
        lifecycle = lifecycleOwner.lifecycle,
        minActiveState = Lifecycle.State.STARTED
    )

    var localGroupSuffixInput by remember(groupSuffixState) { mutableStateOf(groupSuffixState) }

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) }, // Используем stringResource
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back_description)) // Используем stringResource
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SettingItemTitle(stringResource(R.string.setting_group_title))
                
                                 // Показываем текущую выбранную группу или преподавателя
                 if (groupSuffixState.isNotBlank()) {
                     val isGroup = groupSuffixState.matches(Regex("^\\d+[-/]\\d+$"))
                     val displayText = if (isGroup) {
                         "К${groupSuffixState}"
                     } else {
                         // Для преподавателя ищем его имя по ID
                         val teacherName = settingsViewModel.availableTeachers.collectAsStateWithLifecycle(
                             lifecycle = lifecycleOwner.lifecycle,
                             minActiveState = Lifecycle.State.STARTED
                         ).value.find { it.first == groupSuffixState }?.second ?: groupSuffixState
                         teacherName
                     }
                     val icon = if (isGroup) Icons.Filled.School else Icons.Filled.Person
                     val description = if (isGroup) "Группа" else "Преподаватель"
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = displayText,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Medium
                            )
                            Icon(
                                imageVector = icon,
                                contentDescription = description,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
                
                // Кнопка выбора группы
                Button(
                    onClick = { settingsViewModel.showGroupSelectionDialog() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.School,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Выбрать группу или преподавателя")
                }
                
                // Показываем информацию о загруженных данных
                if (!isLoadingGroups) {
                    val groupsCount = settingsViewModel.availableGroups.collectAsStateWithLifecycle(
                        lifecycle = lifecycleOwner.lifecycle,
                        minActiveState = Lifecycle.State.STARTED
                    ).value.size
                    val teachersCount = settingsViewModel.availableTeachers.collectAsStateWithLifecycle(
                        lifecycle = lifecycleOwner.lifecycle,
                        minActiveState = Lifecycle.State.STARTED
                    ).value.size
                    
                    // Добавляем логирование для отладки
                    LaunchedEffect(groupsCount, teachersCount) {
                        android.util.Log.d("SettingsScreen", "UI updated: groups=$groupsCount, teachers=$teachersCount")
                    }
                    
                    Text(
                        text = "Загружено: $groupsCount групп, $teachersCount преподавателей",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            item {
                SettingItemTitle(stringResource(R.string.setting_theme_title)) // "Тема оформления"
                ThemeSettingSelection(
                    currentTheme = themeSettingState,
                    onThemeSelected = { settingsViewModel.onThemeChange(it) },
                    settingsViewModel = settingsViewModel // <--- ПЕРЕДАЕМ ViewModel
                )
            }

            item {
                SettingItemTitle(stringResource(R.string.setting_auto_update_title)) // "Автоматическое обновление"
                SwitchSettingItem(
                    title = stringResource(R.string.enable_auto_update_switch), // "Включить автообновление"
                    checked = autoUpdateEnabled,
                    onCheckedChange = { settingsViewModel.onAutoUpdateEnabledChange(it) }
                )
                if (autoUpdateEnabled) {
                    OutlinedTextField(
                        value = autoUpdateInterval,
                        onValueChange = { settingsViewModel.onAutoUpdateIntervalChange(it) },
                        label = { Text(stringResource(R.string.update_interval_minutes_label)) }, // "Интервал обновления (минуты)"
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        singleLine = true
                    )
                }
            }

            item {
                SettingItemTitle(stringResource(R.string.setting_notifications_title)) // "Уведомления о занятиях"
                SwitchSettingItem(
                    title = stringResource(R.string.enable_notifications_switch), // "Включить уведомления"
                    checked = notificationsEnabled,
                    onCheckedChange = { settingsViewModel.onNotificationsEnabledChange(it) }
                )
                if (notificationsEnabled) {
                    OutlinedTextField(
                        value = notificationLeadTime,
                        onValueChange = { settingsViewModel.onNotificationLeadTimeChange(it) },
                        label = { Text(stringResource(R.string.notify_lead_time_minutes_label)) }, // "Уведомлять за (минуты до начала)"
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        singleLine = true
                    )
                }
            }

            item {
                SettingItemTitle(stringResource(R.string.setting_about_app_title)) // "О приложении"
                InfoItem(stringResource(R.string.app_info_name_label), settingsViewModel.appName) // "Название"
                InfoItem(stringResource(R.string.app_info_version_label), settingsViewModel.appVersion) // "Версия"
                InfoItem(stringResource(R.string.app_info_developer_label), settingsViewModel.developerName) // "Разработчик"
            }
        }
    }
    
    // Диалог выбора группы
    if (isGroupSelectionDialogVisible) {
        val availableGroups = settingsViewModel.availableGroups.collectAsStateWithLifecycle(
            lifecycle = lifecycleOwner.lifecycle,
            minActiveState = Lifecycle.State.STARTED
        ).value
        val availableTeachers = settingsViewModel.availableTeachers.collectAsStateWithLifecycle(
            lifecycle = lifecycleOwner.lifecycle,
            minActiveState = Lifecycle.State.STARTED
        ).value
        
        // Добавляем логирование для отладки
        LaunchedEffect(availableGroups, availableTeachers) {
            android.util.Log.d("SettingsScreen", "Dialog data: groups=${availableGroups.size}, teachers=${availableTeachers.size}")
        }
        
        GroupSelectionDialog(
            onDismiss = { settingsViewModel.hideGroupSelectionDialog() },
            onGroupSelected = { groupName -> settingsViewModel.selectGroup(groupName) },
            availableGroups = availableGroups,
            availableTeachers = availableTeachers,
            isLoading = isLoadingGroups
        )
    }
}

@Composable
fun SettingItemTitle(title: String) { // Оставляем String, так как уже получаем из stringResource
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
fun SwitchSettingItem(
    title: String, // Оставляем String
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun ThemeSettingSelection(
    currentTheme: ThemeSetting,
    onThemeSelected: (ThemeSetting) -> Unit,
    settingsViewModel: SettingsViewModel // <--- ПРИНИМАЕМ ViewModel
) {
    // Получаем список опций темы с локализованными названиями из ViewModel
    val themeOptions = remember(settingsViewModel) { // remember для эффективности
        settingsViewModel.getAvailableThemeOptions()
    }

    Column {
        themeOptions.forEach { (themeEnum, localizedThemeName) -> // Деструктурируем Pair
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onThemeSelected(themeEnum) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = (themeEnum == currentTheme),
                    onClick = { onThemeSelected(themeEnum) }
                )
                Spacer(Modifier.width(8.dp))
                Text(text = localizedThemeName) // <--- ИСПОЛЬЗУЕМ ЛОКАЛИЗОВАННОЕ ИМЯ
            }
        }
    }
}

@Composable
fun InfoItem(label: String, value: String) { // Оставляем String
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupSelectionDialog(
    onDismiss: () -> Unit,
    onGroupSelected: (String) -> Unit,
    availableGroups: List<String>,
    availableTeachers: List<Pair<String, String>>,
    isLoading: Boolean
) {
    Log.d("GroupSelectionDialog", "GroupSelectionDialog composable created")
    Log.d("GroupSelectionDialog", "availableGroups: ${availableGroups.size}, availableTeachers: ${availableTeachers.size}, isLoading: $isLoading")
    // Состояние для отображения ошибки
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    
    // Показываем ошибку, если нет данных и не загружаемся
    LaunchedEffect(availableGroups, availableTeachers, isLoading) {
        Log.d("GroupSelectionDialog", "LaunchedEffect triggered - availableGroups: ${availableGroups.size}, availableTeachers: ${availableTeachers.size}, isLoading: $isLoading")
        if (!isLoading && availableGroups.isEmpty() && availableTeachers.isEmpty()) {
            Log.d("GroupSelectionDialog", "Showing error - no data available")
            showError = true
            errorMessage = "Не удалось загрузить данные. Проверьте подключение к интернету."
        } else {
            Log.d("GroupSelectionDialog", "Hiding error - data available or loading")
            showError = false
        }
    }
    // Добавляем логирование для отладки
    LaunchedEffect(availableGroups, availableTeachers) {
        android.util.Log.d("GroupSelectionDialog", "Dialog received: groups=${availableGroups.size}, teachers=${availableTeachers.size}")
        if (availableGroups.isNotEmpty()) {
            android.util.Log.d("GroupSelectionDialog", "First few groups: ${availableGroups.take(3)}")
        }
                 if (availableTeachers.isNotEmpty()) {
             android.util.Log.d("GroupSelectionDialog", "First few teachers: ${availableTeachers.take(3).map { it.second }}")
         }
    }
    
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) } // 0 - группы, 1 - преподаватели
    
    val filteredGroups = remember(searchQuery, availableGroups) {
        val filtered = if (searchQuery.isBlank()) {
            availableGroups
        } else {
            availableGroups.filter { group ->
                group.contains(searchQuery, ignoreCase = true)
            }
        }
        Log.d("GroupSelectionDialog", "Filtered groups: ${availableGroups.size} from ${availableGroups.size}")
        filtered
    }
    
         val filteredTeachers = remember(searchQuery, availableTeachers) {
         val filtered = if (searchQuery.isBlank()) {
             availableTeachers
         } else {
             availableTeachers.filter { (_, name) ->
                 name.contains(searchQuery, ignoreCase = true)
             }
         }
         Log.d("GroupSelectionDialog", "Filtered teachers: ${filtered.size} from ${availableTeachers.size}")
         filtered
     }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Выберите группу или преподавателя")
        },
        text = {
            Column {
                // Поле поиска
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Поиск") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Поиск",
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Табы для переключения между группами и преподавателями
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    FilterChip(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        label = { Text("Группы (${filteredGroups.size})") }
                    )
                    FilterChip(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        label = { Text("Преподаватели (${filteredTeachers.size})") }
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (showError) {
                    // Показываем ошибку с кнопкой повторной попытки
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    // Здесь можно добавить callback для повторной загрузки
                                    showError = false
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Повторить")
                            }
                        }
                    }
                } else {
                    // Список групп или преподавателей
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (selectedTab == 0) {
                            // Показываем группы
                            items(filteredGroups) { group ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { 
                                            Log.d("GroupSelectionDialog", "Group clicked: $group")
                                            onGroupSelected(group) 
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.School,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = group,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        } else {
                            // Показываем преподавателей
                            items(filteredTeachers) { (id, name) ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { 
                                            Log.d("GroupSelectionDialog", "Teacher clicked: $name (ID: $id)")
                                            onGroupSelected(id) 
                                        }, // Используем ID преподавателя
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Person,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                                                                 Text(
                                             text = name,
                                             style = MaterialTheme.typography.bodyMedium,
                                             color = MaterialTheme.colorScheme.onSurface
                                         )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}
package com.dlab.sirinium.ui.composables

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import com.dlab.sirinium.R
import com.dlab.sirinium.ui.theme.*
import com.dlab.sirinium.ui.viewmodel.SettingsViewModel
import com.dlab.sirinium.ui.screens.GroupSelectionDialog
import android.util.Log
import kotlin.text.Regex

@Composable
fun MainScreenSettings(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onThemeChange: (String) -> Unit,
    onGroupSelection: () -> Unit,
    onMainSettingsClick: () -> Unit,
    currentTheme: String,
    currentGroup: String,
    availableTeachers: List<Pair<String, String>>,
    settingsViewModel: SettingsViewModel
) {
    // Состояние для диалога выбора группы
    var showGroupSelectionDialog by remember { mutableStateOf(false) }
    
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = tween(300)
        ) + fadeIn(animationSpec = tween(300)),
        exit = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(300)
        ) + fadeOut(animationSpec = tween(300))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { onDismiss() }
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.TopCenter),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    // Заголовок
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Настройки",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Закрыть",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Выбор группы/преподавателя
                    SettingsSection(
                        title = "Группа или преподаватель",
                        icon = Icons.Default.Group
                    ) {
                        val displayName = if (currentGroup.isNotBlank()) {
                            if (currentGroup.startsWith("К") || currentGroup.startsWith("И")) {
                                currentGroup
                            } else {
                                availableTeachers.find { it.first == currentGroup }?.second ?: currentGroup
                            }
                        } else {
                            "Не выбрано"
                        }
                        
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { 
                                    Log.d("MainScreenSettings", "Group selection card clicked")
                                    showGroupSelectionDialog = true
                                    Log.d("MainScreenSettings", "showGroupSelectionDialog set to true")
                                    settingsViewModel.showGroupSelectionDialog()
                                    Log.d("MainScreenSettings", "showGroupSelectionDialog() called")
                                },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (currentGroup.startsWith("К") || currentGroup.startsWith("И")) Icons.Default.School else Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Выбор темы
                    SettingsSection(
                        title = "Тема оформления",
                        icon = Icons.Default.Palette
                    ) {
                        ThemePreviewGrid(
                            currentTheme = currentTheme,
                            onThemeSelect = onThemeChange
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Основные настройки
                    SettingsSection(
                        title = "Дополнительно",
                        icon = Icons.Default.Settings
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onMainSettingsClick() },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Остальные настройки",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            
            // Диалог выбора группы - NOW CORRECTLY NESTED
            if (showGroupSelectionDialog) {
                Log.d("MainScreenSettings", "Showing GroupSelectionDialog")
                val availableGroups by settingsViewModel.availableGroups.collectAsStateWithLifecycle(
                    minActiveState = Lifecycle.State.STARTED
                )
                val availableTeachers by settingsViewModel.availableTeachers.collectAsStateWithLifecycle(
                    minActiveState = Lifecycle.State.STARTED
                )
                val isLoading by settingsViewModel.isLoadingGroups.collectAsStateWithLifecycle(
                    minActiveState = Lifecycle.State.STARTED
                )
        
                GroupSelectionDialog(
                    onDismiss = { 
                        showGroupSelectionDialog = false
                        settingsViewModel.hideGroupSelectionDialog()
                    },
                    onGroupSelected = { selection ->
                        Log.d("MainScreenSettings", "Group selected: $selection")
                        // Сохраняем выбранную группу/преподавателя
                        // SettingsViewModel автоматически обработает группу и отправит groupSavedEvent
                        // который будет обработан в MainActivity и обновит ScheduleViewModel
                        settingsViewModel.selectGroup(selection)
                        
                        Log.d("MainScreenSettings", "Group selection completed, closing dialog and settings")
                        showGroupSelectionDialog = false
                        // Закрываем быстрые настройки после выбора
                        onDismiss()
                    },
                    availableGroups = availableGroups,
                    availableTeachers = availableTeachers,
                    isLoading = isLoading
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        content()
    }
}

@Composable
private fun ThemePreviewGrid(
    currentTheme: String,
    onThemeSelect: (String) -> Unit
) {
    val themes = listOf(
        ThemeOption("light", "Светлая", R.drawable.ic_theme_light),
        ThemeOption("dark", "Темная", R.drawable.ic_theme_dark),
        ThemeOption("system", "Системная", R.drawable.ic_theme_system)
    )
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        themes.forEach { theme ->
            ThemePreviewCard(
                theme = theme,
                isSelected = currentTheme == theme.key,
                onSelect = { onThemeSelect(theme.key) }
            )
        }
    }
}

@Composable
private fun ThemePreviewCard(
    theme: ThemeOption,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    }
    
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.05f else 1f,
        animationSpec = tween(200)
    )
    
    Card(
        modifier = Modifier
            .size(100.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable { onSelect() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = borderColor
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Предпросмотр темы
            val (backgroundColor, iconColor) = when (theme.key) {
                "light" -> Color(0xFFFFFBFE) to Color(0xFF1C1B1F)
                "dark" -> Color(0xFF1C1B1F) to Color(0xFFE6E1E5)
                else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
            }
            
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(backgroundColor)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = theme.iconRes),
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = theme.name,
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

private data class ThemeOption(
    val key: String,
    val name: String,
    val iconRes: Int
)

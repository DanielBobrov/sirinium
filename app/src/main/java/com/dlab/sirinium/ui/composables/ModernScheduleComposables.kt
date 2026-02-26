package com.dlab.sirinium.ui.composables

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings

import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.filter
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dlab.sirinium.R
import com.dlab.sirinium.data.model.ScheduleItem
import com.dlab.sirinium.ui.theme.*
import com.dlab.sirinium.ui.viewmodel.DailySchedule
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun ModernScheduleAppBar(
    groupName: String?,
    onSettingsClick: () -> Unit,
    isOfflineData: Boolean
) {
    TopAppBar(
        title = {
            Text(
                text = groupName ?: stringResource(R.string.group_not_selected_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        actions = {
            IconButton(
                onClick = onSettingsClick,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Transparent
                )
            ) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.settings),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}



@Composable
fun ModernOfflineDataWarningCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = "Предупреждение",
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "Отсутствует подключение к интернету",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = "Расписание не обновлено",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ModernDailyScheduleContent(
    modifier: Modifier = Modifier,
    dailySchedule: DailySchedule?,
    isLoading: Boolean,
    error: String?,
    currentGroup: String?,
    isOfflineData: Boolean = false
) {
    Box(modifier = modifier.fillMaxSize()) {
        when {
            currentGroup.isNullOrBlank() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = "Информация",
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.please_select_group_message),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = stringResource(R.string.go_to_settings_to_select_group),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            error != null -> {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.error_loading_data, error),
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
            }
            dailySchedule == null || dailySchedule.items.isEmpty() -> {
                val message = if (dailySchedule != null && dailySchedule.items.isEmpty()) {
                    stringResource(R.string.no_lessons_on_date, dailySchedule.date.format(DateTimeFormatter.ofPattern("dd MMMM, EEEE", Locale("ru"))))
                } else {
                    stringResource(R.string.no_lessons_on_date,
                        if (dailySchedule?.date != null) dailySchedule.date.format(DateTimeFormatter.ofPattern("dd MMMM, EEEE", Locale("ru")))
                        else LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMMM, EEEE", Locale("ru")))
                    )
                }
                Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text(message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 0.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                                         item {
                         Column {
                             // Показываем предупреждение об отсутствии интернета только при отсутствии интернета
                             if (isOfflineData) {
                                 ModernOfflineDataWarningCard()
                             }
                             
                             ModernDateHeader(dailySchedule = dailySchedule)
                         }
                     }
                    val groupedByTime: Map<String, List<ScheduleItem>> = dailySchedule.items.groupBy { it.startTime }

                    groupedByTime.entries.sortedBy { it.key }.forEach { entry ->
                        val itemsAtSameTime = entry.value
                        val isCombinedSlot = itemsAtSameTime.size > 1

                                                 items(
                             items = itemsAtSameTime,
                             key = { scheduleItem ->
                                 "${scheduleItem.date}-${scheduleItem.startTime}-${scheduleItem.discipline}-${scheduleItem.groupType}-${scheduleItem.classroom}-${scheduleItem.hashCode()}"
                             }
                         ) { scheduleItem ->
                            ModernScheduleItemCard(
                                item = scheduleItem,
                                isCombined = isCombinedSlot,
                                modifier = Modifier.animateItem()
                            )
                        }
                        
                        // Добавляем карточку перерыва после каждого занятия
                        if (itemsAtSameTime.isNotEmpty()) {
                            val currentItem = itemsAtSameTime.first()
                            val nextTime = getNextLessonTime(dailySchedule.items, currentItem)
                            if (nextTime != null) {
                                val breakDuration = calculateBreakDuration(currentItem.endTime, nextTime)
                                val breakType = getBreakType(breakDuration)
                                
                                // Показываем перерыв всегда, но с разной логикой
                                item {
                                    ModernBreakCard(
                                        duration = breakDuration,
                                        modifier = Modifier.animateItem()
                                    )
                                }
                            }
                        }
                    }
                    
                    // Дополнительный блок с цветом фона в конце расписания
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .background(
                                    MaterialTheme.colorScheme.surface,
                                    RoundedCornerShape(16.dp)
                                )
                        ) {
                            // Блок пустой, только с цветом фона
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ModernDateHeader(dailySchedule: DailySchedule) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.CalendarMonth,
                contentDescription = "Дата",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = dailySchedule.dayOfWeekFullName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = dailySchedule.date.format(DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale("ru"))),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun ModernScheduleItemCard(item: ScheduleItem, isCombined: Boolean, modifier: Modifier = Modifier) {
    val lessonColor = remember(item.groupType, isCombined) {
        if (isCombined) {
            CombinedColor
        } else {
            when (item.groupType.lowercase(Locale.getDefault())) {
                "зачет", "зачёт" -> CreditColor
                "зачет дифференцированный", "зачёт дифференцированный" -> CreditColor
                "экзамен" -> CreditColor
                "лекции", "лекция" -> LectureColor
                "практические занятия", "практика" -> PracticeColor
                "внеучебное мероприятие" -> ExtracurricularColor
                else -> {
                    try {
                        if (item.color.isNotBlank() && item.color.lowercase(Locale.getDefault()) != "none") {
                            Color(android.graphics.Color.parseColor(item.color))
                        } else DefaultLessonColor
                    } catch (e: Exception) {
                        DefaultLessonColor
                    }
                }
            }
        }
    }

    // Определяем иконку для типа занятия
    val lessonIcon = remember(item.groupType) {
        when (item.groupType.lowercase(Locale.getDefault())) {
            "зачет", "зачёт", "зачет дифференцированный", "зачёт дифференцированный", "экзамен" -> Icons.Filled.Assessment
            "лекции", "лекция" -> Icons.Filled.School
            "практические занятия", "практика" -> Icons.Filled.Build
            "внеучебное мероприятие" -> Icons.Filled.Event
            else -> Icons.Filled.Class
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Левая панель с временем и иконкой
            Column(
                modifier = Modifier
                    .width(80.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(20.dp))
                    .background(lessonColor)
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Иконка типа занятия
                Icon(
                    imageVector = lessonIcon,
                    contentDescription = "Тип занятия",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                // Время начала
                Text(
                    text = item.startTime,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
                // Время окончания
                Text(
                    text = item.endTime,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
            
            // Основная информация о занятии
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.Center
            ) {
                // Название дисциплины
                Text(
                    text = item.discipline,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(6.dp))
                
                // Тип занятия с цветным индикатором
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(lessonColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = item.groupType,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Группа
                if (item.group.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.School,
                            contentDescription = "Группа",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = item.group,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // Преподаватель
                val teacherName = (item.teacherDetails?.fio
                    ?: item.teachers?.values?.firstOrNull()?.fio)
                    ?.takeIf { it.isNotBlank() }
                if (teacherName != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = "Преподаватель",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = teacherName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // Информация о месте проведения
                val locationParts = mutableListOf<String>()
                item.classroom?.takeIf { it.isNotBlank() }?.let { locationParts.add(it) }
                item.address?.takeIf { it.isNotBlank() }?.let { locationParts.add(it) }
                item.place?.takeIf { it.isNotBlank() }?.let { locationParts.add(it) }

                val locationText = if (locationParts.isNotEmpty()) {
                    locationParts.joinToString(" / ")
                } else {
                    stringResource(R.string.location_not_specified)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = "Место проведения",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = locationText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun ModernBreakCard(duration: Int, modifier: Modifier = Modifier) {
    val breakType = getBreakType(duration)
    
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (breakType) {
                is BreakType.Window -> MaterialTheme.colorScheme.secondaryContainer
                is BreakType.ShortBreak -> MaterialTheme.colorScheme.tertiaryContainer
            }
        )
    ) {
        when (breakType) {
            is BreakType.Window -> {
                // Для окна - разделенное отображение
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Schedule,
                            contentDescription = "Окно",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Окно",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    
                    Text(
                        text = getPairsText(breakType.pairsCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            is BreakType.ShortBreak -> {
                // Для перерыва - обычное отображение
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Schedule,
                            contentDescription = "Перерыв",
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Перерыв",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    
                    Text(
                        text = "$duration мин",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}



// Вспомогательные функции для расчета перерывов
private fun getNextLessonTime(items: List<ScheduleItem>, currentItem: ScheduleItem): String? {
    val currentTime = currentItem.startTime
    return items
        .filter { it.startTime > currentTime }
        .minByOrNull { it.startTime }
        ?.startTime
}

// Расписание пар (время начала и окончания)
private val lessonSchedule = mapOf(
    1 to ("08:45" to "10:05"),
    2 to ("10:20" to "11:40"),
    3 to ("11:55" to "13:15"),
    4 to ("13:30" to "14:50"),
    5 to ("15:05" to "16:25"),
    6 to ("16:40" to "18:00"),
    7 to ("18:15" to "19:35"),
    8 to ("19:50" to "21:10")
)

// Перерывы между парами
private val breaksBetweenLessons = mapOf(
    1 to 2 to 15, // между 1 и 2 парой: 10:05-10:20 = 15 мин
    2 to 3 to 15, // между 2 и 3 парой: 11:40-11:55 = 15 мин
    3 to 4 to 15, // между 3 и 4 парой: 13:15-13:30 = 15 мин
    4 to 5 to 15, // между 4 и 5 парой: 14:50-15:05 = 15 мин
    5 to 6 to 15, // между 5 и 6 парой: 16:25-16:40 = 15 мин
    6 to 7 to 15, // между 6 и 7 парой: 18:00-18:15 = 15 мин
    7 to 8 to 15  // между 7 и 8 парой: 19:35-19:50 = 15 мин
)

private fun calculateBreakDuration(endTime: String, nextStartTime: String): Int {
    val end = LocalTime.parse(endTime, DateTimeFormatter.ofPattern("HH:mm"))
    val start = LocalTime.parse(nextStartTime, DateTimeFormatter.ofPattern("HH:mm"))
    val durationMinutes = java.time.Duration.between(end, start).toMinutes().toInt()
    
    return durationMinutes
}

// Функция для определения типа перерыва
private fun getBreakType(duration: Int): BreakType {
    return when {
        duration > 80 -> {
            // Если перерыв больше 80 минут (больше одной пары), это окно
            // Вычисляем количество пропущенных пар
            val lessonDuration = 80 // 1 час 20 минут
            val breakDuration = 15 // стандартный перерыв
            val totalTimePerPair = lessonDuration + breakDuration
            
            val pairsCount = (duration / totalTimePerPair).toInt()
            BreakType.Window(pairsCount)
        }
        duration > 15 -> {
            // Перерыв больше 15 минут, но меньше одной пары - показываем минуты
            BreakType.ShortBreak(duration)
        }
        duration > 0 -> BreakType.ShortBreak(duration)
        else -> BreakType.ShortBreak(0)
    }
}

// Функция для правильного склонения слова "пара"
private fun getPairsText(count: Int): String {
    return when {
        count == 1 -> "1 пара"
        count in 2..4 -> "$count пары"
        else -> "$count пар"
    }
}

// Типы перерывов
sealed class BreakType {
    data class ShortBreak(val duration: Int) : BreakType()
    data class Window(val pairsCount: Int) : BreakType()
}

@OptIn(ExperimentalAnimationApi::class, ExperimentalFoundationApi::class)
@Composable
fun ModernDateNavigationBar(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onCalendarIconClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val today = LocalDate.now()
    var currentSelectedDate = selectedDate

    // Диапазон ±365 дней — выглядит бесконечным
    val rangeRadius = 365
    val totalDays = rangeRadius * 2 + 1
    val rangeStart = remember { today.minusDays(rangeRadius.toLong()) }

    val selectedIndex = remember(currentSelectedDate) {
        ChronoUnit.DAYS.between(rangeStart, currentSelectedDate).toInt().coerceIn(0, totalDays - 1)
    }

    // Локальный индекс для подсветки — обновляется в реальном времени при скролле
    var pendingIndex by remember { mutableIntStateOf(selectedIndex) }

    val itemWidthDp = 52.dp
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val coroutineScope = rememberCoroutineScope()
    val snapBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    // Реальное время: обновляем подсветку по мере скролла
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemScrollOffset }
            .collect {
                val layoutInfo = listState.layoutInfo
                val viewportCenter =
                    (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                val centeredItem = layoutInfo.visibleItemsInfo
                    .minByOrNull { kotlin.math.abs(it.offset + it.size / 2 - viewportCenter) }
                centeredItem?.let { pendingIndex = it.index }
            }
    }

    // После остановки скролла — сообщаем дату во ViewModel (обновляет расписание)
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .filter { !it }
            .collect {
                val layoutInfo = listState.layoutInfo
                val viewportCenter =
                    (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                val centeredItem = layoutInfo.visibleItemsInfo
                    .minByOrNull { kotlin.math.abs(it.offset + it.size / 2 - viewportCenter) }
                centeredItem?.let { item ->
                    val newDate = rangeStart.plusDays(item.index.toLong())
                    if (newDate != currentSelectedDate) {
                        onDateSelected(newDate)
                        currentSelectedDate = newDate
                    }
                }
            }
    }

    // Когда дата меняется внешне (свайп расписания) — синхронизируем pendingIndex и прокручиваем навбар
    LaunchedEffect(selectedIndex) {
        pendingIndex = selectedIndex
        if (!listState.isScrollInProgress) {
            listState.animateScrollToItem(selectedIndex)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .offset(y = (-30).dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            shadowElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Скролл-слайдер с датами
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    // Отступы по бокам центрируют активный элемент в LazyRow
                    val sidePadding = (maxWidth - itemWidthDp) / 2

                    LazyRow(
                        state = listState,
                        flingBehavior = snapBehavior,
                        contentPadding = PaddingValues(horizontal = sidePadding),
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(totalDays) { index ->
                            val dateItem = rangeStart.plusDays(index.toLong())
                            val isSelected = index == pendingIndex
                            val isToday = dateItem == today

                            val circleBgColor by animateColorAsState(
                                targetValue = if (isSelected) MaterialTheme.colorScheme.primary
                                              else Color.Transparent,
                                animationSpec = tween(3, easing = FastOutSlowInEasing),
                                label = "circleBg"
                            )
                            val circleBorderColor by animateColorAsState(
                                targetValue = when {
                                    isSelected -> MaterialTheme.colorScheme.primary
                                    isToday    -> MaterialTheme.colorScheme.primary
                                    else       -> Color.Transparent
                                },
                                animationSpec = tween(3, easing = FastOutSlowInEasing),
                                label = "circleBorder"
                            )
                            val numberColor by animateColorAsState(
                                targetValue = when {
                                    isSelected -> MaterialTheme.colorScheme.onPrimary
                                    isToday    -> MaterialTheme.colorScheme.primary
                                    else       -> MaterialTheme.colorScheme.onSurface
                                },
                                animationSpec = tween(3, easing = FastOutSlowInEasing),
                                label = "numberColor"
                            )
                            val labelColor by animateColorAsState(
                                targetValue = when {
                                    isSelected -> MaterialTheme.colorScheme.primary
                                    isToday    -> MaterialTheme.colorScheme.primary
                                    else       -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                animationSpec = tween(3, easing = FastOutSlowInEasing),
                                label = "labelColor"
                            )
                            val scale by animateFloatAsState(
                                targetValue = if (isSelected) 1.1f else 1.0f,
                                animationSpec = tween(300, easing = FastOutSlowInEasing),
                                label = "scale"
                            )

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .width(itemWidthDp)
                                    .fillMaxHeight()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        coroutineScope.launch {
                                            listState.animateScrollToItem(index)
                                        }
                                    }
                                    .graphicsLayer(scaleX = scale, scaleY = scale)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(circleBgColor, CircleShape)
                                        .border(1.5.dp, circleBorderColor, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = dateItem.dayOfMonth.toString(),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected || isToday) FontWeight.Bold
                                                     else FontWeight.Normal,
                                        color = numberColor
                                    )
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = dateItem.format(
                                        DateTimeFormatter.ofPattern("EEE", Locale("ru"))
                                    ).uppercase(Locale("ru")),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected || isToday) FontWeight.Bold
                                                 else FontWeight.Normal,
                                    color = labelColor,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                // Разделитель
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(35.dp)
                        .background(
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            RoundedCornerShape(0.5.dp)
                        )
                )

                // Кнопка календаря
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onCalendarIconClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.CalendarMonth,
                        contentDescription = "Открыть календарь",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

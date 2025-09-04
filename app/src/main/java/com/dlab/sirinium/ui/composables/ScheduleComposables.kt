package com.dlab.sirinium.ui.composables

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
fun ScheduleAppBar(
    groupName: String?,
    onSettingsClick: () -> Unit,
    isOfflineData: Boolean
) {
    TopAppBar(
        title = {
            Text(
                text = groupName ?: stringResource(R.string.group_not_selected_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        actions = {
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}



@Composable
fun OfflineDataWarningCard() {
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
fun DailyScheduleContent(
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
                        else LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMMM, EEEE", Locale("ru"))) // Фоллбэк, если dailySchedule.date null
                    )
                }
                Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text(message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                                         item {
                         Column {
                             // Показываем предупреждение об отсутствии интернета только при отсутствии интернета
                             if (isOfflineData) {
                                 OfflineDataWarningCard()
                             }
                             
                             Text(
                                 text = "${dailySchedule.dayOfWeekFullName}, ${dailySchedule.date.format(DateTimeFormatter.ofPattern("dd MMMM", Locale("ru")))}",
                                 style = MaterialTheme.typography.headlineSmall,
                                 modifier = Modifier.padding(bottom = 4.dp)
                             )
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
                            ScheduleItemCard(
                                item = scheduleItem,
                                isCombined = isCombinedSlot,
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun ScheduleItemCard(item: ScheduleItem, isCombined: Boolean, modifier: Modifier = Modifier) {
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

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .width(90.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(lessonColor)
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = item.startTime,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = item.endTime,
                    color = Color.White,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = item.discipline,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.groupType,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                // Показываем группу вместо ФИО преподавателя
                if (item.group.isNotBlank()) {
                    Text(
                        text = item.group,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                val locationParts = mutableListOf<String>()
                item.classroom?.takeIf { it.isNotBlank() }?.let { locationParts.add(it) }
                item.address?.takeIf { it.isNotBlank() }?.let { locationParts.add(it) }
                item.place?.takeIf { it.isNotBlank() }?.let { locationParts.add(it) }

                val locationText = if (locationParts.isNotEmpty()) {
                    locationParts.joinToString(" / ")
                } else {
                    stringResource(R.string.location_not_specified)
                }

                Text(
                    text = locationText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun BreakCard(duration: Int, modifier: Modifier = Modifier) {
    val breakType = getBreakType(duration)
    
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (breakType) {
                is com.dlab.sirinium.ui.composables.BreakType.Window -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                is com.dlab.sirinium.ui.composables.BreakType.ShortBreak -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        )
    ) {
        when (breakType) {
            is com.dlab.sirinium.ui.composables.BreakType.Window -> {
                // Для окна - разделенное отображение
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Schedule,
                            contentDescription = "Окно",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Окно",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Text(
                        text = getPairsText(breakType.pairsCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            is com.dlab.sirinium.ui.composables.BreakType.ShortBreak -> {
                // Для перерыва - обычное отображение
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Schedule,
                            contentDescription = "Перерыв",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Перерыв",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Text(
                        text = "$duration мин",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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

private fun calculateBreakDuration(endTime: String, nextStartTime: String): Int {
    val end = LocalTime.parse(endTime, DateTimeFormatter.ofPattern("HH:mm"))
    val start = LocalTime.parse(nextStartTime, DateTimeFormatter.ofPattern("HH:mm"))
    val durationMinutes = java.time.Duration.between(end, start).toMinutes().toInt()
    
    // Если перерыв больше 15 минут, делаем пересчет
    if (durationMinutes > 15) {
        // Пара идет 1ч 20 минут (80 минут)
        val lessonDuration = 80
        // Если перерыв равен или больше 110 минут, это окно в несколько пар
        if (durationMinutes >= 110) {
            val pairsCount = (durationMinutes / (lessonDuration + 15)).toInt()
            return pairsCount * lessonDuration + (pairsCount - 1) * 15
        }
    }
    
    return durationMinutes
}

// Функция для правильного склонения слова "пара"
private fun getPairsText(count: Int): String {
    return when {
        count == 1 -> "1 пара"
        count in 2..4 -> "$count пары"
        else -> "$count пар"
    }
}

// Функция для определения типа перерыва
private fun getBreakType(duration: Int): com.dlab.sirinium.ui.composables.BreakType {
    return when {
        duration > 80 -> {
            // Если перерыв больше 80 минут (больше одной пары), это окно
            // Вычисляем количество пропущенных пар
            val lessonDuration = 80 // 1 час 20 минут
            val breakDuration = 15 // стандартный перерыв
            val totalTimePerPair = lessonDuration + breakDuration
            
            val pairsCount = (duration / totalTimePerPair).toInt()
            com.dlab.sirinium.ui.composables.BreakType.Window(pairsCount)
        }
        duration > 15 -> {
            // Перерыв больше 15 минут, но меньше одной пары - показываем минуты
            com.dlab.sirinium.ui.composables.BreakType.ShortBreak(duration)
        }
        duration > 0 -> com.dlab.sirinium.ui.composables.BreakType.ShortBreak(duration)
        else -> com.dlab.sirinium.ui.composables.BreakType.ShortBreak(0)
    }
}

@OptIn(ExperimentalAnimationApi::class, ExperimentalFoundationApi::class)
@Composable
fun DateNavigationBar(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onCalendarIconClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val today = LocalDate.now()
    // Define a small, dynamic range of dates for the LazyRow
    val datesToShow by remember(selectedDate) {
        derivedStateOf {
            // Display 5 dates: selected, 2 before, 2 after
            (-2..2).map { offset ->
                selectedDate.plusDays(offset.toLong())
            }
        }
    }
    val dayFormatter = DateTimeFormatter.ofPattern("EEE\ndd", Locale("ru"))
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Removed LaunchedEffect for scrolling as it's not effective with a dynamically changing small list.
    // The LazyRow will naturally update its items when datesToShow changes,
    // and Modifier.animateItem() will handle the visual transitions.

        Surface(
            modifier = modifier.fillMaxWidth(),
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp)
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(
                    items = datesToShow, // Now using the small, dynamic list of 5 dates
                    key = { date -> date.toEpochDay() }
                ) { dateItem ->
                    val isSelected = dateItem == selectedDate
                    val isToday = dateItem == today

                    val backgroundColor by animateColorAsState(
                        targetValue = when {
                            isSelected -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surface
                        },
                        animationSpec = tween(durationMillis = 200),
                        label = "dateItemBackgroundAnim"
                    )
                    val textColor by animateColorAsState(
                        targetValue = when {
                            isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                            isToday -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        animationSpec = tween(durationMillis = 200),
                        label = "dateItemTextAnim"
                    )
                    val shape = CircleShape

                    Box(
                        modifier = Modifier
                            .clip(shape)
                            .clickable {
                                Log.d("DateNav", "Date clicked in UI: $dateItem. Current selectedDate from VM: $selectedDate")
                                if (dateItem != selectedDate) { // Call onDateSelected only if the date actually changed
                                    onDateSelected(dateItem)
                                } else {
                                    Log.d("DateNav", "Clicked on already selected date: $dateItem. No action.")
                                }
                            }
                            .background(backgroundColor, shape)
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .defaultMinSize(minWidth = 48.dp, minHeight = 52.dp)
                            .animateItem()
                        ,
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = dateItem.format(dayFormatter).uppercase(Locale("ru")), // Explicit Locale for uppercase
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = textColor,
                            lineHeight = 12.sp
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable { onCalendarIconClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.CalendarMonth, 
                    contentDescription = stringResource(R.string.open_calendar),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

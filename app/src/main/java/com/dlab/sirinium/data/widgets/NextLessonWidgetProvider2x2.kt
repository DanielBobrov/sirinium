package com.dlab.sirinium.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.dlab.sirinium.MainActivity
import com.dlab.sirinium.R
import com.dlab.sirinium.data.UserPreferencesRepository
import com.dlab.sirinium.data.local.AppDatabase
import com.dlab.sirinium.data.model.ScheduleItem
import com.dlab.sirinium.data.remote.RetrofitClient
import com.dlab.sirinium.data.repository.NetworkResult
import com.dlab.sirinium.data.repository.ScheduleRepository
import com.dlab.sirinium.ui.viewmodel.ThemeSetting // Импорт ThemeSetting
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

private const val TAG_WIDGET_2X2 = "NextLessonWidget2x2"

/**
 * AppWidgetProvider для виджета 2x2.
 * Отображает только номер следующей аудитории и цвет занятия.
 */
class NextLessonWidgetProvider2x2 : AppWidgetProvider() {

    private val widgetScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private lateinit var scheduleRepositoryInternal: ScheduleRepository
    private lateinit var userPreferencesRepositoryInternal: UserPreferencesRepository

    private val apiDateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale("ru"))
    private val apiTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale("ru"))

    /**
     * Инициализирует репозитории, если они еще не инициализированы.
     * @param context Контекст приложения.
     * @return true, если зависимости успешно инициализированы, иначе false.
     */
    private fun initializeDependencies(context: Context): Boolean {
        try {
            if (!::userPreferencesRepositoryInternal.isInitialized) {
                userPreferencesRepositoryInternal = UserPreferencesRepository(context.applicationContext)
            }
            if (!::scheduleRepositoryInternal.isInitialized) {
                val scheduleDao = AppDatabase.getDatabase(context.applicationContext).scheduleDao()
                scheduleRepositoryInternal = ScheduleRepository(
                    apiService = RetrofitClient.instance,
                    scheduleDao = scheduleDao,
                    context = context.applicationContext
                )
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG_WIDGET_2X2, "Error initializing dependencies: ${e.message}", e)
            return false
        }
    }

    /**
     * Вызывается для обновления виджета.
     * @param context Контекст приложения.
     * @param appWidgetManager Менеджер виджетов.
     * @param appWidgetIds Массив идентификаторов виджетов для обновления.
     */
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        if (!initializeDependencies(context)) {
            appWidgetIds.forEach { appWidgetId ->
                Log.e(TAG_WIDGET_2X2, "Failed to initialize dependencies for widget ID $appWidgetId during onUpdate.")
                updateWidgetUi(
                    context = context,
                    appWidgetManager = appWidgetManager,
                    appWidgetId = appWidgetId,
                    nextLesson = null,
                    errorMessage = context.getString(R.string.widget_error_init_failed),
                    themeSetting = ThemeSetting.LIGHT // Fallback theme
                )
            }
            return
        }

        appWidgetIds.forEach { appWidgetId ->
            Log.d(TAG_WIDGET_2X2, "onUpdate for widget ID: $appWidgetId")
			val pendingResult = try {
				goAsync()
			} catch (e: Exception) {
				Log.w(TAG_WIDGET_2X2, "goAsync() failed or not available: ${e.message}")
				null
			}
            widgetScope.launch {
                var nextLesson: ScheduleItem? = null
                var displayMessage: String? = context.getString(R.string.widget_loading)
                var currentGroupForLog: String? = null
                var themeSetting: ThemeSetting = ThemeSetting.LIGHT // Инициализация themeSetting здесь

                try {
					themeSetting = userPreferencesRepositoryInternal.themeSettingFlow.first() // Получаем настройку темы
                    val groupSuffix = userPreferencesRepositoryInternal.groupSuffixFlow.first()
                    if (groupSuffix.isBlank()) {
                        Log.w(TAG_WIDGET_2X2, "Group suffix is blank for widget ID $appWidgetId.")
                        displayMessage = context.getString(R.string.widget_select_group)
                    } else {
                        val isTeacher = !groupSuffix.startsWith("К")
                        currentGroupForLog = if (isTeacher) {
                            "teacher_$groupSuffix"
                        } else {
                            groupSuffix
                        }
                        Log.d(TAG_WIDGET_2X2, "Fetching for ${if (isTeacher) "teacher" else "group"} $currentGroupForLog, widget ID $appWidgetId")

                        val now = LocalDateTime.now()
                        val allFetchedScheduleItems = mutableListOf<ScheduleItem>()
                        val weekOffsetsToFetch = (0..7)
                        var firstErrorFromRepo: String? = null

                        fetchLoop@ for (weekOffset in weekOffsetsToFetch) {
                            if (displayMessage != context.getString(R.string.widget_loading)) break

                            try {
                                val result = if (!groupSuffix.startsWith("К")) {
                                    // Teacher selected
                                    scheduleRepositoryInternal.getTeacherSchedule(
                                        teacherId = groupSuffix,
                                        week = weekOffset,
                                        forceNetwork = false
                                    )
                                } else {
                                    // Group selected
                                    scheduleRepositoryInternal.getSchedule(
                                        group = currentGroupForLog,
                                        week = weekOffset,
                                        forceNetwork = false
                                    )
                                }
                                val awaited = withTimeout(10000L) {
                                    result
                                        .filter { it !is NetworkResult.Loading }
                                        .first()
                                }

                                when (awaited) {
                                    is NetworkResult.Success -> {
                                        allFetchedScheduleItems.addAll(awaited.data ?: emptyList())
                                        if (awaited.isStale == true) {
                                            Log.w(TAG_WIDGET_2X2, "Data for group $currentGroupForLog, week $weekOffset is stale.")
                                        }
                                    }
                                    is NetworkResult.Error -> {
                                        Log.e(TAG_WIDGET_2X2, "Error fetching for $currentGroupForLog, week $weekOffset: ${awaited.message} (Code: ${awaited.code})")
                                        if (firstErrorFromRepo == null) {
                                            firstErrorFromRepo = awaited.message ?: context.getString(R.string.widget_error_fetching_schedule)
                                        }
                                    }
                                    is NetworkResult.Loading -> { /* Отфильтровано */ }
                                }
                            } catch (e: TimeoutCancellationException) {
                                Log.e(TAG_WIDGET_2X2, "Timeout fetching week $weekOffset for $currentGroupForLog, widget $appWidgetId", e)
                                if (firstErrorFromRepo == null) {
                                    firstErrorFromRepo = context.getString(R.string.widget_network_error)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG_WIDGET_2X2, "Exception fetching week $weekOffset for $currentGroupForLog, widget $appWidgetId", e)
                                if (firstErrorFromRepo == null) {
                                    firstErrorFromRepo = context.getString(R.string.widget_network_error)
                                }
                            }

                            // После обработки этой недели пробуем найти ближайшую пару среди накопленных
                            if (allFetchedScheduleItems.isNotEmpty() && nextLesson == null) {
                                val parsedAndSortedLessons = allFetchedScheduleItems
                                    .mapNotNull { item ->
                                        try {
                                            if (item.date == null || item.startTime == null || item.endTime == null) {
                                                Log.w(TAG_WIDGET_2X2, "ScheduleItem has null date/time: ${item.discipline}")
                                                return@mapNotNull null
                                            }
                                            val lessonDate = LocalDate.parse(item.date, apiDateFormatter)
                                            val lessonStartTime = LocalTime.parse(item.startTime, apiTimeFormatter)
                                            val lessonEndTime = LocalTime.parse(item.endTime, apiTimeFormatter)
                                            Triple(
                                                LocalDateTime.of(lessonDate, lessonStartTime),
                                                LocalDateTime.of(lessonDate, lessonEndTime),
                                                item
                                            )
                                        } catch (e: DateTimeParseException) {
                                            Log.e(TAG_WIDGET_2X2, "DateTimeParseException for item '${item.discipline}' on date '${item.date}': ${e.message}")
                                            null
                                        } catch (e: Exception) {
                                            Log.e(TAG_WIDGET_2X2, "Error mapping ScheduleItem '${item.discipline}': ${e.message}")
                                            null
                                        }
                                    }
                                    .sortedBy { it.first }

                                val candidate = parsedAndSortedLessons.find { (startDateTime, _, _) ->
                                    startDateTime.isAfter(now)
                                }?.third

                                if (candidate != null) {
                                    nextLesson = candidate
                                    displayMessage = firstErrorFromRepo
                                    // нашли ближайшую — прерываем цикл недель
                                    break@fetchLoop
                                }
                            }
                        }

                        // Если на этой неделе ничего не нашли — идём дальше по циклу
                        if (nextLesson == null && displayMessage == context.getString(R.string.widget_loading)) {
                            displayMessage = firstErrorFromRepo ?: context.getString(R.string.widget_no_upcoming_lessons_for_period)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG_WIDGET_2X2, "General error during onUpdate for widget $appWidgetId, group ${currentGroupForLog ?: "N/A"}", e)
                    displayMessage = displayMessage ?: context.getString(R.string.widget_update_error)
                } finally {
                    updateWidgetUi(
                        context = context,
                        appWidgetManager = appWidgetManager,
                        appWidgetId = appWidgetId,
                        nextLesson = nextLesson,
                        errorMessage = displayMessage,
                        themeSetting = themeSetting // Передаем настройку темы
                    )
					try {
						pendingResult?.finish()
					} catch (e: Exception) {
						Log.w(TAG_WIDGET_2X2, "pendingResult.finish() failed: ${e.message}")
					}
                    Log.d(TAG_WIDGET_2X2, "Finished async work for widget ID: $appWidgetId. Final Message: $displayMessage")
                }
            }
        }
    }

    /**
     * Вызывается при изменении размера виджета.
     * @param context Контекст приложения.
     * @param appWidgetManager Менеджер виджетов.
     * @param appWidgetId Идентификатор виджета.
     * @param newOptions Новый набор опций виджета.
     */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        Log.d(TAG_WIDGET_2X2, "onAppWidgetOptionsChanged for widget ID: $appWidgetId")
        // Повторно запускаем onUpdate, чтобы перерисовать виджет с новым размером и темой
        onUpdate(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    /**
     * Обновляет пользовательский интерфейс виджета.
     * @param context Контекст приложения.
     * @param appWidgetManager Менеджер виджетов.
     * @param appWidgetId Идентификатор виджета.
     * @param nextLesson Следующее занятие для отображения.
     * @param errorMessage Сообщение об ошибке, если данные не могут быть загружены.
     * @param themeSetting Текущая настройка темы приложения.
     */
    private fun updateWidgetUi(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        nextLesson: ScheduleItem?,
        errorMessage: String?,
        themeSetting: ThemeSetting
    ) {
        Log.d(TAG_WIDGET_2X2, "updateWidgetUi for ID $appWidgetId. Next: ${nextLesson?.discipline}, Error: $errorMessage, Theme: $themeSetting")

        val views = RemoteViews(context.packageName, R.layout.widget_next_lesson_2x2)

        // Устанавливаем фон контейнера в зависимости от темы
        val backgroundResId = when (themeSetting) {
            ThemeSetting.LIGHT -> R.drawable.widget_background_light
            ThemeSetting.DARK -> R.drawable.widget_background_dark
            ThemeSetting.SYSTEM -> {
                // Определяем системную тему
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val nightModeFlags = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
                    if (nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
                        R.drawable.widget_background_dark
                    } else {
                        R.drawable.widget_background_light
                    }
                } else {
                    R.drawable.widget_background_light // Можно выбрать дефолт для старых версий
                }
            }
        }
        views.setInt(R.id.widget_content_container, "setBackgroundResource", backgroundResId)


        if (nextLesson != null) {
            views.setViewVisibility(R.id.widget_classroom_container_2x2, View.VISIBLE)
            views.setViewVisibility(R.id.widget_tv_message_2x2, View.GONE)
            views.setTextViewText(R.id.widget_tv_classroom_2x2, nextLesson.classroom ?: nextLesson.place ?: "")
            views.setInt(R.id.widget_classroom_container_2x2, "setBackgroundResource",
                getLessonBackgroundResource(nextLesson.groupType, nextLesson.color,
                    (nextLesson.teachers?.size ?: 0) > 1 && nextLesson.code == "6")
            )
            
            // Номер аудитории всегда белый для контраста с цветным фоном
            views.setInt(R.id.widget_tv_classroom_2x2, "setTextColor", context.getColor(android.R.color.white))
        } else {
            views.setViewVisibility(R.id.widget_classroom_container_2x2, View.GONE)
            views.setViewVisibility(R.id.widget_tv_message_2x2, View.VISIBLE)
            views.setTextViewText(R.id.widget_tv_message_2x2, errorMessage ?: context.getString(R.string.widget_no_upcoming_lessons))
            
            // Устанавливаем цвет текста сообщения об ошибке в зависимости от темы
            val messageTextColor = when (themeSetting) {
                ThemeSetting.LIGHT -> context.getColor(R.color.widget_text_error_light)
                ThemeSetting.DARK -> context.getColor(R.color.widget_text_error_dark)
                ThemeSetting.SYSTEM -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val nightModeFlags = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
                        if (nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
                            context.getColor(R.color.widget_text_error_dark)
                        } else {
                            context.getColor(R.color.widget_text_error_light)
                        }
                    } else {
                        context.getColor(R.color.widget_text_error_light)
                    }
                }
            }
            views.setInt(R.id.widget_tv_message_2x2, "setTextColor", messageTextColor)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(context, appWidgetId, intent, pendingIntentFlags)
        views.setOnClickPendingIntent(R.id.widget_root_layout_2x2, pendingIntent)

        try {
            appWidgetManager.updateAppWidget(appWidgetId, views)
            Log.d(TAG_WIDGET_2X2, "Widget $appWidgetId UI actually updated by updateWidgetUi.")
        } catch (e: Exception) {
            Log.e(TAG_WIDGET_2X2, "Error during final appWidgetManager.updateAppWidget for $appWidgetId: ${e.message}", e)
        }
    }

    /**
     * Возвращает ресурс фона для аудитории в зависимости от типа занятия и цвета.
     * @param groupType Тип группы занятия (например, "лекция", "практика").
     * @param apiColorStr Строковое представление цвета из API.
     * @param isCombined Является ли занятие комбинированным (для нескольких преподавателей/кодов).
     * @return ID ресурса drawable для фона.
     */
    private fun getLessonBackgroundResource(groupType: String?, apiColorStr: String?, isCombined: Boolean): Int {
        if (isCombined) return R.drawable.widget_rounded_bg_combined
        return when (groupType?.lowercase(Locale.getDefault())) {
            "зачет", "зачёт", "зачет дифференцированный", "зачёт дифференцированный", "экзамен" -> R.drawable.widget_rounded_bg_credit
            "лекции", "лекция" -> R.drawable.widget_rounded_bg_lecture
            "практические занятия", "практика" -> R.drawable.widget_rounded_bg_practice
            "внеучебное мероприятие" -> R.drawable.widget_rounded_bg_extracurricular
            else -> {
                when (apiColorStr?.lowercase(Locale.getDefault())) {
                    "#e53935" -> R.drawable.widget_rounded_bg_credit
                    "#43a047" -> R.drawable.widget_rounded_bg_lecture
                    "#1e88e5" -> R.drawable.widget_rounded_bg_practice
                    "#ffb300" -> R.drawable.widget_rounded_bg_extracurricular
                    else -> R.drawable.widget_rounded_bg_default
                }
            }
        }
    }

    /**
     * Вызывается при первом размещении виджета на главном экране.
     * @param context Контекст приложения.
     */
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        if (!initializeDependencies(context)) {
            Log.e(TAG_WIDGET_2X2, "Failed to initialize dependencies in onEnabled.")
        } else {
            Log.d(TAG_WIDGET_2X2, "Dependencies initialized successfully in onEnabled or already were.")
        }
    }

    /**
     * Вызывается при удалении последнего экземпляра виджета с главного экрана.
     * @param context Контекст приложения.
     */
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        if (widgetScope.isActive) {
            widgetScope.cancel()
            Log.d(TAG_WIDGET_2X2, "Last widget disabled, widgetScope cancelled.")
        }
    }
}

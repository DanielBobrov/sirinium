package com.dlab.sirinium.workers

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.dlab.sirinium.R
import com.dlab.sirinium.data.UserPreferencesRepository
import com.dlab.sirinium.data.local.AppDatabase
import com.dlab.sirinium.data.model.ScheduleItem
import com.dlab.sirinium.data.remote.RetrofitClient
import com.dlab.sirinium.data.repository.NetworkResult
import com.dlab.sirinium.data.repository.ScheduleRepository
import com.dlab.sirinium.widgets.NextLessonWidgetProvider2x2 // Импорт нового провайдера виджета
import com.dlab.sirinium.widgets.NextLessonWidgetProvider4x1 // Импорт нового провайдера виджета
import android.appwidget.AppWidgetProvider // Импорт AppWidgetProvider
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.TextStyle
import java.util.Locale

private const val TAG_WORKER = "ScheduleWorker"

/**
 * Worker для фоновой загрузки расписания и обновления виджетов/уведомлений.
 */
class ScheduleWorker(
    val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val repository: ScheduleRepository
    private val userPreferencesRepository: UserPreferencesRepository

    private val apiDateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale("ru"))
    private val apiTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale("ru"))

    init {
        val scheduleDao = AppDatabase.getDatabase(appContext).scheduleDao()
        repository = ScheduleRepository(
            RetrofitClient.instance,
            scheduleDao,
            appContext
        )
        userPreferencesRepository = UserPreferencesRepository(appContext)
    }

    /**
     * Выполняет основную работу воркера: загружает расписание, проверяет уведомления и обновляет виджеты.
     * @return Результат работы воркера (успех, повтор, сбой).
     */
    override suspend fun doWork(): ListenableWorker.Result {
        Log.i(TAG_WORKER, "doWork started. Attempt: $runAttemptCount")
        var groupForLogging: String? = null

        try {
            val groupSuffix = userPreferencesRepository.groupSuffixFlow.first()
            val notificationsEnabled = userPreferencesRepository.notificationsEnabledFlow.first()
            val leadTime = userPreferencesRepository.notificationLeadTimeFlow.first()

            Log.d(
                TAG_WORKER,
                "Preferences loaded: groupSuffix='$groupSuffix', notificationsEnabled=$notificationsEnabled, leadTime=$leadTime minutes"
            )

            if (groupSuffix.isBlank()) {
                Log.w(TAG_WORKER, "Group not set, worker stopping.")
                // Отправляем broadcast всем провайдерам виджетов, чтобы они обновились и показали "Выберите группу"
                sendWidgetUpdateBroadcast(appContext, NextLessonWidgetProvider2x2::class.java)
                sendWidgetUpdateBroadcast(appContext, NextLessonWidgetProvider4x1::class.java)
                return ListenableWorker.Result.success()
            }
            // Определяем, является ли текущий выбор группой или преподавателем
            val isTeacher = !groupSuffix.startsWith("К")
            val groupForLogging = if (isTeacher) {
                "teacher_$groupSuffix"
            } else {
                "К$groupSuffix"
            }
            Log.i(TAG_WORKER, "Fetching schedule for ${if (isTeacher) "teacher" else "group"}: $groupForLogging")

            var allFetchedLessons: MutableList<ScheduleItem> = mutableListOf()
            var firstErrorMessage: String? = null
            val weekOffsetsToFetch = listOf(0, 1) // Загружаем расписание на текущую и следующую неделю

            for(weekOffset in weekOffsetsToFetch) {
                try {
                    Log.d(TAG_WORKER, "Fetching for ${if (isTeacher) "teacher" else "group"} '$groupForLogging', week offset: $weekOffset")
                    val result = if (isTeacher) {
                        repository.getTeacherSchedule(groupSuffix, weekOffset, forceNetwork = true)
                    } else {
                        repository.getSchedule(groupForLogging, weekOffset, forceNetwork = true)
                    }
                        .filter { it !is NetworkResult.Loading }
                        .first()

                    Log.d(TAG_WORKER, "Result for week $weekOffset for group $groupForLogging: $result")
                    when (result) {
                        is NetworkResult.Success -> {
                            if (result.data.isNotEmpty()) {
                                allFetchedLessons.addAll(result.data)
                                Log.d(TAG_WORKER, "Fetched ${result.data.size} lessons for week offset $weekOffset")
                            } else {
                                Log.d(TAG_WORKER, "Fetched 0 lessons for week offset $weekOffset (Success but empty)")
                            }
                        }
                        is NetworkResult.Error -> {
                            if (firstErrorMessage == null) firstErrorMessage = result.message
                            Log.w(TAG_WORKER, "Error fetching schedule for week $weekOffset: ${result.message}")
                        }
                        is NetworkResult.Loading -> {
                            Log.w(TAG_WORKER, "Unexpected Loading state in 'when' for week $weekOffset: $result")
                        }
                    }
                } catch (e: NoSuchElementException) {
                    Log.e(TAG_WORKER, "Flow completed without Success/Error after Loading for week $weekOffset (group $groupForLogging)", e)
                    if (firstErrorMessage == null) firstErrorMessage = "Ошибка данных (неделя $weekOffset)"
                }
                catch (e: Exception) {
                    Log.e(TAG_WORKER, "Exception fetching schedule for week $weekOffset (group $groupForLogging): ${e.message}", e)
                    if (firstErrorMessage == null) firstErrorMessage = "Ошибка сети (неделя $weekOffset)"
                }
            }

            if (notificationsEnabled && allFetchedLessons.isNotEmpty()) {
                val now = LocalDateTime.now()
                Log.d(TAG_WORKER, "Current DateTime for notification check: $now. Total fetched lessons: ${allFetchedLessons.size}")

                val potentialLessons = allFetchedLessons
                    .mapNotNull { item ->
                        try {
                            val lessonDate = LocalDate.parse(item.date, apiDateFormatter)
                            val lessonStartTime = LocalTime.parse(item.startTime, apiTimeFormatter)
                            val lessonEndTime = LocalTime.parse(item.endTime, apiTimeFormatter)
                            val startDateTime = LocalDateTime.of(lessonDate, lessonStartTime)
                            val endDateTime = LocalDateTime.of(lessonDate, lessonEndTime)
                            if (endDateTime.isAfter(now)) {
                                Triple(startDateTime, endDateTime, item)
                            } else {
                                null
                            }
                        } catch (e: DateTimeParseException) {
                            Log.e(TAG_WORKER, "DateTimeParseException for lesson '${item.discipline}': date='${item.date}', startTime='${item.startTime}', endTime='${item.endTime}'. Error: ${e.message}")
                            null
                        }
                        catch (e: Exception) {
                            Log.e(TAG_WORKER, "Generic parsing error for lesson ${item.discipline}: ${e.message}", e)
                            null
                        }
                    }
                    .sortedBy { it.first }

                Log.d(TAG_WORKER, "Found ${potentialLessons.size} potentially upcoming/ongoing lessons for notifications.")

                potentialLessons.firstOrNull()?.let { (_, _, lessonToNotify) ->
                    if (NotificationHelper.shouldNotify(
                            lessonToNotify,
                            now,
                            leadTime,
                            apiDateFormatter,
                            apiTimeFormatter
                        )
                    ) {
                        Log.i(TAG_WORKER, "SHOULD NOTIFY for lesson: ${lessonToNotify.discipline} at ${lessonToNotify.startTime} on ${lessonToNotify.date}")
                        if (ActivityCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                            NotificationHelper.showUpcomingLessonNotification(appContext, lessonToNotify, leadTime)
                            Log.i(TAG_WORKER, "Notification shown for: ${lessonToNotify.discipline}")
                        } else {
                            Log.w(TAG_WORKER, "POST_NOTIFICATIONS permission not granted. Cannot show notification.")
                        }
                    }
                }
            }

            Log.d(TAG_WORKER, "Attempting to send broadcast to update widgets for group: $groupForLogging")
            // Отправляем широковещательные сообщения всем оставшимся провайдерам виджетов
            sendWidgetUpdateBroadcast(appContext, NextLessonWidgetProvider2x2::class.java)
            sendWidgetUpdateBroadcast(appContext, NextLessonWidgetProvider4x1::class.java)

            return ListenableWorker.Result.success()

        } catch (e: Exception) {
            Log.e(TAG_WORKER, "Critical error in doWork for group ${groupForLogging ?: "UNKNOWN"}: ${e.message}", e)
            return if (runAttemptCount < 3) ListenableWorker.Result.retry() else ListenableWorker.Result.failure()
        }
    }

    /**
     * Вспомогательная функция для отправки широковещательного сообщения конкретному провайдеру виджета.
     * @param context Контекст приложения.
     * @param providerClass Класс провайдера виджета, который нужно обновить.
     */
    private fun sendWidgetUpdateBroadcast(context: Context, providerClass: Class<out AppWidgetProvider>) {
        val intent = Intent(context, providerClass).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            val appWidgetManagerInstance = AppWidgetManager.getInstance(context)
            val ids = appWidgetManagerInstance.getAppWidgetIds(ComponentName(context, providerClass))
            if (ids.isNotEmpty()) {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
        }
        if (intent.hasExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)) {
            context.sendBroadcast(intent)
            Log.i(TAG_WORKER, "Sent broadcast to update widget provider: ${providerClass.simpleName}.")
        } else {
            Log.w(TAG_WORKER, "Widget update broadcast not sent for ${providerClass.simpleName} as no widget IDs were found.")
        }
    }
}

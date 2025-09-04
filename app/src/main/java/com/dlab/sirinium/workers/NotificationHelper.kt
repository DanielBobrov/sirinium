package com.dlab.sirinium.workers

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.dlab.sirinium.MainActivity
import com.dlab.sirinium.R
import com.dlab.sirinium.data.model.ScheduleItem
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

object NotificationHelper {

    const val CHANNEL_ID = "sirius_schedule_channel"
    private const val NOTIFICATION_ID_PREFIX = "schedule_notification_"
    private const val TAG_NOTIFICATION_HELPER = "NotificationHelper"

    fun showUpcomingLessonNotification(
        context: Context,
        lesson: ScheduleItem,
        leadTimeMinutes: Int
    ) {
        val notificationId = (NOTIFICATION_ID_PREFIX + lesson.date + lesson.startTime + lesson.discipline).hashCode()

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "Следующая пара: ${lesson.discipline}"
        val locationInfo = lesson.classroom?.takeIf { it.isNotBlank() } ?: lesson.place?.takeIf { it.isNotBlank() }
        val contentText = if (!locationInfo.isNullOrBlank()) {
            locationInfo
        } else {
            "Время: ${lesson.startTime} - ${lesson.endTime}"
        }

        val notificationIcon = R.drawable.ic_launcher_foreground // TODO: Замените на свою иконку уведомлений!

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(notificationIcon)
            .setContentTitle(title)
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, builder.build())
        Log.i(TAG_NOTIFICATION_HELPER, "Notification shown for: ${lesson.discipline} at ${lesson.startTime}")
    }

    fun shouldNotify(
        lesson: ScheduleItem,
        currentTime: LocalDateTime,
        leadTimeMinutes: Int,
        apiDateFormatter: DateTimeFormatter,
        apiTimeFormatter: DateTimeFormatter
    ): Boolean {
        try {
            val lessonDate = LocalDate.parse(lesson.date, apiDateFormatter)
            val lessonStartTime = LocalTime.parse(lesson.startTime, apiTimeFormatter)
            val lessonDateTime = LocalDateTime.of(lessonDate, lessonStartTime)

            val notificationTriggerTime = lessonDateTime.minusMinutes(leadTimeMinutes.toLong())

            Log.d(TAG_NOTIFICATION_HELPER, "Checking lesson: ${lesson.discipline} on $lessonDate at $lessonStartTime (parsed as $lessonDateTime)")
            Log.d(TAG_NOTIFICATION_HELPER, "Current time: $currentTime")
            Log.d(TAG_NOTIFICATION_HELPER, "Lead time: $leadTimeMinutes minutes")
            Log.d(TAG_NOTIFICATION_HELPER, "Notification trigger time: $notificationTriggerTime")

            val shouldShow = !currentTime.isBefore(notificationTriggerTime) && currentTime.isBefore(lessonDateTime)

            Log.d(TAG_NOTIFICATION_HELPER, "Result of shouldNotify: $shouldShow")

            return shouldShow
        } catch (e: DateTimeParseException) {
            Log.e(TAG_NOTIFICATION_HELPER, "DateTimeParseException for lesson '${lesson.discipline}': date='${lesson.date}', time='${lesson.startTime}'. Error: ${e.message}")
            return false
        }
        catch (e: Exception) {
            Log.e(TAG_NOTIFICATION_HELPER, "Error in shouldNotify for lesson '${lesson.discipline}': ${e.message}", e)
            return false
        }
    }
}

package com.dlab.sirinium.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.dlab.sirinium.data.local.SingleTeacherConverter // ИСПРАВЛЕНО: Импорт для одиночного Teacher
import com.dlab.sirinium.data.local.TeacherMapConverter // ИСПРАВЛЕНО: Импорт для карты Teacher

@Entity(tableName = "schedule_items")
@TypeConverters(TeacherMapConverter::class, SingleTeacherConverter::class) // Регистрируем оба конвертера
data class ScheduleItem(
    @PrimaryKey(autoGenerate = true)
    var localId: Int = 0,

    val date: String,
    val dayWeek: String,
    val startTime: String,
    val endTime: String,
    val discipline: String,
    val groupType: String,
    val address: String?,
    val classroom: String?,
    val comment: String?,
    val place: String?,

    val teachers: Map<String, Teacher>?, // Будет использовать TeacherMapConverter

    val teacherDetails: Teacher?, // ИСПРАВЛЕНО: Будет использовать SingleTeacherConverter

    val urlOnline: String?,
    val group: String,
    val numberPair: Int,
    val color: String = "#FFFFFF",
    val code: String?,
    var weekIdentifier: String
)

data class Teacher(
    val id: String?,
    val lastName: String?,
    val firstName: String?,
    val middleName: String?,
    val fio: String?,
    val departmentFio: String? = null,
    val department: String? = null
)

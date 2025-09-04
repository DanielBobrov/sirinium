package com.dlab.sirinium.data.local

import androidx.room.TypeConverter
import com.dlab.sirinium.data.model.Teacher
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class TeacherMapConverter { // ИСПРАВЛЕНО: Переименовано
    private val gson = Gson()

    @TypeConverter
    fun fromTeacherMap(teachers: Map<String, Teacher>?): String? {
        if (teachers == null) {
            return null
        }
        return gson.toJson(teachers)
    }

    @TypeConverter
    fun toTeacherMap(teachersString: String?): Map<String, Teacher>? {
        if (teachersString == null) {
            return null
        }
        val type = object : TypeToken<Map<String, Teacher>>() {}.type
        return gson.fromJson(teachersString, type)
    }
}

// ИСПРАВЛЕНО: Добавлен конвертер для одиночного объекта Teacher
class SingleTeacherConverter {
    private val gson = Gson()

    @TypeConverter
    fun fromTeacher(teacher: Teacher?): String? {
        if (teacher == null) {
            return null
        }
        return gson.toJson(teacher)
    }

    @TypeConverter
    fun toTeacher(teacherString: String?): Teacher? {
        if (teacherString == null) {
            return null
        }
        return gson.fromJson(teacherString, Teacher::class.java)
    }
}

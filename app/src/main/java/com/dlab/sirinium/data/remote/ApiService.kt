package com.dlab.sirinium.data.remote // Изменено

import com.dlab.sirinium.data.model.ScheduleItem
import com.dlab.sirinium.data.model.TeacherInfo
import com.dlab.sirinium.data.model.GroupInfo
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

// Интерфейс для взаимодействия с API расписания
interface ApiService {
    @GET("api/schedule")
    suspend fun getSchedule(
        @Query("group") group: String,    // Номер группы
        @Query("week") week: Int = 0      // Смещение недели (0 - текущая)
    ): Response<List<ScheduleItem>> // Ожидаем список элементов расписания
    
    @GET("api/teacherschedule")
    suspend fun getTeacherSchedule(
        @Query("id") teacherId: String,   // ID преподавателя
        @Query("week") week: Int = 0      // Смещение недели (0 - текущая)
    ): Response<List<ScheduleItem>> // Ожидаем список элементов расписания
    
    @GET("api/teachers")
    suspend fun getTeachers(): Response<Map<String, String>> // ID:ФИО
    
    @GET("api/groups")
    suspend fun getGroups(): Response<List<String>> // Список всех групп
}

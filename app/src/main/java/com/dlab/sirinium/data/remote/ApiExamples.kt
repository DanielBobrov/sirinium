package com.dlab.sirinium.data.remote

import com.dlab.sirinium.data.repository.ScheduleRepository
import com.dlab.sirinium.data.repository.TeachersRepository
import com.dlab.sirinium.data.repository.GroupsRepository
import com.dlab.sirinium.data.repository.NetworkResult
import kotlinx.coroutines.flow.collect

/**
 * Примеры использования нового API
 * 
 * Новые эндпоинты:
 * - https://eralas.ru/api/teacherschedule?id=...&week=0 - расписание преподавателя
 * - https://eralas.ru/api/teachers - список всех преподавателей
 * - https://eralas.ru/api/groups - список всех групп  
 * - https://eralas.ru/api/schedule?group=...&week=0 - расписание группы
 */

class ApiExamples(
    private val scheduleRepository: ScheduleRepository,
    private val teachersRepository: TeachersRepository,
    private val groupsRepository: GroupsRepository
) {
    
    /**
     * Пример получения списка всех преподавателей
     */
    suspend fun getTeachersExample() {
        teachersRepository.getTeachers().collect { result ->
            when (result) {
                is NetworkResult.Success -> {
                    val teachers = result.data
                    println("Найдено преподавателей: ${teachers.size}")
                    teachers.forEach { teacher ->
                        println("ID: ${teacher.id}, ФИО: ${teacher.name}")
                    }
                }
                is NetworkResult.Error -> {
                    println("Ошибка: ${result.message}")
                }
                is NetworkResult.Loading -> {
                    println("Загрузка...")
                }
            }
        }
    }
    
    /**
     * Пример получения списка всех групп
     */
    suspend fun getGroupsExample() {
        groupsRepository.getGroups().collect { result ->
            when (result) {
                is NetworkResult.Success -> {
                    val groups = result.data
                    println("Найдено групп: ${groups.size}")
                    groups.forEach { group ->
                        println("Группа: ${group.name}")
                    }
                }
                is NetworkResult.Error -> {
                    println("Ошибка: ${result.message}")
                }
                is NetworkResult.Loading -> {
                    println("Загрузка...")
                }
            }
        }
    }
    
    /**
     * Пример получения расписания преподавателя
     */
    suspend fun getTeacherScheduleExample(teacherId: String) {
        scheduleRepository.getTeacherSchedule(teacherId, 0).collect { result ->
            when (result) {
                is NetworkResult.Success -> {
                    val lessons = result.data
                    println("Найдено занятий у преподавателя: ${lessons.size}")
                    lessons.forEach { lesson ->
                        println("${lesson.date} ${lesson.startTime}-${lesson.endTime}: ${lesson.discipline}")
                    }
                }
                is NetworkResult.Error -> {
                    println("Ошибка: ${result.message}")
                }
                is NetworkResult.Loading -> {
                    println("Загрузка...")
                }
            }
        }
    }
    
    /**
     * Пример получения расписания группы
     */
    suspend fun getGroupScheduleExample(groupName: String) {
        scheduleRepository.getSchedule(groupName, 0).collect { result ->
            when (result) {
                is NetworkResult.Success -> {
                    val lessons = result.data
                    println("Найдено занятий в группе: ${lessons.size}")
                    lessons.forEach { lesson ->
                        println("${lesson.date} ${lesson.startTime}-${lesson.endTime}: ${lesson.discipline}")
                    }
                }
                is NetworkResult.Error -> {
                    println("Ошибка: ${result.message}")
                }
                is NetworkResult.Loading -> {
                    println("Загрузка...")
                }
            }
        }
    }
}

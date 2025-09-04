// File: com/dlab/sirinium/data/local/ScheduleDao.kt
package com.dlab.sirinium.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dlab.sirinium.data.model.ScheduleItem
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedule_items WHERE weekIdentifier = :weekIdentifier ORDER BY date, startTime")
    fun getScheduleForWeek(weekIdentifier: String): Flow<List<ScheduleItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(scheduleItems: List<ScheduleItem>)

    @Query("DELETE FROM schedule_items WHERE weekIdentifier = :weekIdentifier")
    suspend fun deleteScheduleForWeek(weekIdentifier: String)

    suspend fun clearAndInsertForWeek(weekIdentifier: String, scheduleItems: List<ScheduleItem>) {
        deleteScheduleForWeek(weekIdentifier)
        if (scheduleItems.isNotEmpty()) {
            insertAll(scheduleItems.map { it.copy(weekIdentifier = weekIdentifier) })
        }
    }
}
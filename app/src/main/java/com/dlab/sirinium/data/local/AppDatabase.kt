// File: com/dlab/sirinium/data/local/AppDatabase.kt
package com.dlab.sirinium.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.dlab.sirinium.data.model.ScheduleItem

@Database(
    entities = [ScheduleItem::class],
    version = 2, // Убедитесь, что версия = 2
    exportSchema = true // Рекомендуется установить в true для отслеживания схем
)
@TypeConverters(TeacherMapConverter::class, SingleTeacherConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scheduleDao(): ScheduleDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private const val DATABASE_NAME = "sirinium_schedule_database"

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    // Добавляем миграцию. Room вызовет ее, ТОЛЬКО если
                    // на устройстве существует база данных версии 1.
                    // Если база создается с нуля, миграция не нужна и не будет вызвана.
                    .addMigrations(MIGRATION_1_2)
                    // ОПЦИОНАЛЬНО: Для разработки, если вы часто меняете схему
                    // и не хотите писать миграции каждый раз, можно использовать:
                    // .fallbackToDestructiveMigration()
                    // Но это УДАЛИТ ВСЕ ДАННЫЕ при обновлении схемы без миграции.
                    // НЕ ИСПОЛЬЗУЙТЕ В ПРОДАВКШЕНЕ без явной необходимости.
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
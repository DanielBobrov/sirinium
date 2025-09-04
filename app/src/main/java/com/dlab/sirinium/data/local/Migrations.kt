// File: com/dlab/sirinium/data/local/Migrations.kt
package com.dlab.sirinium.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.util.Log // Для логирования

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Логируем начало миграции
        Log.i("Migration_1_2", "Starting migration from version 1 to 2.")

        // Проверяем, существует ли уже столбец, прежде чем его добавлять.
        // Это более безопасный подход, если миграция может запускаться несколько раз
        // или если вы не уверены в точном состоянии старой схемы.
        // Однако, для простого случая добавления столбца, если вы уверены,
        // что в версии 1 его не было, это необязательно.
        // Но так как вы столкнулись с "duplicate column", этот код может помочь.

        var columnExists = false
        val cursor = db.query("PRAGMA table_info(schedule_items)")
        val nameIndex = cursor.getColumnIndex("name")
        if (nameIndex >= 0) {
            while (cursor.moveToNext()) {
                if ("weekIdentifier" == cursor.getString(nameIndex)) {
                    columnExists = true
                    break
                }
            }
        }
        cursor.close()

        if (!columnExists) {
            Log.i("Migration_1_2", "Column 'weekIdentifier' does not exist. Adding column.")
            db.execSQL("ALTER TABLE schedule_items ADD COLUMN weekIdentifier TEXT NOT NULL DEFAULT ''")
            Log.i("Migration_1_2", "Column 'weekIdentifier' added successfully.")
        } else {
            Log.w("Migration_1_2", "Column 'weekIdentifier' already exists. Skipping ADD COLUMN operation.")
        }
        // Здесь могли бы быть другие SQL-команды для миграции, если бы они были нужны.
        Log.i("Migration_1_2", "Finished migration from version 1 to 2.")
    }
}
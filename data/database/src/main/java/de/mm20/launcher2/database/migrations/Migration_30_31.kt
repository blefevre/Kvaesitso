package de.mm20.launcher2.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

class Migration_30_31 : Migration(30, 31) {

    override fun migrate(connection: SQLiteConnection) {
        // Add context history column to Searchable table for smart favorites
        connection.execSQL(
            """
            ALTER TABLE `Searchable` ADD COLUMN `contextHistory` TEXT NOT NULL DEFAULT '[]'
            """.trimIndent()
        )
    }
}
package com.speedomate.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [TripEntity::class], version = 2, exportSchema = false)
abstract class TripDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao

    companion object {
        @Volatile private var INSTANCE: TripDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE trips ADD COLUMN minAltitude REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE trips ADD COLUMN maxAltitude REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE trips ADD COLUMN altitudePoints TEXT NOT NULL DEFAULT '[]'")
            }
        }

        fun getDatabase(context: Context): TripDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    TripDatabase::class.java,
                    "trip_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
package com.cybertrail.app.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Track::class, TrackPoint::class, PhotoAnchor::class, WaypointEntity::class, RouteEntity::class], version = 6, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun waypointDao(): WaypointDao
    abstract fun routeDao(): RouteDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE tracks ADD COLUMN favorite INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `waypoints` (
                        `id` TEXT NOT NULL, 
                        `name` TEXT NOT NULL, 
                        `latitude` REAL NOT NULL, 
                        `longitude` REAL NOT NULL, 
                        `elevation` REAL, 
                        `description` TEXT, 
                        `iconType` TEXT NOT NULL, 
                        `favorite` INTEGER NOT NULL DEFAULT 0, 
                        `createTime` INTEGER NOT NULL, 
                        `photoRef` TEXT, 
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `routes` (
                        `id` TEXT NOT NULL, 
                        `name` TEXT NOT NULL, 
                        `description` TEXT, 
                        `createTime` INTEGER NOT NULL, 
                        `favorite` INTEGER NOT NULL DEFAULT 0, 
                        `distanceMeters` REAL NOT NULL, 
                        `estimatedTimeMinutes` INTEGER NOT NULL, 
                        `waypointIds` TEXT NOT NULL, 
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "cybertrail_database"
                )
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

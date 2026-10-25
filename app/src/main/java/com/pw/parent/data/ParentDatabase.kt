package com.pw.parent.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [OfflineDraft::class, NotificationItem::class],
    version = 1,
    exportSchema = false
)
abstract class ParentDatabase : RoomDatabase() {
    abstract fun parentDao(): ParentDao

    companion object {
        @Volatile
        private var INSTANCE: ParentDatabase? = null

        fun getDatabase(context: Context): ParentDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ParentDatabase::class.java,
                    "parent_app_database"
                )
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

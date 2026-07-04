package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [PariMessage::class], version = 1, exportSchema = false)
abstract class PariDatabase : RoomDatabase() {
    abstract fun pariDao(): PariDao

    companion object {
        @Volatile
        private var INSTANCE: PariDatabase? = null

        fun getDatabase(context: Context): PariDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PariDatabase::class.java,
                    "pari_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

package com.luma.reader.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Book::class, Highlight::class],
    version = 1,
    exportSchema = false
)
abstract class LumaDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun highlightDao(): HighlightDao

    companion object {
        @Volatile
        private var instance: LumaDatabase? = null

        fun get(context: Context): LumaDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    LumaDatabase::class.java,
                    "luma.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}

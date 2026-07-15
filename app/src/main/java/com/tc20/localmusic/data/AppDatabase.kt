package com.tc20.localmusic.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [Song::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
}

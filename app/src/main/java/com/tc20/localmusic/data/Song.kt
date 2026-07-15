package com.tc20.localmusic.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class Song(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val artist: String,
    val duration: Long,
    val localFilePath: String,
    val originUrl: String,
    val isFavorite: Boolean = false,
    val artworkLocalPath: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

package com.tc20.localmusic.data

import kotlinx.coroutines.flow.Flow

interface SongRepository {
    fun songs(): Flow<List<Song>>
    fun searchSongs(query: String): Flow<List<Song>>
    suspend fun insert(song: Song): Long
    suspend fun update(song: Song)
    suspend fun delete(song: Song)
    suspend fun deleteById(songId: Long)
    suspend fun getById(songId: Long): Song?
    suspend fun setFavorite(songId: Long, favorite: Boolean)
    suspend fun toggleFavorite(songId: Long)
}

class RoomSongRepository(private val dao: SongDao) : SongRepository {
    override fun songs(): Flow<List<Song>> = dao.getAllSongs()
    override fun searchSongs(query: String): Flow<List<Song>> = dao.search(query)
    override suspend fun insert(song: Song): Long = dao.insert(song)
    override suspend fun update(song: Song) = dao.update(song)
    override suspend fun delete(song: Song) = dao.delete(song)
    override suspend fun deleteById(songId: Long) = dao.deleteById(songId)
    override suspend fun getById(songId: Long): Song? = dao.getById(songId)
    override suspend fun setFavorite(songId: Long, favorite: Boolean) = dao.setFavorite(songId, favorite)
    override suspend fun toggleFavorite(songId: Long) = dao.toggleFavorite(songId)
}

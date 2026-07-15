package com.tc20.localmusic.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext

class MusicController(private val context: Context) {
    private val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
    private val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()

    suspend fun controller(): MediaController = withContext(Dispatchers.Main.immediate) {
        controllerFuture.await()
    }

    suspend fun playQueue(songs: List<com.tc20.localmusic.data.Song>, startSongId: Long) {
        val controller = controller()
        val index = songs.indexOfFirst { it.id == startSongId }.coerceAtLeast(0)
        controller.setMediaItems(songs.map { it.toMediaItem(context) }, index, C.TIME_UNSET)
        controller.prepare()
        controller.play()
    }

    suspend fun playPause() {
        val c = controller()
        if (c.isPlaying) c.pause() else c.play()
    }

    suspend fun next() {
        val c = controller()
        if (c.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)) c.seekToNextMediaItem()
    }

    suspend fun previous() {
        val c = controller()
        if (c.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)) c.seekToPreviousMediaItem()
    }

    suspend fun seekTo(positionMs: Long) {
        val c = controller()
        if (c.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) c.seekTo(positionMs)
    }

    suspend fun sendToggleFavorite() {
        controller().sendCustomCommand(SessionCommand(PlaybackService.ACTION_TOGGLE_FAVORITE, android.os.Bundle.EMPTY), android.os.Bundle.EMPTY)
    }

    fun release() {
        MediaController.releaseFuture(controllerFuture)
    }
}

package com.tc20.localmusic.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.tc20.localmusic.LocalMusicApp
import com.tc20.localmusic.data.Song
import com.tc20.localmusic.data.SongRepository
import com.tc20.localmusic.download.AudioDownloader
import com.tc20.localmusic.playback.MusicController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    application: Application,
    private val repository: SongRepository,
    private val downloader: AudioDownloader,
    private val musicController: MusicController
) : AndroidViewModel(application) {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val songs: StateFlow<List<Song>> = _query
        .flatMapLatest { text ->
            if (text.isBlank()) repository.songs() else repository.searchSongs(text.trim())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private val _playback = MutableStateFlow(PlaybackUiState())
    val playback: StateFlow<PlaybackUiState> = _playback.asStateFlow()

    private var progressJob: Job? = null

    init {
        attachPlayer()
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun downloadFromUrl(url: String) {
        if (url.isBlank()) {
            _downloadState.value = DownloadState.Error("Wklej link do pliku audio.")
            return
        }
        viewModelScope.launch {
            _downloadState.value = DownloadState.Downloading(0f)
            runCatching {
                downloader.downloadAndSave(url) { progress ->
                    _downloadState.value = DownloadState.Downloading(progress)
                }
            }.onSuccess { song ->
                _downloadState.value = DownloadState.Success(song.title)
            }.onFailure { error ->
                _downloadState.value = DownloadState.Error(error.message ?: "Nie udało się pobrać utworu.")
            }
        }
    }

    fun resetDownloadState() {
        _downloadState.value = DownloadState.Idle
    }

    fun play(song: Song) {
        viewModelScope.launch {
            musicController.playQueue(songs.value, song.id)
        }
    }

    fun playPause() {
        viewModelScope.launch { musicController.playPause() }
    }

    fun next() {
        viewModelScope.launch { musicController.next() }
    }

    fun previous() {
        viewModelScope.launch { musicController.previous() }
    }

    fun seekTo(positionMs: Long) {
        viewModelScope.launch { musicController.seekTo(positionMs) }
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch { repository.toggleFavorite(song.id) }
    }

    fun deleteSong(song: Song) {
        viewModelScope.launch {
            repository.delete(song)
            runCatching { File(song.localFilePath).delete() }
            song.artworkLocalPath?.let { runCatching { File(it).delete() } }
        }
    }

    private fun attachPlayer() {
        viewModelScope.launch {
            val controller = musicController.controller()
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) = sync(controller)
                override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) = sync(controller)
                override fun onPlaybackStateChanged(playbackState: Int) = sync(controller)
            }
            controller.addListener(listener)
            sync(controller)
            progressJob = launch {
                while (isActive) {
                    sync(controller)
                    delay(500)
                }
            }
        }
    }

    private fun sync(player: Player) {
        val currentId = player.currentMediaItem?.mediaId?.toLongOrNull()
        val currentSong = songs.value.firstOrNull { it.id == currentId }
        val duration = player.duration.takeIf { it > 0 } ?: currentSong?.duration ?: 0L
        val position = player.currentPosition.coerceAtLeast(0L)
        _playback.value = PlaybackUiState(
            currentSong = currentSong,
            isPlaying = player.isPlaying,
            durationMs = duration,
            positionMs = position.coerceAtMost(duration.coerceAtLeast(position)),
            hasNext = player.hasNextMediaItem(),
            hasPrevious = player.hasPreviousMediaItem()
        )
    }

    override fun onCleared() {
        progressJob?.cancel()
        musicController.release()
        super.onCleared()
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory {
            val app = application as LocalMusicApp
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MainViewModel(
                        application = app,
                        repository = app.container.songRepository,
                        downloader = app.container.audioDownloader,
                        musicController = app.container.musicController
                    ) as T
                }
            }
        }
    }
}

sealed interface DownloadState {
    data object Idle : DownloadState
    data class Downloading(val progress: Float) : DownloadState
    data class Success(val title: String) : DownloadState
    data class Error(val message: String) : DownloadState
}

data class PlaybackUiState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false
)

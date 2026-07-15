package com.tc20.localmusic.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tc20.localmusic.ui.theme.LocalMusicTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        MainViewModel.factory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LocalMusicTheme {
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) {}
                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                val songs by viewModel.songs.collectAsStateWithLifecycle()
                val query by viewModel.query.collectAsStateWithLifecycle()
                val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()
                val playback by viewModel.playback.collectAsStateWithLifecycle()

                MusicAppScreen(
                    songs = songs,
                    query = query,
                    downloadState = downloadState,
                    playback = playback,
                    onQueryChange = viewModel::setQuery,
                    onSongClick = viewModel::play,
                    onFavoriteClick = viewModel::toggleFavorite,
                    onDeleteSong = viewModel::deleteSong,
                    onDownload = viewModel::downloadFromUrl,
                    onDismissDownload = viewModel::resetDownloadState,
                    onPlayPause = viewModel::playPause,
                    onNext = viewModel::next,
                    onPrevious = viewModel::previous,
                    onSeek = viewModel::seekTo
                )
            }
        }
    }
}

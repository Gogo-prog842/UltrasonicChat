package com.tc20.localmusic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.tc20.localmusic.R
import com.tc20.localmusic.data.Song
import java.io.File
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicAppScreen(
    songs: List<Song>,
    query: String,
    downloadState: DownloadState,
    playback: PlaybackUiState,
    onQueryChange: (String) -> Unit,
    onSongClick: (Song) -> Unit,
    onFavoriteClick: (Song) -> Unit,
    onDeleteSong: (Song) -> Unit,
    onDownload: (String) -> Unit,
    onDismissDownload: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit
) {
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var showPlayer by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(downloadState) {
        if (downloadState is DownloadState.Success) showAddDialog = false
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF163D25), Color(0xFF050505), Color(0xFF050505))
                )
            )
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("BeatLy", fontWeight = FontWeight.Bold)
                            Text("Offline-first Kotlin player", style = MaterialTheme.typography.labelMedium, color = Color(0xFFBDBDBD))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { showAddDialog = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Dodaj") }
                )
            },
            bottomBar = {
                playback.currentSong?.let { song ->
                    MiniPlayer(
                        song = song,
                        playback = playback,
                        onClick = { showPlayer = true },
                        onPlayPause = onPlayPause
                    )
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(horizontal = 18.dp)
                    .fillMaxSize()
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    placeholder = { Text("Szukaj utworu albo wykonawcy") },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp)
                )
                Spacer(Modifier.height(18.dp))
                if (songs.isEmpty()) {
                    EmptyLibrary()
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 110.dp)
                    ) {
                        items(songs, key = { it.id }) { song ->
                            SongRow(
                                song = song,
                                isCurrent = playback.currentSong?.id == song.id,
                                onClick = { onSongClick(song) },
                                onFavoriteClick = { onFavoriteClick(song) },
                                onDelete = { onDeleteSong(song) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddSongDialog(
            state = downloadState,
            onDismiss = {
                showAddDialog = false
                onDismissDownload()
            },
            onDownload = onDownload
        )
    }

    if (showPlayer && playback.currentSong != null) {
        PlayerDialog(
            playback = playback,
            onDismiss = { showPlayer = false },
            onPlayPause = onPlayPause,
            onNext = onNext,
            onPrevious = onPrevious,
            onSeek = onSeek,
            onFavoriteClick = { playback.currentSong?.let(onFavoriteClick) }
        )
    }
}

@Composable
private fun SongRow(
    song: Song,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = if (isCurrent) Color(0xFF263F2D) else Color(0xFF171717)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AlbumArt(path = song.artworkLocalPath, modifier = Modifier.size(58.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color(0xFFBDBDBD))
                Text(formatDuration(song.duration), style = MaterialTheme.typography.labelSmall, color = Color(0xFF8D8D8D))
            }
            IconButton(onClick = onFavoriteClick) {
                Icon(
                    imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Ulubione",
                    tint = if (song.isFavorite) MaterialTheme.colorScheme.primary else Color.White
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Usuń", tint = Color(0xFFBDBDBD))
            }
        }
    }
}

@Composable
private fun AlbumArt(path: String?, modifier: Modifier = Modifier) {
    AsyncImage(
        model = path?.let { File(it) } ?: R.drawable.album_placeholder,
        contentDescription = null,
        modifier = modifier.clip(RoundedCornerShape(14.dp)),
        contentScale = ContentScale.Crop
    )
}

@Composable
private fun EmptyLibrary() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(86.dp)
                .clip(CircleShape)
                .background(Color(0xFF1E1E1E)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(18.dp))
        Text("Biblioteka jest pusta", fontWeight = FontWeight.Bold)
        Text("Kliknij Dodaj i wklej bezpośredni link do audio.", color = Color(0xFFBDBDBD))
    }
}

@Composable
private fun AddSongDialog(
    state: DownloadState,
    onDismiss: () -> Unit,
    onDownload: (String) -> Unit
) {
    var url by rememberSaveable { mutableStateOf("") }
    val isDownloading = state is DownloadState.Downloading

    AlertDialog(
        onDismissRequest = { if (!isDownloading) onDismiss() },
        title = { Text("Pobierz i dodaj") },
        text = {
            Column {
                Text("Wklej bezpośredni legalny link do pliku audio. Linki do stron typu YouTube/Spotify wymagają własnego legalnego resolvera.")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    enabled = !isDownloading,
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("https://example.com/song.mp3") }
                )
                Spacer(Modifier.height(14.dp))
                when (state) {
                    DownloadState.Idle -> Unit
                    is DownloadState.Downloading -> {
                        LinearProgressIndicator(progress = { state.progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Text("Pobieranie: ${(state.progress * 100).roundToLong()}%")
                    }
                    is DownloadState.Success -> Text("Dodano: ${state.title}", color = MaterialTheme.colorScheme.primary)
                    is DownloadState.Error -> Text(state.message, color = Color(0xFFFF8A80))
                }
            }
        },
        confirmButton = {
            Button(onClick = { onDownload(url) }, enabled = !isDownloading) {
                if (isDownloading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Pobierz i dodaj")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isDownloading) { Text("Zamknij") }
        }
    )
}

@Composable
private fun MiniPlayer(
    song: Song,
    playback: PlaybackUiState,
    onClick: () -> Unit,
    onPlayPause: () -> Unit
) {
    Card(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(12.dp)
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF202020))
    ) {
        Column {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AlbumArt(path = song.artworkLocalPath, modifier = Modifier.size(48.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color(0xFFBDBDBD))
                }
                IconButton(onClick = onPlayPause) {
                    Icon(if (playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Play/Pause")
                }
            }
            LinearProgressIndicator(
                progress = { playback.progressFraction() },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun PlayerDialog(
    playback: PlaybackUiState,
    onDismiss: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onFavoriteClick: () -> Unit
) {
    val song = playback.currentSong ?: return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Odtwarzacz", modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Zamknij") }
            }
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AlbumArt(
                    path = song.artworkLocalPath,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                )
                Spacer(Modifier.height(18.dp))
                Text(song.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(song.artist, color = Color(0xFFBDBDBD), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(14.dp))
                Slider(
                    value = playback.positionMs.toFloat(),
                    onValueChange = { onSeek(it.toLong()) },
                    valueRange = 0f..playback.durationMs.coerceAtLeast(1L).toFloat()
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatDuration(playback.positionMs), style = MaterialTheme.typography.labelMedium)
                    Text(formatDuration(playback.durationMs), style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    IconButton(onClick = onFavoriteClick) {
                        Icon(
                            if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Ulubione",
                            tint = if (song.isFavorite) MaterialTheme.colorScheme.primary else Color.White
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    IconButton(onClick = onPrevious, enabled = playback.hasPrevious) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Poprzedni", modifier = Modifier.size(34.dp))
                    }
                    IconButton(
                        onClick = onPlayPause,
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            if (playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.Black,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    IconButton(onClick = onNext, enabled = playback.hasNext) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Następny", modifier = Modifier.size(34.dp))
                    }
                }
            }
        },
        confirmButton = {}
    )
}

private fun PlaybackUiState.progressFraction(): Float {
    if (durationMs <= 0L) return 0f
    return (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

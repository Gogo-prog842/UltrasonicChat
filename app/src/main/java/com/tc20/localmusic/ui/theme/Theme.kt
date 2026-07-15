package com.tc20.localmusic.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SpotifyGreen = Color(0xFF1DB954)
private val AlmostBlack = Color(0xFF050505)
private val DarkSurface = Color(0xFF121212)
private val CardSurface = Color(0xFF1E1E1E)

private val DarkScheme: ColorScheme = darkColorScheme(
    primary = SpotifyGreen,
    secondary = SpotifyGreen,
    background = AlmostBlack,
    surface = DarkSurface,
    surfaceVariant = CardSurface,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFE0E0E0)
)

@Composable
fun LocalMusicTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}

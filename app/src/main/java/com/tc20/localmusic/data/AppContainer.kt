package com.tc20.localmusic.data

import android.content.Context
import androidx.room.Room
import com.tc20.localmusic.download.AudioDownloader
import com.tc20.localmusic.download.DirectAudioLinkResolver
import com.tc20.localmusic.download.MetadataExtractor
import com.tc20.localmusic.playback.MusicController
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppContainer(private val context: Context) {
    val database: AppDatabase by lazy {
        Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "local_music.db"
        ).build()
    }

    val songRepository: SongRepository by lazy {
        RoomSongRepository(database.songDao())
    }

    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    val metadataExtractor: MetadataExtractor by lazy {
        MetadataExtractor(context.applicationContext)
    }

    val audioDownloader: AudioDownloader by lazy {
        AudioDownloader(
            context = context.applicationContext,
            client = httpClient,
            resolver = DirectAudioLinkResolver(httpClient),
            metadataExtractor = metadataExtractor,
            repository = songRepository
        )
    }

    val musicController: MusicController by lazy {
        MusicController(context.applicationContext)
    }
}

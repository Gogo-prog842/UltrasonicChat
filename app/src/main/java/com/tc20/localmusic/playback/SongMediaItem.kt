package com.tc20.localmusic.playback

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.tc20.localmusic.data.Song
import java.io.File

fun Song.toMediaItem(context: Context): MediaItem {
    val audioUri = Uri.fromFile(File(localFilePath))
    val artworkFile = artworkLocalPath?.let(::File)?.takeIf { it.exists() }
    val artworkUri = artworkFile?.let {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it)
    }
    val artworkBytes = artworkFile?.runCatching { readBytes() }?.getOrNull()

    val metadataBuilder = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle("Local Library")
        .setAlbumArtist(artist)

    if (artworkUri != null) metadataBuilder.setArtworkUri(artworkUri)
    if (artworkBytes != null) {
        metadataBuilder.setArtworkData(artworkBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
    }

    return MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(audioUri)
        .setMediaMetadata(metadataBuilder.build())
        .build()
}

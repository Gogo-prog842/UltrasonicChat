package com.tc20.localmusic.download

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MetadataExtractor(private val context: Context) {
    suspend fun extract(file: File, fallbackTitle: String?, fallbackArtist: String?): LocalAudioMetadata = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() }
                ?: fallbackTitle
                ?: file.nameWithoutExtension.replace('_', ' ')
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() }
                ?: fallbackArtist
                ?: "Unknown Artist"
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: 0L
            val artworkPath = retriever.embeddedPicture?.let { saveEmbeddedArtwork(it, file.nameWithoutExtension) }
            LocalAudioMetadata(title, artist, duration, artworkPath)
        } finally {
            retriever.release()
        }
    }

    private fun saveEmbeddedArtwork(bytes: ByteArray, baseName: String): String? {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val dir = File(context.filesDir, "artwork").apply { mkdirs() }
        val safeName = baseName.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.ifBlank { "cover" }
        val file = File(dir, "${safeName}_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)
        }
        return file.absolutePath
    }
}

data class LocalAudioMetadata(
    val title: String,
    val artist: String,
    val duration: Long,
    val artworkLocalPath: String?
)

package com.tc20.localmusic.download

import android.content.Context
import com.tc20.localmusic.data.Song
import com.tc20.localmusic.data.SongRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

class AudioDownloader(
    private val context: Context,
    private val client: OkHttpClient,
    private val resolver: LinkResolver,
    private val metadataExtractor: MetadataExtractor,
    private val repository: SongRepository
) {
    suspend fun downloadAndSave(inputUrl: String, onProgress: suspend (Float) -> Unit): Song {
        val resolved = resolver.resolve(inputUrl)
        val target = downloadAudio(resolved, onProgress)
        val artworkPathFromResolver = resolved.artworkUrl?.let { downloadArtwork(it) }
        val metadata = metadataExtractor.extract(
            file = target,
            fallbackTitle = resolved.suggestedTitle,
            fallbackArtist = resolved.suggestedArtist
        )

        val song = Song(
            title = metadata.title,
            artist = metadata.artist,
            duration = metadata.duration,
            localFilePath = target.absolutePath,
            originUrl = inputUrl,
            isFavorite = false,
            artworkLocalPath = artworkPathFromResolver ?: metadata.artworkLocalPath
        )
        val id = repository.insert(song)
        return song.copy(id = id)
    }

    private suspend fun downloadAudio(resolved: ResolvedAudio, onProgress: suspend (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(resolved.audioUrl)
            .get()
            .header("User-Agent", "BeatLy/1.0")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Pobieranie nieudane: HTTP ${response.code}")
            }
            val body = response.body ?: throw IllegalStateException("Serwer zwrócił pusty plik.")
            val extension = extensionFrom(response.header("Content-Type"), resolved.audioUrl)
            val dir = File(context.filesDir, "music").apply { mkdirs() }
            val outputFile = File(dir, "song_${System.currentTimeMillis()}.$extension")
            val total = body.contentLength()
            var downloaded = 0L

            body.byteStream().use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (total > 0) onProgress((downloaded.toFloat() / max(total, 1L)).coerceIn(0f, 1f))
                    }
                }
            }
            onProgress(1f)
            outputFile
        }
    }

    private suspend fun downloadArtwork(url: String): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", "BeatLy/1.0")
            .build()
        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body ?: return@use null
                val contentType = response.header("Content-Type")?.lowercase().orEmpty()
                val ext = when {
                    "png" in contentType -> "png"
                    "webp" in contentType -> "webp"
                    else -> "jpg"
                }
                val dir = File(context.filesDir, "artwork").apply { mkdirs() }
                val file = File(dir, "cover_${System.currentTimeMillis()}.$ext")
                body.byteStream().use { input ->
                    FileOutputStream(file).use { output -> input.copyTo(output) }
                }
                file.absolutePath
            }
        }.getOrNull()
    }

    private fun extensionFrom(contentType: String?, url: String): String {
        val path = url.substringBefore('?').lowercase()
        listOf("mp3", "m4a", "aac", "ogg", "opus", "flac", "wav").firstOrNull { path.endsWith(".$it") }?.let { return it }
        val type = contentType?.substringBefore(';')?.lowercase().orEmpty()
        return when {
            "mpeg" in type || "mp3" in type -> "mp3"
            "mp4" in type || "m4a" in type || "aac" in type -> "m4a"
            "ogg" in type -> "ogg"
            "opus" in type -> "opus"
            "flac" in type -> "flac"
            "wav" in type -> "wav"
            else -> "mp3"
        }
    }
}

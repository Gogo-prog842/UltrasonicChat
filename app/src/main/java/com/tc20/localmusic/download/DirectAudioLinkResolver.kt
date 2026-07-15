package com.tc20.localmusic.download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class DirectAudioLinkResolver(
    private val client: OkHttpClient
) : LinkResolver {
    private val blockedHosts = listOf(
        "youtube.com", "youtu.be", "music.youtube.com",
        "spotify.com", "open.spotify.com",
        "tiktok.com", "vm.tiktok.com",
        "soundcloud.com"
    )

    override suspend fun resolve(inputUrl: String): ResolvedAudio = withContext(Dispatchers.IO) {
        val httpUrl = inputUrl.trim().toHttpUrlOrNull()
            ?: throw UnsupportedLinkException("To nie wygląda jak poprawny link HTTPS/HTTP.")

        val host = httpUrl.host.lowercase()
        if (blockedHosts.any { host == it || host.endsWith(".$it") }) {
            throw UnsupportedLinkException(
                "Ten link pochodzi z platformy, która nie udostępnia bezpośredniego legalnego pliku audio. " +
                    "Podepnij własny legalny resolver/backend albo użyj bezpośredniego linku do pliku .mp3/.m4a/.ogg/.flac/.wav."
            )
        }

        val head = Request.Builder()
            .url(httpUrl)
            .head()
            .header("User-Agent", USER_AGENT)
            .build()

        val response = client.newCall(head).execute()
        response.use {
            val finalUrl = it.request.url.toString()
            val contentType = it.header("Content-Type")?.substringBefore(";")?.lowercase()
            val disposition = it.header("Content-Disposition")
            val titleFromHeader = disposition?.let(::titleFromContentDisposition)
            val looksLikeAudio = isSupportedAudioContentType(contentType) || hasSupportedAudioExtension(finalUrl)

            if (!looksLikeAudio) {
                throw UnsupportedLinkException(
                    "Serwer nie zwrócił pliku audio. Content-Type: ${contentType ?: "brak"}. " +
                        "Aplikacja przyjmuje bezpośrednie linki do audio, a nie strony z odtwarzaczem."
                )
            }

            ResolvedAudio(
                audioUrl = finalUrl,
                suggestedTitle = titleFromHeader,
                contentType = contentType
            )
        }
    }

    private fun isSupportedAudioContentType(contentType: String?): Boolean {
        return contentType != null && (
            contentType.startsWith("audio/") ||
                contentType == "application/octet-stream" ||
                contentType == "application/x-mpegurl"
            )
    }

    private fun hasSupportedAudioExtension(url: String): Boolean {
        val clean = url.substringBefore('?').lowercase()
        return listOf(".mp3", ".m4a", ".aac", ".ogg", ".opus", ".flac", ".wav").any { clean.endsWith(it) }
    }

    private fun titleFromContentDisposition(header: String): String? {
        val marker = "filename="
        val index = header.indexOf(marker, ignoreCase = true)
        if (index == -1) return null
        return header.substring(index + marker.length)
            .trim()
            .trim('"')
            .substringBeforeLast('.')
            .replace('_', ' ')
            .takeIf { it.isNotBlank() }
    }

    private companion object {
        const val USER_AGENT = "BeatLy/1.0"
    }
}

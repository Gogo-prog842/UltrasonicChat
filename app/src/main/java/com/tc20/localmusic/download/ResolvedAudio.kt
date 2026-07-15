package com.tc20.localmusic.download

data class ResolvedAudio(
    val audioUrl: String,
    val suggestedTitle: String? = null,
    val suggestedArtist: String? = null,
    val artworkUrl: String? = null,
    val contentType: String? = null
)

class UnsupportedLinkException(message: String) : IllegalArgumentException(message)

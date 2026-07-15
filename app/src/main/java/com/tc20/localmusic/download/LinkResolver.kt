package com.tc20.localmusic.download

interface LinkResolver {
    suspend fun resolve(inputUrl: String): ResolvedAudio
}

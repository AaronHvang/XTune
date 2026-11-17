package com.qqmusictv.data.qq

/** Raw, provider-shaped values returned by [QQMusicApi]; mapped to the app's DTOs one layer up. */

data class QQSong(
    val mid: String,
    val name: String,
    val singerName: String,
    val singerMid: String?,
    val albumMid: String?,
    val albumName: String,
    val durationSec: Int,
    val payPlay: Int = 0,
)

data class QQAlbum(
    val mid: String,
    val name: String,
    val singerName: String,
    val singerMid: String?,
    val releaseDate: String?,
    val trackCount: Int,
    val genre: String?,
    val company: String?,
    val description: String?,
)

data class QQArtist(
    val mid: String,
    val name: String,
    val songCount: Int = 0,
    val albumCount: Int = 0,
)

data class QQPlaylist(
    val id: String,
    val name: String,
    val curator: String,
    val coverUrl: String?,
    val description: String?,
    val songCount: Int,
)

data class QQSearchResult(
    val songs: List<QQSong> = emptyList(),
    val albums: List<QQAlbum> = emptyList(),
    val artists: List<QQArtist> = emptyList(),
    val playlists: List<QQPlaylist> = emptyList(),
)

object QQImages {
    fun album(mid: String?, size: Int = 500): String? =
        mid?.takeIf { it.isNotBlank() }
            ?.let { "https://y.gtimg.cn/music/photo_new/T002R${size}x${size}M000$it.jpg" }

    fun artist(mid: String?, size: Int = 300): String? =
        mid?.takeIf { it.isNotBlank() }
            ?.let { "https://y.gtimg.cn/music/photo_new/T001R${size}x${size}M000$it.jpg" }
}

/** Loopback port of the in-app web server; playback URLs point at its `/api/stream/` route. */
object QQStreamGateway {
    @Volatile
    var port: Int = 8080
}

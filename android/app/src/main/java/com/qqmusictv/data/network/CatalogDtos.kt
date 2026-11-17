package com.qqmusictv.data.network

import com.qqmusictv.data.model.resizeArtwork
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SongDto(
    val id:             String,
    val type:           String       = "songs",
    val title:          String,
    val artistName:     String,
    val artistId:       String? = null,
    val albumId:        String? = null,
    val albumName:      String,
    val durationMs:     Long,
    val artworkUrl:     String?,
    val artworkBgColor: String?,
    val previewUrl:     String?,
    val previewHlsUrl:  String?,
    val hasLyrics:      Boolean      = false,
    val trackNumber:    Int?         = null,
    val dateAdded:      String?      = null,
    val genreNames:     List<String> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class AlbumDto(
    val id:             String,
    val title:          String,
    val artistName:     String,
    val artworkUrl:     String?,
    val type:           String       = "albums",
    val artworkBgColor: String?,
    val releaseDate:    String?,
    val trackCount:     Int          = 0,
    val genreNames:     List<String> = emptyList(),
    val recordLabel:    String?      = null,
    val copyright:      String?      = null,
    val editorialNotes: String?      = null,
    val isMasteredForItunes: Boolean = false,
    /** Square motion-artwork loop for shelves we deliberately animate. */
    val motionUrl:      String?      = null,
    /** Editorial tagline for spotlight cards. */
    val tagline:        String?      = null,
    /** Pre-filled landscape editorial image URL. */
    val wideArtworkUrl: String?      = null,
    val dateAdded: String? = null,
    val additionOrder: Int? = null,
)

@JsonClass(generateAdapter = true)
data class ArtistDto(
    val id:             String,
    val name:           String,
    val artworkUrl:     String?,
    val genreNames:     List<String> = emptyList(),
    val editorialNotes: String?      = null,
)

@JsonClass(generateAdapter = true)
data class SimilarArtistDto(
    val id:         String,
    val name:       String,
    val artworkUrl: String?,
)

@JsonClass(generateAdapter = true)
data class ArtistFullDto(
    val id:             String,
    val name:           String,
    val artworkUrl:     String?,
    val genreNames:     List<String>          = emptyList(),
    val editorialNotes: String?               = null,
    val topSongs:       List<SongDto>         = emptyList(),
    val musicVideos:    List<SongDto>         = emptyList(),
    val latestRelease:  AlbumDto?             = null,
    val albums:         List<AlbumDto>        = emptyList(),
    val featuredAlbums: List<AlbumDto>        = emptyList(),
    val similarArtists: List<SimilarArtistDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class PlaylistDto(
    val id:             String,
    val name:           String,
    val curatorName:    String,
    val artworkUrl:     String?,
    val artworkBgColor: String?,
    val description:    String?,
    val playlistType:   String? = null,
    val dateAdded: String? = null,
    val additionOrder: Int? = null,
) {
    fun artworkUrl(size: Int) = artworkUrl?.resizeArtwork(size)
}

@JsonClass(generateAdapter = true)
data class CuratorDto(
    val id:         String,
    val name:       String,
    val kind:       String  = "curator",   // "multiroom" | "curator" | "apple-curator"
    val isEditorial:    Boolean = false,
    val artworkUrl: String? = null,
)

@JsonClass(generateAdapter = true)
data class SearchResponse(
    val songs:     List<SongDto>     = emptyList(),
    val albums:    List<AlbumDto>    = emptyList(),
    val artists:   List<ArtistDto>   = emptyList(),
    val playlists: List<PlaylistDto> = emptyList(),
    val curators:  List<CuratorDto>  = emptyList(),
)

@JsonClass(generateAdapter = true)
data class TracksResponse(val tracks: List<SongDto>, val next: String? = null)

@JsonClass(generateAdapter = true)
data class AlbumsResponse(val albums: List<AlbumDto> = emptyList())

@JsonClass(generateAdapter = true)
data class SongsResponse(val songs: List<SongDto> = emptyList())

@JsonClass(generateAdapter = true)
data class StationStreamResponse(
    val liveStreamUrl: String? = null,
    val drmKeyUri:     String? = null,
    val certUrl:       String? = null,
    val wvKeyUri:      String? = null,
    val adamId:        String? = null,
    val isLive:        Boolean = true,
)

@JsonClass(generateAdapter = true)
data class LibrarySongsResponse(
    val songs: List<SongDto> = emptyList(),
    val songsNewestFirst: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class LibraryAlbumsResponse(val albums: List<AlbumDto> = emptyList())

@JsonClass(generateAdapter = true)
data class LibraryPlaylistsResponse(val playlists: List<PlaylistDto> = emptyList())

@JsonClass(generateAdapter = true)
data class LibraryArtistsResponse(val artists: List<ArtistDto> = emptyList())

@JsonClass(generateAdapter = true)
data class LyricWord(val startMs: Long, val endMs: Long, val text: String)

@JsonClass(generateAdapter = true)
data class LyricBackground(
    val startMs: Long,
    val endMs:   Long = 0L,
    val text:    String,
    val words:   List<LyricWord> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class LyricLine(
    val startMs:    Long,
    val endMs:      Long = 0L,
    val text:       String,
    val words:      List<LyricWord> = emptyList(),
    val background: LyricBackground? = null,
)

@JsonClass(generateAdapter = true)
data class LyricsResponse(val lines: List<LyricLine> = emptyList(), val source: String? = null)

@JsonClass(generateAdapter = true)
data class MotionResponse(val video: String? = null)

@JsonClass(generateAdapter = true)
data class GenreDto(val id: String, val name: String)

@JsonClass(generateAdapter = true)
data class GenresResponse(val genres: List<GenreDto> = emptyList())

@JsonClass(generateAdapter = true)
data class HomeSection(
    val title: String,
    val albums: List<AlbumDto> = emptyList(),
    val videos: List<SongDto> = emptyList(),
    /** Editorial room id for this shelf; renders a trailing "More" card. */
    val roomId: String? = null,
    /** Presentation hint: "spotlight" | "gradient" | null. */
    val style: String? = null,
)

@JsonClass(generateAdapter = true)
data class HomeResponse(val sections: List<HomeSection> = emptyList())

@JsonClass(generateAdapter = true)
data class CategorySectionDto(val title: String, val items: List<CuratorDto> = emptyList())

@JsonClass(generateAdapter = true)
data class CategoriesResponse(val sections: List<CategorySectionDto> = emptyList())

@JsonClass(generateAdapter = true)
data class MultiRoomDto(
    val id: String = "",
    val title: String = "",
    val description: String? = null,
    val artworkUrl: String? = null,
    val sections: List<HomeSection> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class AuthStatus(
    val hasMUT:     Boolean,
    val mutSetAt:   String?,
    val hasBearer:  Boolean,
)

@JsonClass(generateAdapter = true)
data class AccountServiceStatus(
    val name:   String,
    val status: String,
)

@JsonClass(generateAdapter = true)
data class AccountStatusResponse(
    val ok:        Boolean,
    val services:  List<AccountServiceStatus> = emptyList(),
    val checkedAt: String? = null,
)

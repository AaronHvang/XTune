package com.qqmusictv.data.repository

import com.qqmusictv.data.VideoPlaybackPolicy

import com.qqmusictv.data.model.Album
import com.qqmusictv.data.model.Artist
import com.qqmusictv.data.model.Song
import com.qqmusictv.data.AccountPreferences
import com.qqmusictv.data.ServerPreferences
import com.qqmusictv.data.auth.QQAuthState
import com.qqmusictv.data.qq.QQBrowseSource
import com.qqmusictv.data.qq.QQCookieStore
import com.qqmusictv.data.qq.QQLyricsSource
import com.qqmusictv.data.qq.QQMusicDataSource
import com.qqmusictv.data.qq.QQStreamGateway
import com.qqmusictv.data.network.*
import javax.inject.Inject
import javax.inject.Singleton

/** An editorial curator (e.g. "Formula 1", "Tomorrowland") — opens a page of its playlists. */
data class Curator(
    val id:         String,
    val name:       String,
    val kind:       String,   // "multiroom" | "curator" | "apple-curator"
    val isEditorial:    Boolean,
    val artworkUrl: String?,
)

/** A titled row of category tiles (Genres, Moods & Activities, Decades). */
data class CategoryGroup(val title: String, val items: List<Curator>)

data class LibrarySongCollection(val songs: List<Song>, val newestFirst: Boolean)

data class SearchResults(
    val songs:     List<Song>    = emptyList(),
    val albums:    List<Album>   = emptyList(),
    val artists:   List<Artist>  = emptyList(),
    val playlists: List<Album>   = emptyList(),
    val curators:  List<Curator> = emptyList(),
)

@Singleton
class MusicRepository @Inject constructor(
    private val mutPrefs: AccountPreferences,
    private val serverPrefs: ServerPreferences,
    private val direct: QQMusicDataSource,
    private val directLyrics: QQLyricsSource,
    private val directBrowse: QQBrowseSource,
    private val cookieStore: QQCookieStore,
    private val qqAuth: QQAuthState,
    private val appleAuth: com.qqmusictv.data.auth.AuthIssueState,
) {
    val authIssue = appleAuth.issue

    // ── Catalog ───────────────────────────────────────────────────────────
    suspend fun search(term: String, limit: Int = 20): Result<SearchResults> =
        direct.search(term, limit).map { r ->
            SearchResults(
                songs = r.songs.filter { VideoPlaybackPolicy.allows(it.id, it.type) }.map(::songFromDto),
                albums = r.albums.filter { VideoPlaybackPolicy.allows(it.id, it.type) }.map(::albumFromDto),
                artists = r.artists.map(::artistFromDto),
                playlists = r.playlists.map(::playlistToAlbum).filter { it.id.startsWith("pl.") },
                curators = r.curators.map { Curator(it.id, it.name, it.kind, it.isEditorial, it.artworkUrl) },
            )
        }

    suspend fun getStationTracks(id: String) =
        direct.stationTracks(id).map { it.filter { VideoPlaybackPolicy.allows(it.id, it.type) }.map(::songFromDto) }

    suspend fun getStationStream(id: String) = direct.stationStream(id)

    suspend fun getAlbum(id: String) =
        direct.album(id).mapCatching { albumFromDto(it) }
            .mapCatching { item -> check(VideoPlaybackPolicy.allows(item.id, item.type)); item }

    suspend fun getAlbumTracks(id: String) =
        direct.albumTracks(id).map { it.filter { VideoPlaybackPolicy.allows(it.id, it.type) }.map(::songFromDto) }

    suspend fun getRelatedAlbums(id: String) =
        direct.relatedAlbums(id).map { it.filter { VideoPlaybackPolicy.allows(it.id, it.type) }.map(::albumFromDto) }

    suspend fun getSong(id: String) =
        direct.song(id).mapCatching { songFromDto(it) }
            .mapCatching { item -> check(VideoPlaybackPolicy.allows(item.id, item.type)); item }

    suspend fun getArtist(id: String) = direct.artist(id).map(::artistFromDto)

    suspend fun getArtistFull(id: String) =
        direct.artistFull(id).map(VideoPlaybackPolicy::artist)

    suspend fun getArtistAlbums(id: String) =
        direct.artistAlbums(id).map { it.filter { VideoPlaybackPolicy.allows(it.id, it.type) }.map(::albumFromDto) }

    // ── Home ─────────────────────────────────────────────────────────────
    suspend fun getHome() =
        runCatching { sectionsOf(directBrowse.home(mutPrefs.hasMUT())) }.map(VideoPlaybackPolicy::home)

    private val gradientTitleRe = Regex("^Playlists Made for You", RegexOption.IGNORE_CASE)

    // Style by title: "Top Picks for You" is the big-lockup hero,
    // "Playlists Made for You" is the gradient shelf.
    private fun sectionsOf(pairs: List<Pair<String, List<AlbumDto>>>) =
        HomeResponse(
            sections = pairs.map { (title, albums) ->
                val style = when {
                    title.equals("Top Picks for You", ignoreCase = true) -> "picks"
                    gradientTitleRe.containsMatchIn(title) -> "gradient"
                    else -> null
                }
                HomeSection(title, albums, style = style)
            }
        )

    suspend fun getBrowse() =
        runCatching { HomeResponse(directBrowse.browse()) }.map(VideoPlaybackPolicy::home)

    // Curator page (playlists, or grouping tabs for rich apple-curators).
    suspend fun getCurator(id: String, isEditorial: Boolean) =
        runCatching { direct.getCurator(id, isEditorial) }.map(VideoPlaybackPolicy::room)

    /** A plain editorial room — the "see all" page opened by a shelf's "More" card. */
    suspend fun getRoom(id: String) =
        runCatching { direct.getRoom(id) }.map(VideoPlaybackPolicy::room)

    suspend fun getMultiRoom(id: String) =
        runCatching { direct.getMultiRoom(id) }.map(VideoPlaybackPolicy::room)

    /** A catalog grouping page (e.g. Music Videos = grouping 34). */
    suspend fun getGrouping(id: String) =
        if (!VideoPlaybackPolicy.allows(id, "grouping")) Result.success(MultiRoomDto(id = id, title = ""))
        else runCatching { direct.getGrouping(id) }.map(VideoPlaybackPolicy::room)

    // Genre/mood/decade tile grid (each tile is a curator → category page).
    suspend fun getCategories(): Result<List<CategoryGroup>> =
        runCatching { direct.getCategories().map { it.toGroup() } }
            .map { groups ->
                groups.map { it.copy(items = it.items.filter { item -> VideoPlaybackPolicy.allows(item.id, item.kind) }) }
                    .filter { it.items.isNotEmpty() }
            }

    private fun CategorySectionDto.toGroup() =
        CategoryGroup(title, items.map { Curator(it.id, it.name, it.kind, it.isEditorial, it.artworkUrl) })

    suspend fun getGenres() =
        direct.genres().map { g -> g.filter { it.name.isNotEmpty() && it.id != "34" } }
            .map { genres -> genres.filter { VideoPlaybackPolicy.allows(it.id, "genres") } }

    suspend fun getGenreContent(id: String) =
        runCatching { sectionsOf(directBrowse.genreContent(id)) }.map(VideoPlaybackPolicy::home)

    suspend fun getRelatedSongs(songId: String) =
        direct.relatedSongs(songId).map { it.filter { VideoPlaybackPolicy.allows(it.id, it.type) }.map(::songFromDto) }

    suspend fun getGenreStation(genreId: String) =
        direct.genreStationSongs(genreId).map { it.filter { VideoPlaybackPolicy.allows(it.id, it.type) }.map(::songFromDto) }

    // ── Lyrics ────────────────────────────────────────────────────────────
    suspend fun getLyrics(songId: String, title: String = "", artist: String = "", durationSec: Long = 0) =
        runCatching { directLyrics.getLyrics(songId, direct.storefront, title, artist, durationSec) }

    /** Warm the lyrics cache for an upcoming song so it shows instantly on switch. */
    suspend fun prefetchLyrics(songId: String, title: String, artist: String, durationSec: Long) {
        runCatching { directLyrics.getLyrics(songId, direct.storefront, title, artist, durationSec) }
    }

    suspend fun getMotion(songId: String) = direct.motion(songId)

    /** Probe a personalized ra.* station's raw payload. */
    suspend fun probeStation(id: String): Result<String> = direct.probeStation(id)

    /** Animated cover for an editorial playlist; null for user playlists. */
    suspend fun getPlaylistMotion(playlistId: String): Result<String?> = direct.playlistMotion(playlistId)

    /** Motion loop for any card (editorial playlist / album / song), or null. Used for
     *  focus-triggered animated artwork on every shelf. */
    suspend fun cardMotion(id: String, type: String): String? = runCatching {
        when {
            id.startsWith("pl.") || type == "playlists" -> getPlaylistMotion(id).getOrNull()
            type == "albums" || id.startsWith("l.")      -> direct.albumMotion(id).getOrNull()
            type == "songs"                              -> getMotion(id).getOrNull()
            else -> null
        }
    }.getOrNull()

    /** QQ Music has no companion PC server, so there is never a proxy to reach. */
    suspend fun pingServer(): Boolean = false

    suspend fun getAccountStatus() = direct.accountStatus()

    /** Shared account region for metadata recovery. */
    val catalogStorefront: String get() = direct.storefront

    /** Pre-warm storefront / account state. */
    suspend fun prepareStandalone() {
        direct.detectStorefront()
    }

    // ── Full song stream URL ───────────────────────────────────────────────
    /** Resolved lazily by the in-app server, which exchanges the cookie for a fresh vkey URL. */
    fun streamUrl(songId: String): String =
        "http://127.0.0.1:${QQStreamGateway.port}/api/stream/$songId"

    fun prefetchUrl(songId: String): String = streamUrl(songId)

    fun serverBaseUrl(): String = serverPrefs.effectiveBaseUrl().trimEnd('/')

    // ── Library ───────────────────────────────────────────────────────────
    suspend fun getLibrarySongs(limit: Int = 25, offset: Int = 0) =
        direct.librarySongs().map {
            LibrarySongCollection(
                it.songs.filter { s -> VideoPlaybackPolicy.allows(s.id, s.type) }.map(::songFromDto),
                it.songsNewestFirst,
            )
        }

    suspend fun getLibraryAlbums(limit: Int = 25, offset: Int = 0) =
        direct.libraryAlbums().map { it.albums.filter { a -> VideoPlaybackPolicy.allows(a.id, a.type) }.map(::albumFromDto) }

    suspend fun getLibraryPlaylists(limit: Int = 25) =
        direct.libraryPlaylists().map { it.playlists }
            .map { lists -> lists.filter { !VideoPlaybackPolicy.isVideo(it.playlistType.orEmpty()) } }

    // ── Library writes ──────────────────────────────────────────────────
    /** Add a song (or album/video) to the user's library. A library id (starts with "i.")
     *  is already in the library, so it's a no-op success. */
    suspend fun addToLibrary(song: Song): Result<Unit> {
        if (song.id.startsWith("i.")) return Result.success(Unit)   // already a library item
        val type = if (song.isMusicVideo) "music-videos" else "songs"
        return direct.addToLibrary(song.id, type)
    }

    /** Append a song to one of the user's editable library playlists. */
    suspend fun addToPlaylist(playlistId: String, song: Song): Result<Unit> {
        val type = if (song.isMusicVideo) "music-videos" else "songs"
        return direct.addToPlaylist(playlistId, song.id, type)
    }

    suspend fun getPlaylistTracks(id: String) =
        direct.playlistTracks(id).map { it.songs.filter { s -> VideoPlaybackPolicy.allows(s.id, s.type) }.map(::songFromDto) }

    suspend fun getLibraryArtists(limit: Int = 25) =
        direct.libraryArtists().map { it.map(::artistFromDto) }

    // ── Auth ──────────────────────────────────────────────────────────────
    suspend fun getAuthStatus(): AuthStatus = qqAuth.status()

    /** The login page hands us a pasted QQ Music cookie; no token verification is possible. */
    suspend fun setMUT(token: String) {
        cookieStore.setCookie(token)
        // The rest of the app keys its "signed in" state off AccountPreferences, so mirror it there.
        mutPrefs.setMUT(token)
        mutPrefs.markVerified(token)
        appleAuth.recovered()
    }

    suspend fun syncMUTToServer(token: String) = Unit

    suspend fun clearMUT() {
        cookieStore.clear()
        mutPrefs.setMUT("")
    }

    // ── Mappers ───────────────────────────────────────────────────────────
    fun songFromDto(dto: SongDto) = Song(
        id             = dto.id,
        title          = dto.title,
        artistName     = dto.artistName,
        albumName      = dto.albumName,
        durationMs     = dto.durationMs,
        artworkUrl     = dto.artworkUrl,
        artworkBgColor = dto.artworkBgColor,
        previewUrl     = dto.previewHlsUrl ?: dto.previewUrl,
        hasLyrics      = dto.hasLyrics,
        trackNumber    = dto.trackNumber,
        genreNames     = dto.genreNames,
        artistId       = dto.artistId,
        albumId        = dto.albumId,
        type           = dto.type,
        dateAdded      = dto.dateAdded,
    )

    fun albumFromDto(dto: AlbumDto) = Album(
        additionOrder  = dto.additionOrder,
        dateAdded      = dto.dateAdded,
        id             = dto.id,
        title          = dto.title,
        artistName     = dto.artistName,
        artworkUrl     = dto.artworkUrl,
        type           = dto.type,
        artworkBgColor = dto.artworkBgColor,
        releaseDate    = dto.releaseDate,
        trackCount     = dto.trackCount,
        genreNames     = dto.genreNames,
        recordLabel    = dto.recordLabel,
        copyright      = dto.copyright,
        editorialNotes = dto.editorialNotes,
        motionUrl      = dto.motionUrl,
        tagline        = dto.tagline,
        wideArtworkUrl = dto.wideArtworkUrl,
    )

    /** Search playlists render as album-style cards; the "pl.*" id routes to the playlist screen. */
    private fun playlistToAlbum(dto: PlaylistDto) = Album(
        id             = dto.id,
        title          = dto.name,
        artistName     = dto.curatorName,
        artworkUrl     = dto.artworkUrl,
        artworkBgColor = dto.artworkBgColor,
    )

    fun artistFromDto(dto: ArtistDto) = Artist(
        id             = dto.id,
        name           = dto.name,
        artworkUrl     = dto.artworkUrl,
        genreNames     = dto.genreNames,
        editorialNotes = dto.editorialNotes,
    )
}

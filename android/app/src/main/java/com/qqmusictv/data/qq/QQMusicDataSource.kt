package com.qqmusictv.data.qq

import com.qqmusictv.data.network.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * QQ Music implementation of the catalog surface [com.qqmusictv.data.repository.MusicRepository]
 * consumes. Everything is mapped into the app's existing DTOs so the Compose UI stays untouched.
 */
@Singleton
class QQMusicDataSource @Inject constructor(
    private val api: QQMusicApi,
    private val cookies: QQCookieStore,
) {
    /** Kept for signature parity with the original source; QQ Music is region-fixed. */
    var storefront: String = "cn"

    suspend fun detectStorefront() = Unit

    // ── Mapping ───────────────────────────────────────────────────────────
    fun toSongDto(q: QQSong) = SongDto(
        id = q.mid,
        type = "songs",
        title = q.name,
        artistName = q.singerName,
        artistId = q.singerMid,
        albumId = q.albumMid,
        albumName = q.albumName,
        durationMs = q.durationSec * 1000L,
        artworkUrl = QQImages.album(q.albumMid),
        artworkBgColor = null,
        previewUrl = null,
        previewHlsUrl = null,
        hasLyrics = true,
    )

    fun toAlbumDto(q: QQAlbum) = AlbumDto(
        id = q.mid,
        title = q.name,
        artistName = q.singerName,
        artworkUrl = QQImages.album(q.mid),
        artworkBgColor = null,
        releaseDate = q.releaseDate,
        trackCount = q.trackCount,
        genreNames = listOfNotNull(q.genre),
        recordLabel = q.company,
        editorialNotes = q.description,
    )

    fun toArtistDto(q: QQArtist) = ArtistDto(
        id = q.mid,
        name = q.name,
        artworkUrl = QQImages.artist(q.mid),
    )

    fun toPlaylistDto(q: QQPlaylist, prefix: String) = PlaylistDto(
        id = prefix + q.id,
        name = q.name,
        curatorName = q.curator,
        artworkUrl = q.coverUrl,
        artworkBgColor = null,
        description = q.description,
    )

    /** Playlist cards render through [AlbumDto]; the id prefix routes them to the playlist screen. */
    fun toPlaylistAlbum(q: QQPlaylist, prefix: String = "pl.") = AlbumDto(
        id = prefix + q.id,
        title = q.name,
        artistName = q.curator,
        artworkUrl = q.coverUrl,
        artworkBgColor = null,
        releaseDate = null,
        editorialNotes = q.description,
    )

    fun toChartAlbum(id: String, name: String, cover: String?) = AlbumDto(
        id = "pl.chart.$id",
        title = name,
        artistName = "QQ音乐排行榜",
        artworkUrl = cover,
        artworkBgColor = null,
        releaseDate = null,
    )

    // ── Catalog ───────────────────────────────────────────────────────────
    suspend fun search(term: String, limit: Int = 20): Result<SearchResponse> = runCatching {
        val r = api.search(term, limit)
        SearchResponse(
            songs = r.songs.map(::toSongDto),
            albums = r.albums.map(::toAlbumDto),
            artists = r.artists.map(::toArtistDto),
            playlists = r.playlists.map { toPlaylistDto(it, "pl.") },
            curators = emptyList(),
        )
    }

    suspend fun album(id: String): Result<AlbumDto> = runCatching {
        toAlbumDto(api.albumDetail(id).info)
    }

    suspend fun albumTracks(id: String): Result<List<SongDto>> = runCatching {
        api.albumDetail(id).songs.map(::toSongDto)
    }

    suspend fun song(id: String): Result<SongDto> = runCatching {
        api.songDetail(id)?.let(::toSongDto) ?: error("song not found")
    }

    suspend fun artist(id: String): Result<ArtistDto> = runCatching {
        toArtistDto(api.singerDetail(id).info)
    }

    suspend fun artistAlbums(id: String): Result<List<AlbumDto>> = runCatching {
        api.singerAlbums(id).map(::toAlbumDto)
    }

    suspend fun artistFull(id: String): Result<ArtistFullDto> = runCatching {
        val detail = api.singerDetail(id)
        val albums = api.singerAlbums(id, 20)
        val topSongs = api.singerSongs(id, detail.info.name)
        ArtistFullDto(
            id = id,
            name = detail.info.name,
            artworkUrl = QQImages.artist(id),
            genreNames = emptyList(),
            editorialNotes = detail.description,
            topSongs = topSongs.map(::toSongDto),
            musicVideos = emptyList(),
            latestRelease = albums.firstOrNull()?.let(::toAlbumDto),
            albums = albums.map(::toAlbumDto),
            featuredAlbums = emptyList(),
            similarArtists = emptyList(),
        )
    }

    suspend fun relatedAlbums(id: String): Result<List<AlbumDto>> = Result.success(emptyList())

    suspend fun relatedSongs(seedId: String): Result<List<SongDto>> = Result.success(emptyList())

    // ── Home / Browse ─────────────────────────────────────────────────────
    suspend fun home(hasMut: Boolean): List<Pair<String, List<AlbumDto>>> = runCatching {
        buildList {
            add("推荐歌单" to api.recommendPlaylists(20).map { toPlaylistAlbum(it) })
            add("华语流行" to api.recommendPlaylists(20, "1").map { toPlaylistAlbum(it) })
            add("欧美热歌" to api.recommendPlaylists(20, "2").map { toPlaylistAlbum(it) })
        }
    }.getOrDefault(emptyList())

    suspend fun browse(): List<HomeSection> = runCatching {
        buildList {
            val charts = CHART_IDS.mapNotNull { (id, label) ->
                runCatching {
                    val chart = api.chart(id, 30)
                    val cover = chart.cover ?: chart.songs.firstOrNull()?.albumMid?.let { QQImages.album(it) }
                    toChartAlbum(id, chart.name.ifEmpty { label }, cover)
                }.getOrNull()
            }
            if (charts.isNotEmpty()) add(HomeSection("排行榜", charts))
            add(HomeSection("推荐歌单", api.recommendPlaylists(20).map { toPlaylistAlbum(it) }))
            GENRES.take(6).forEach { (id, name) ->
                val albums = api.recommendPlaylists(20, id).map { toPlaylistAlbum(it) }
                if (albums.isNotEmpty()) add(HomeSection(name, albums))
            }
        }
    }.getOrDefault(emptyList())

    suspend fun genreContent(id: String): List<Pair<String, List<AlbumDto>>> = runCatching {
        val name = genreName(id)
        listOf(name to api.recommendPlaylists(30, id).map { toPlaylistAlbum(it) })
    }.getOrDefault(emptyList())

    suspend fun getCurator(id: String, isEditorial: Boolean): MultiRoomDto = runCatching {
        val categoryId = id.substringAfterLast('-')
        val name = genreName(categoryId)
        MultiRoomDto(
            id = id,
            title = name,
            sections = listOf(
                HomeSection(name, api.recommendPlaylists(30, categoryId).map { toPlaylistAlbum(it) })
            ),
        )
    }.getOrDefault(MultiRoomDto(id = id))

    suspend fun getRoom(id: String): MultiRoomDto = MultiRoomDto(id = id)

    suspend fun getMultiRoom(id: String): MultiRoomDto = MultiRoomDto(id = id)

    suspend fun getGrouping(id: String): MultiRoomDto = MultiRoomDto(id = id)

    private val genreCoverCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    private suspend fun genreCover(id: String): String? =
        genreCoverCache[id] ?: runCatching { api.recommendPlaylists(1, id).firstOrNull()?.coverUrl }
            .getOrNull()?.also { genreCoverCache[id] = it }

    suspend fun getCategories(): List<CategorySectionDto> = coroutineScope {
        val covers = GENRES.map { (id, _) -> async { genreCover(id) } }.map { it.await() }
        listOf(
            CategorySectionDto(
                title = "分类",
                items = GENRES.mapIndexed { i, (id, name) ->
                    CuratorDto(id = "c-$id", name = name, kind = "curator", artworkUrl = covers[i])
                },
            )
        )
    }

    suspend fun genres(): Result<List<GenreDto>> = Result.success(
        GENRES.map { (id, name) -> GenreDto(id, name) }
    )

    // ── Library ───────────────────────────────────────────────────────────
    suspend fun librarySongs(): Result<LibrarySongsResponse> = runCatching {
        LibrarySongsResponse(api.favoriteSongs(cookies.uin()).map(::toSongDto), songsNewestFirst = false)
    }

    suspend fun libraryAlbums(): Result<LibraryAlbumsResponse> = Result.success(LibraryAlbumsResponse())

    suspend fun libraryPlaylists(): Result<LibraryPlaylistsResponse> = runCatching {
        LibraryPlaylistsResponse(api.userPlaylists(cookies.uin()).map { toPlaylistDto(it, "p.") })
    }

    suspend fun libraryArtists(): Result<List<ArtistDto>> = Result.success(emptyList())

    suspend fun playlistTracks(id: String): Result<LibrarySongsResponse> = runCatching {
        val raw = id.removePrefix("pl.").removePrefix("p.")
        val songs = when {
            raw.startsWith("chart.") -> api.chart(raw.removePrefix("chart.")).songs
            else -> api.playlistDetail(raw).songs
        }
        LibrarySongsResponse(songs.map(::toSongDto))
    }

    suspend fun addToLibrary(id: String, type: String): Result<Unit> = Result.success(Unit)

    suspend fun addToPlaylist(playlistId: String, songId: String, type: String = "songs"): Result<Unit> =
        Result.success(Unit)

    // ── Extras with no QQ equivalent ──────────────────────────────────────
    suspend fun recommendations(): Result<List<Pair<String, List<AlbumDto>>>> = runCatching { home(false) }

    suspend fun charts(genre: String? = null): Result<List<Pair<String, List<AlbumDto>>>> = runCatching {
        CHART_IDS.mapNotNull { (id, label) ->
            runCatching {
                val chart = api.chart(id, 30)
                val cover = chart.cover ?: chart.songs.firstOrNull()?.albumMid?.let { QQImages.album(it) }
                chart.name.ifEmpty { label } to listOf(toChartAlbum(id, chart.name.ifEmpty { label }, cover))
            }.getOrNull()
        }
    }

    suspend fun stationTracks(id: String): Result<List<SongDto>> = Result.success(emptyList())
    suspend fun stationStream(id: String): Result<StationStreamResponse> = Result.success(StationStreamResponse(isLive = false))
    suspend fun genreStationSongs(genreId: String): Result<List<SongDto>> = Result.success(emptyList())
    suspend fun probeStation(id: String): Result<String> = Result.success("")
    suspend fun motion(songId: String): Result<String?> = Result.success(null)
    suspend fun albumMotion(albumId: String): Result<String?> = Result.success(null)
    suspend fun playlistMotion(playlistId: String): Result<String?> = Result.success(null)

    suspend fun accountStatus(): Result<AccountStatusResponse> = Result.success(
        AccountStatusResponse(
            ok = true,
            services = listOf(AccountServiceStatus("QQ音乐", "可用")),
            checkedAt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
                .format(java.util.Date()),
        )
    )

    private fun genreName(id: String): String =
        GENRES.firstOrNull { it.first == id }?.second ?: "推荐歌单"

    companion object {
        /** QQ Music playlist category ids (from fcg_get_diss_tag_conf). */
        val GENRES = listOf(
            "165" to "国语",
            "167" to "英语",
            "169" to "日语",
            "168" to "韩语",
            "166" to "粤语",
            "11" to "摇滚",
            "24" to "电子",
            "28" to "民谣",
            "153" to "嘻哈",
            "21" to "爵士",
            "27" to "古典",
            "133" to "影视原声",
        )

        /** toplist ids → fallback label */
        val CHART_IDS = listOf(
            "26" to "热歌榜",
            "4" to "飙升榜",
            "27" to "新歌榜",
            "62" to "音乐人榜",
        )
    }
}

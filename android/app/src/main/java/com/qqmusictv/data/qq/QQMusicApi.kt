package com.qqmusictv.data.qq

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class QQPlaylistDetail(val info: QQPlaylist, val songs: List<QQSong>)
data class QQAlbumDetail(val info: QQAlbum, val songs: List<QQSong>)
data class QQArtistDetail(val info: QQArtist, val description: String?)
data class QQChart(val name: String, val songs: List<QQSong>, val cover: String? = null)

/** Signed-in account identity returned by `music.UserInfo.userInfoServer`. */
data class QQUserInfo(val nick: String, val avatarUrl: String?)

/**
 * Thin client over the public y.qq.com web endpoints. Everything except stream URL resolution
 * works anonymously; [streamUrl] needs a logged-in cookie (see [QQCookieStore]).
 */
@Singleton
class QQMusicApi @Inject constructor(private val cookies: QQCookieStore) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val userAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0 Safari/537.36"

    // ── Search ────────────────────────────────────────────────────────────
    suspend fun search(term: String, limit: Int = 20): QQSearchResult = withContext(Dispatchers.IO) {
        fun query(type: Int): JSONArray {
            val body = JSONObject()
                .put(
                    "req_1",
                    JSONObject()
                        .put("method", "DoSearchForQQMusicDesktop")
                        .put("module", "music.search.SearchCgiService")
                        .put("param", JSONObject()
                            .put("num_per_page", limit)
                            .put("page_num", 0)
                            .put("query", term)
                            .put("search_type", type))
                )
                .put("comm", comm(ct = 11))
            val json = runCatching { postJson(MUSICU, body.toString()) }.getOrNull() ?: return JSONArray()
            return json.optJSONObject("req_1")
                ?.optJSONObject("data")
                ?.optJSONObject("body")
                ?.optJSONObject(sectionFor(type))
                ?.optJSONArray("list")
                ?: JSONArray()
        }
        return@withContext QQSearchResult(
            songs = query(0).mapObjects(::parseSong),
            artists = query(1).mapObjects(::parseArtist),
            albums = query(2).mapObjects(::parseAlbumFromSearch),
            playlists = query(3).mapObjects(::parsePlaylistFromSearch),
        )
    }

    private fun sectionFor(type: Int) = when (type) {
        1 -> "singer"
        2 -> "album"
        3 -> "songlist"
        else -> "song"
    }

    // ── Catalog ───────────────────────────────────────────────────────────
    suspend fun albumDetail(albumMid: String): QQAlbumDetail = withContext(Dispatchers.IO) {
        val url = "$C_Y_QQ/v8/fcg-bin/fcg_v8_album_info_cp.fcg?albummid=${enc(albumMid)}&format=json&g_tk=5381"
        val data = runCatching { getJson(url) }.getOrNull()?.optJSONObject("data")
        val songs = data?.optJSONArray("list").orEmpty().mapObjects(::parseSongFromLegacy)
        QQAlbumDetail(
            info = QQAlbum(
                mid = albumMid,
                name = data?.optString("name").orEmpty().ifEmpty { "未知专辑" },
                singerName = data?.optString("singername").orEmpty().ifEmpty { "未知歌手" },
                singerMid = data?.optString("singermid")?.takeIf { it.isNotEmpty() },
                releaseDate = data?.optString("aDate")?.takeIf { it.isNotEmpty() },
                trackCount = data?.optInt("total_song_num", songs.size) ?: songs.size,
                genre = data?.optString("genre")?.takeIf { it.isNotEmpty() },
                company = data?.optString("company")?.takeIf { it.isNotEmpty() },
                description = data?.optString("desc")?.takeIf { it.isNotEmpty() },
            ),
            songs = songs,
        )
    }

    suspend fun singerDetail(singerMid: String): QQArtistDetail = withContext(Dispatchers.IO) {
        val url = "$C_Y_QQ/v8/fcg-bin/fcg_v8_singer_detail_cp.fcg?singermid=${enc(singerMid)}&format=json&g_tk=5381"
        val json = runCatching { getJson(url) }.getOrNull()
        val info = json?.optJSONObject("getSingerInfo")
        QQArtistDetail(
            info = QQArtist(
                mid = singerMid,
                name = info?.optString("Fsinger_name").orEmpty().ifEmpty { "未知歌手" },
                songCount = json?.optInt("total_song", 0) ?: 0,
                albumCount = json?.optInt("total_album", 0) ?: 0,
            ),
            description = json?.optString("singerBrief")?.takeIf { it.isNotEmpty() },
        )
    }

    suspend fun singerAlbums(singerMid: String, limit: Int = 30): List<QQAlbum> = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put(
                "req_0",
                JSONObject()
                    .put("module", "music.musichallAlbum.AlbumListServer")
                    .put("method", "GetAlbumList")
                    .put("param", JSONObject()
                        .put("singerMid", singerMid)
                        .put("begin", 0)
                        .put("num", limit)
                        .put("order", 0))
            )
            .put("comm", comm())
        val json = runCatching { postJson(MUSICU, body.toString()) }.getOrNull()
        json?.optJSONObject("req_0")
            ?.optJSONObject("data")
            ?.optJSONArray("albumList")
            .orEmpty()
            .mapObjects { o ->
                QQAlbum(
                    mid = o.optString("albumMid"),
                    name = o.optString("albumName").ifEmpty { "未知专辑" },
                    singerName = o.optString("singerName").ifEmpty { "未知歌手" },
                    singerMid = singerMid,
                    releaseDate = o.optString("publishDate")?.takeIf { it.isNotEmpty() },
                    trackCount = o.optInt("totalNum", 0),
                    genre = o.optString("albumType")?.takeIf { it.isNotEmpty() },
                    company = null,
                    description = null,
                )
            }
    }

    /** Prefers the singer detail's hot-song list; falls back to a name search filtered by mid. */
    suspend fun singerSongs(singerMid: String, singerName: String, limit: Int = 50): List<QQSong> =
        withContext(Dispatchers.IO) {
            val url = "$C_Y_QQ/v8/fcg-bin/fcg_v8_singer_detail_cp.fcg" +
                "?singermid=${enc(singerMid)}&format=json&g_tk=5381"
            val hot = runCatching { getJson(url) }.getOrNull()
                ?.optJSONArray("getSongInfo")
                .orEmpty()
                .mapObjects(::parseSongFromLegacy)
            if (hot.isNotEmpty()) return@withContext hot.take(limit)

            val result = search(singerName, betterOf(limit, 60))
            val exact = result.songs.filter { it.singerMid == singerMid }
            (if (exact.size >= 5) exact else result.songs).take(limit)
        }

    suspend fun songDetail(songMid: String): QQSong? = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put(
                "req_0",
                JSONObject()
                    .put("module", "music.pf_song_detail_svr")
                    .put("method", "get_song_detail")
                    .put("param", JSONObject().put("song_mid", songMid))
            )
            .put("comm", comm())
        val json = runCatching { postJson(MUSICU, body.toString()) }.getOrNull()
        json?.optJSONObject("req_0")
            ?.optJSONObject("data")
            ?.optJSONObject("track_info")
            ?.let(::parseSong)
    }

    // ── Playlists / charts ────────────────────────────────────────────────
    suspend fun playlistDetail(disstid: String): QQPlaylistDetail = withContext(Dispatchers.IO) {
        // The qzone endpoint silently returns an empty cdlist for plaza/recommended lists,
        // so the module API comes first and the legacy one is only a fallback.
        val musicu = runCatching { playlistDetailFromMusicu(disstid) }.getOrNull()
        if (musicu != null && musicu.songs.isNotEmpty()) return@withContext musicu

        val url = "$C_Y_QQ/qzone/fcg-bin/fcg_ucc_getcdinfo_byids_cp.fcg" +
            "?type=1&json=1&utf8=1&onlysong=0&disstid=${enc(disstid)}&format=json&g_tk=5381"
        val cd = runCatching { getJson(url) }.getOrNull()?.optJSONArray("cdlist")?.optJSONObject(0)
        val songs = cd?.optJSONArray("songlist").orEmpty().mapObjects(::parseSongFromLegacy)
        QQPlaylistDetail(
            info = QQPlaylist(
                id = disstid,
                name = cd?.optString("dissname").orEmpty().ifEmpty { "歌单" },
                curator = cd?.optString("nickname").orEmpty().ifEmpty { "QQ音乐" },
                coverUrl = cd?.optString("logo")?.takeIf { it.isNotEmpty() },
                description = cd?.optString("desc")?.takeIf { it.isNotEmpty() },
                songCount = cd?.optInt("songnum", songs.size) ?: songs.size,
            ),
            songs = songs,
        )
    }

    private fun playlistDetailFromMusicu(disstid: String): QQPlaylistDetail? {
        val body = JSONObject()
            .put(
                "req_0",
                JSONObject()
                    .put("module", "music.srfDissInfo.DissInfo")
                    .put("method", "CgiGetDiss")
                    .put("param", JSONObject()
                        .put("disstid", disstid.toLongOrNull() ?: 0L)
                        .put("dirid", 0)
                        .put("tag", 1)
                        .put("song_begin", 0)
                        .put("song_num", 100)
                        .put("userinfo", 1))
            )
            .put("comm", comm())
        val data = postJson(MUSICU, body.toString()).optJSONObject("req_0")?.optJSONObject("data")
            ?: return null
        val dir = data.optJSONObject("dirinfo")
        val songs = data.optJSONArray("songlist").orEmpty().mapObjects(::parseSong)
        return QQPlaylistDetail(
            info = QQPlaylist(
                id = disstid,
                name = dir?.optString("title").orEmpty().ifEmpty { "歌单" },
                curator = dir?.optString("host_nick").orEmpty().ifEmpty { "QQ音乐" },
                coverUrl = dir?.optString("picurl")?.takeIf { it.isNotEmpty() },
                description = dir?.optString("desc")?.takeIf { it.isNotEmpty() },
                songCount = dir?.optInt("songnum", songs.size) ?: songs.size,
            ),
            songs = songs,
        )
    }

    suspend fun recommendPlaylists(limit: Int = 20, categoryId: String = "10000000"): List<QQPlaylist> =
        withContext(Dispatchers.IO) {
        val url = "$C_Y_QQ/splcloud/fcgi-bin/fcg_get_diss_by_tag.fcg" +
            "?categoryId=${enc(categoryId)}&sortId=5&sin=0&ein=${limit.coerceIn(1, 50)}&format=json&g_tk=5381"
        val json = runCatching { getJson(url) }.getOrNull()
        json?.optJSONObject("data")
            ?.optJSONArray("list")
            .orEmpty()
            .mapObjects { o ->
                QQPlaylist(
                    id = o.optString("dissid"),
                    name = o.optString("dissname").ifEmpty { "歌单" },
                    curator = o.optJSONObject("creator")?.optString("name").orEmpty().ifEmpty { "QQ音乐" },
                    coverUrl = o.optString("imgurl")?.takeIf { it.isNotEmpty() },
                    description = o.optString("introduction")?.takeIf { it.isNotEmpty() },
                    songCount = o.optInt("song_count", 0),
                )
            }
    }

    suspend fun chart(topid: String, limit: Int = 30): QQChart = withContext(Dispatchers.IO) {
        val url = "$C_Y_QQ/v8/fcg-bin/fcg_v8_toplist_cp.fcg" +
            "?topid=${enc(topid)}&page=detail&type=top&format=json&g_tk=5381&tpl=3&date=&song_begin=0&song_num=$limit"
        val json = runCatching { getJson(url) }.getOrNull()
        val topinfo = json?.optJSONObject("topinfo")
        val name = topinfo?.optString("ListName")?.takeIf { it.isNotEmpty() }
            ?: "排行榜"
        val cover = topinfo?.optString("pic")?.takeIf { it.isNotEmpty() }
        val songs = json?.optJSONArray("songlist").orEmpty().mapObjects { item ->
            item.optJSONObject("data")?.let(::parseSongFromLegacy)
        }
        QQChart(name, songs.take(limit), cover)
    }

    // ── Library (needs cookie) ────────────────────────────────────────────
    suspend fun userPlaylists(uin: String, limit: Int = 100): List<QQPlaylist> = withContext(Dispatchers.IO) {
        if (uin.isEmpty()) return@withContext emptyList()
        val url = "$C_Y_QQ/rsc/fcgi-bin/fcg_user_created_diss" +
            "?hostuin=${enc(uin)}&sin=0&size=$limit&format=json&g_tk=5381"
        val json = runCatching { getJson(url) }.getOrNull()
        json?.optJSONObject("data")
            ?.optJSONArray("disslist")
            .orEmpty()
            .mapObjects { o ->
                QQPlaylist(
                    id = o.optString("tid").ifEmpty { o.optString("dissid") },
                    name = o.optString("diss_name").ifEmpty { "歌单" },
                    curator = "我的歌单",
                    coverUrl = o.optString("diss_cover")?.takeIf { it.isNotEmpty() },
                    description = null,
                    songCount = o.optInt("song_cnt", 0),
                )
            }
    }

    suspend fun favoriteSongs(uin: String, limit: Int = 100): List<QQSong> = withContext(Dispatchers.IO) {
        if (uin.isEmpty()) return@withContext emptyList()
        val url = "$C_Y_QQ/fav/fcgi-bin/fcg_get_profile_order_asset.fcg" +
            "?ct=20&cid=205360956&userid=${enc(uin)}&reqtype=3&sin=0&size=$limit&format=json&g_tk=5381"
        val json = runCatching { getJson(url) }.getOrNull()
        json?.optJSONObject("data")
            ?.optJSONArray("songlist")
            .orEmpty()
            .mapObjects(::parseSongFromLegacy)
    }

    // ── Lyrics ────────────────────────────────────────────────────────────
    suspend fun lyric(songMid: String): String? = withContext(Dispatchers.IO) {
        val url = "$C_Y_QQ/lyric/fcgi-bin/fcg_query_lyric_new.fcg" +
            "?songmid=${enc(songMid)}&format=json&nobase64=1&g_tk=5381&loginUin=0&hostUin=0" +
            "&inCharset=utf8&outCharset=utf-8&platform=yqq.json&needNewCode=0"
        val json = runCatching { getJson(url) }.getOrNull() ?: return@withContext null
        json.optString("lyric")?.takeIf { it.isNotBlank() }
    }

    // ── Stream ────────────────────────────────────────────────────────────
    /**
     * Resolves a fresh playable URL, preferring lossless.
     *
     * Without a `filename` QQ hands back the account's default quality — in practice 128k AAC.
     * That same response carries the "file mid", which differs from songmid and is what the
     * quality-specific filenames are built from (`F000<fileMid>.flac`). Ask for Hi-Res FLAC
     * first, then SQ FLAC, and keep the default URL as fallback. Null when not logged in,
     * VIP-locked or region blocked.
     */
    suspend fun streamUrl(songMid: String): String? = withContext(Dispatchers.IO) {
        val fallback = vkey(songMid, null) ?: return@withContext null
        val dot = fallback.name.lastIndexOf('.')
        val fileMid = if (dot > 9) fallback.name.substring(4, dot) else ""
        if (fileMid.isNotEmpty()) {
            for ((prefix, ext) in listOf("F000" to "flac", "M800" to "flac")) {
                vkey(songMid, "$prefix$fileMid.$ext")?.let { return@withContext it.url }
            }
        }
        fallback.url
    }

    private class VKey(val name: String, val url: String)

    private suspend fun vkey(songMid: String, filename: String?): VKey? {
        val param = JSONObject()
            .put("guid", "10000")
            .put("songmid", JSONArray().put(songMid))
            .put("songtype", JSONArray().put(0))
            .put("uin", cookies.uin().ifEmpty { "0" })
            .put("loginflag", 1)
            .put("platform", "20")
        if (filename != null) param.put("filename", JSONArray().put(filename))
        val body = JSONObject()
            .put("req_0", JSONObject()
                .put("module", "vkey.GetVkeyServer")
                .put("method", "CgiGetVkey")
                .put("param", param))
            .put("comm", comm())
        val json = runCatching { postJson(MUSICU, body.toString()) }.getOrNull() ?: return null
        val data = json.optJSONObject("req_0")?.optJSONObject("data") ?: return null
        val info = data.optJSONArray("midurlinfo")?.optJSONObject(0) ?: return null
        val purl = info.optString("purl").orEmpty()
        if (purl.isEmpty()) return null
        val sip = data.optJSONArray("sip")?.optString(0).orEmpty()
        return VKey(info.optString("filename").orEmpty(), sip + purl)
    }

    // ── Account ───────────────────────────────────────────────────────────
    /** Nickname + avatar of the signed-in account. */
    suspend fun loginUserInfo(): QQUserInfo? = withContext(Dispatchers.IO) {
        val uin = cookies.uin()
        if (uin.isEmpty()) return@withContext null
        val body = JSONObject()
            .put("comm", comm())
            .put("req", JSONObject()
                .put("module", "music.UserInfo.userInfoServer")
                .put("method", "GetLoginUserInfo")
                .put("param", JSONObject().put("uin", uin)))
        val json = runCatching { postJson(MUSICU, body.toString()) }.getOrNull()
            ?: return@withContext null
        val data = json.optJSONObject("req")?.optJSONObject("data") ?: return@withContext null
        val creator = data.optJSONObject("creator") ?: data
        val avatar = listOf("avatarUrl", "headpic", "headPic", "avatar")
            .firstNotNullOfOrNull { key -> creator.optString(key).takeIf { it.startsWith("http") } }
        val nick = listOf("nick", "nickname", "hostname")
            .firstNotNullOfOrNull { key -> creator.optString(key).takeIf { it.isNotBlank() } }
            .orEmpty()
        QQUserInfo(nick = nick, avatarUrl = avatar?.replaceFirst("http://", "https://"))
    }

    // ── HTTP ──────────────────────────────────────────────────────────────
    private fun comm(ct: Int = 24): JSONObject = JSONObject()
        .put("uin", cookies.uin().ifEmpty { "0" })
        .put("format", "json")
        .put("ct", ct)
        .put("cv", 0)

    private fun getJson(url: String): JSONObject = JSONObject(getText(url))

    private fun getText(url: String): String {
        val builder = Request.Builder().url(url).get()
            .header("Referer", "https://y.qq.com/")
            .header("User-Agent", userAgent)
        cookies.cookieHeader().takeIf { it.isNotEmpty() }?.let { builder.header("Cookie", it) }
        client.newCall(builder.build()).execute().use { resp ->
            val bytes = resp.body?.bytes() ?: ByteArray(0)
            return decode(bytes)
        }
    }

    private fun postJson(url: String, payload: String): JSONObject {
        val builder = Request.Builder().url(url).post(payload.toRequestBody(jsonMedia))
            .header("Referer", "https://y.qq.com/")
            .header("User-Agent", userAgent)
        cookies.cookieHeader().takeIf { it.isNotEmpty() }?.let { builder.header("Cookie", it) }
        client.newCall(builder.build()).execute().use { resp ->
            val bytes = resp.body?.bytes() ?: ByteArray(0)
            return JSONObject(decode(bytes))
        }
    }

    /** Some legacy endpoints answer in GBK; decode UTF-8 first and fall back when it mojibakes. */
    private fun decode(bytes: ByteArray): String {
        val utf8 = String(bytes, Charsets.UTF_8)
        return if (utf8.contains('\uFFFD')) String(bytes, charset("GB18030")) else utf8
    }

    // ── Parsers ───────────────────────────────────────────────────────────
    private fun parseSong(o: JSONObject): QQSong? {
        val mid = o.optString("mid").ifEmpty { o.optString("songmid") }
        if (mid.isEmpty()) return null
        val name = o.optString("name").ifEmpty { o.optString("songname") }.ifEmpty { o.optString("title") }
        val singers = o.optJSONArray("singer")
        val singerName = buildString {
            if (singers != null) for (i in 0 until singers.length()) {
                val n = singers.optJSONObject(i)?.optString("name").orEmpty()
                if (n.isNotEmpty()) { if (isNotEmpty()) append(" / "); append(n) }
            }
        }.ifEmpty { "未知歌手" }
        val singerMid = singers?.optJSONObject(0)?.optString("mid")?.takeIf { it.isNotEmpty() }
        val album = o.optJSONObject("album")
        val albumMid = album?.optString("mid")?.takeIf { it.isNotEmpty() }
            ?: o.optString("albummid").takeIf { it.isNotEmpty() }
        val albumName = album?.optString("name")?.takeIf { it.isNotEmpty() }
            ?: o.optString("albumname").takeIf { it.isNotEmpty() }
            ?: "未知专辑"
        return QQSong(
            mid = mid,
            name = name.ifEmpty { "未知歌曲" },
            singerName = singerName,
            singerMid = singerMid,
            albumMid = albumMid,
            albumName = albumName,
            durationSec = o.optInt("interval", 0),
            payPlay = o.optJSONObject("pay")?.optInt("pay_play", 0) ?: 0,
        )
    }

    /** Album/playlist/chart payloads keep top-level ids instead of nested objects. */
    private fun parseSongFromLegacy(o: JSONObject): QQSong? {
        val mid = o.optString("songmid").ifEmpty { o.optString("mid") }
        if (mid.isEmpty()) return null
        val singers = o.optJSONArray("singer")
        val singerName = buildString {
            if (singers != null) for (i in 0 until singers.length()) {
                val n = singers.optJSONObject(i)?.optString("name").orEmpty()
                if (n.isNotEmpty()) { if (isNotEmpty()) append(" / "); append(n) }
            }
        }.ifEmpty { "未知歌手" }
        return QQSong(
            mid = mid,
            name = o.optString("songname").ifEmpty { o.optString("name") }.ifEmpty { "未知歌曲" },
            singerName = singerName,
            singerMid = singers?.optJSONObject(0)?.optString("mid")?.takeIf { it.isNotEmpty() },
            albumMid = o.optString("albummid").takeIf { it.isNotEmpty() },
            albumName = o.optString("albumname").takeIf { it.isNotEmpty() }.orEmpty().ifEmpty { "未知专辑" },
            durationSec = o.optInt("interval", 0),
            payPlay = o.optJSONObject("pay")?.optInt("pay_play", 0) ?: 0,
        )
    }

    private fun parseAlbumFromSearch(o: JSONObject): QQAlbum = QQAlbum(
        mid = o.optString("albumMID"),
        name = o.optString("albumName").ifEmpty { "未知专辑" },
        singerName = o.optString("singerName").ifEmpty { "未知歌手" },
        singerMid = o.optString("singerMID")?.takeIf { it.isNotEmpty() },
        releaseDate = o.optString("publicTime")?.takeIf { it.isNotEmpty() },
        trackCount = o.optInt("song_count", 0),
        genre = null,
        company = null,
        description = null,
    )

    private fun parseArtist(o: JSONObject): QQArtist = QQArtist(
        mid = o.optString("singerMID"),
        name = o.optString("singerName").ifEmpty { "未知歌手" },
        songCount = o.optInt("songNum", 0),
        albumCount = o.optInt("albumNum", 0),
    )

    private fun parsePlaylistFromSearch(o: JSONObject): QQPlaylist = QQPlaylist(
        id = o.optString("dissid"),
        name = o.optString("dissname").ifEmpty { "歌单" },
        curator = o.optJSONObject("creator")?.optString("name").orEmpty().ifEmpty { "QQ音乐" },
        coverUrl = o.optString("imgurl")?.takeIf { it.isNotEmpty() },
        description = o.optString("introduction")?.takeIf { it.isNotEmpty() },
        songCount = o.optInt("song_count", 0),
    )

    private fun JSONArray?.orEmpty(): JSONArray = this ?: JSONArray()

    private inline fun <T> JSONArray?.mapObjects(block: (JSONObject) -> T?): List<T> {
        if (this == null) return emptyList()
        val out = ArrayList<T>(length())
        for (i in 0 until length()) {
            val item = optJSONObject(i) ?: continue
            block(item)?.let(out::add)
        }
        return out
    }

    private fun betterOf(a: Int, b: Int) = maxOf(a, b)

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    private companion object {
        const val C_Y_QQ = "https://c.y.qq.com"
        const val MUSICU = "https://u.y.qq.com/cgi-bin/musicu.fcg"
    }
}

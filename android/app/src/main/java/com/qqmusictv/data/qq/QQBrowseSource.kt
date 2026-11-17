package com.qqmusictv.data.qq

import com.qqmusictv.data.network.AlbumDto
import com.qqmusictv.data.network.HomeSection
import javax.inject.Inject
import javax.inject.Singleton

/** Browse-tab shelves, backed by the QQ catalog. */
@Singleton
class QQBrowseSource @Inject constructor(private val source: QQMusicDataSource) {

    suspend fun home(hasMut: Boolean): List<Pair<String, List<AlbumDto>>> = source.home(hasMut)

    suspend fun browse(): List<HomeSection> = source.browse()

    suspend fun genreContent(id: String): List<Pair<String, List<AlbumDto>>> = source.genreContent(id)
}

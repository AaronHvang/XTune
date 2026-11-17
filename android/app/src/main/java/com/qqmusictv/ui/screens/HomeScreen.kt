package com.qqmusictv.ui.screens

import com.qqmusictv.i18n.tr
import com.qqmusictv.R

import com.qqmusictv.ui.components.pageHorizontalInset

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qqmusictv.ui.viewmodel.PlayerViewModel
import com.qqmusictv.ui.navigation.ReturnLazyRow as LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.tv.material3.*
import com.qqmusictv.data.model.Album
import com.qqmusictv.ui.components.AlbumCard
import com.qqmusictv.ui.viewmodel.HomeSection

/** Consume once after real content is mounted; normal tab returns keep their saved position. */
@Composable
internal fun RestorePostLoginHomeTop(
    listState: LazyListState,
    pending: Boolean,
    onRestored: () -> Unit,
) {
    val restored by rememberUpdatedState(onRestored)
    LaunchedEffect(pending) {
        if (pending) {
            withFrameNanos { }
            listState.scrollToItem(0, 0)
            restored()
        }
    }
}

/**
 * One home feed section. Rendered inside the Browse page's single LazyColumn so the
 * recommendation shelves and the browse shelves share one scroll container.
 */
@Composable
internal fun HomeSectionRow(
    section: HomeSection,
    playerVm: PlayerViewModel,
    onAlbumClick: (String) -> Unit,
    onPlaylistClick: (id: String, name: String, artworkUrl: String) -> Unit,
    onCategoryClick: (String) -> Unit,
    motionEnabled: Boolean = true,
) {
    val openCard: (Album) -> Unit = { album ->
        val isPlaylist = album.id.startsWith("pl.") || album.id.startsWith("p.")
        val isStation = album.id.startsWith("ra.")
        val isCategory = album.id.startsWith("ac-") || album.id.startsWith("c-") || album.id.startsWith("mr-")
        when {
            isCategory -> onCategoryClick(album.id)
            isStation  -> playerVm.playStation(album.id, album.artworkUrl(600), album.title)
            isPlaylist -> onPlaylistClick(album.id, album.title, album.artworkUrl(500) ?: "")
            else       -> onAlbumClick(album.id)
        }
    }

    // "为你精选" hero — big square lockups with the caption below.
    if (section.style == "picks") {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(homeSectionTitle(section.title), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = com.qqmusictv.ui.theme.AppPalette.primary,
                modifier = Modifier.padding(start = pageHorizontalInset, end = pageHorizontalInset, bottom = 14.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = pageHorizontalInset, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(section.albums, key = { it.id }, contentType = { "picks" }) { album ->
                    AlbumCard(
                        album = album, size = 210,
                        onClick = { openCard(album) },
                        onLongClick = { if (album.id.startsWith("pl.") || album.id.startsWith("p.")) playerVm.shufflePlayPlaylist(album.id) },
                        motionEnabled = motionEnabled,
                    )
                }
            }
        }
        return
    }

    // Spotlight rows ("为你打造的播放列表") render as big gradient cards.
    if (section.style == "gradient") {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(homeSectionTitle(section.title), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = com.qqmusictv.ui.theme.AppPalette.primary,
                modifier = Modifier.padding(start = pageHorizontalInset, end = pageHorizontalInset, bottom = 14.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = pageHorizontalInset, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(section.albums, key = { it.id }, contentType = { "gradient" }) { album ->
                    com.qqmusictv.ui.components.GradientCard(
                        album = album, width = 250,
                        onClick = { openCard(album) },
                        onLongClick = { if (album.id.startsWith("pl.") || album.id.startsWith("p.")) playerVm.shufflePlayPlaylist(album.id) },
                        motionEnabled = motionEnabled,
                    )
                }
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text       = homeSectionTitle(section.title),
            fontSize   = 22.sp,
            fontWeight = FontWeight.Bold,             color      = com.qqmusictv.ui.theme.AppPalette.primary,
            modifier   = Modifier.padding(start = pageHorizontalInset, end = pageHorizontalInset, bottom = 14.dp),
        )
        LazyRow(
            contentPadding        = PaddingValues(horizontal = pageHorizontalInset, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(section.albums, key = { it.id }, contentType = { "album" }) { album ->
                val isPlaylist = album.id.startsWith("pl.") || album.id.startsWith("p.")
                val isStation = album.id.startsWith("ra.")
                // "Find Your Mood" cards carry the CategoryScreen prefix ("ac-"/"c-").
                val isCategory = album.id.startsWith("ac-") || album.id.startsWith("c-") || album.id.startsWith("mr-")
                AlbumCard(
                    album = album,
                    size = 130,
                    onClick = {
                        when {
                            isCategory -> onCategoryClick(album.id)
                            isStation  -> playerVm.playStation(album.id, album.artworkUrl(600), album.title)
                            isPlaylist -> onPlaylistClick(album.id, album.title, album.artworkUrl(500) ?: "")
                            else       -> onAlbumClick(album.id)
                        }
                    },
                    onLongClick = {
                        if (isPlaylist) playerVm.shufflePlayPlaylist(album.id)
                    },
                    motionEnabled = motionEnabled,
                )
            }
        }
    }
}

/** Localize known editorial headings while retaining source titles and shelf identity. */
private fun homeSectionTitle(title: String): String =
    if (title.trim().equals("Top Picks for You", ignoreCase = true)) tr(R.string.msg_151) else com.qqmusictv.i18n.AppLanguage.heading(title)

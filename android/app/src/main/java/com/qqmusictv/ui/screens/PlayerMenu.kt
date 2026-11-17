package com.qqmusictv.ui.screens

import com.qqmusictv.i18n.tr
import com.qqmusictv.R

import com.qqmusictv.ui.components.TouchSurface as Surface

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import androidx.tv.material3.Border
import com.qqmusictv.ui.viewmodel.PlayerViewModel
import com.qqmusictv.ui.components.Glyph

@Composable
internal fun SongDestinationsPopup(
    visible: Boolean,
    song: com.qqmusictv.data.model.Song,
    playerVm: PlayerViewModel,
    onDismiss: () -> Unit,
    onArtistClick: (String) -> Unit,
    onAlbumClick: (String) -> Unit,
) {
    val visibility = remember { androidx.compose.animation.core.MutableTransitionState(false) }
    visibility.targetState = visible
    if (visibility.isIdle && !visibility.currentState) return
    var ids by remember(song.id) { mutableStateOf(song.artistId to song.albumId) }
    var resolving by remember(song.id) { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(song.id) {
        if (ids.first == null || ids.second == null) {
            resolving = true
            val resolved = playerVm.lookupSongIds(song.id)
            ids = (ids.first ?: resolved.first) to (ids.second ?: resolved.second)
            resolving = false
        }
    }
    PlayerBubbleMenu(visibility,onDismiss,initialFocus=firstFocus, focusAvailable=ids.first != null || ids.second != null) {
        if(resolving) Text(tr(R.string.msg_282),color=Color(0xFF6E6E73),fontSize=12.sp,modifier=Modifier.padding(12.dp))
        NpMenuItem(tr(R.string.msg_132),modifier=if (ids.first != null) Modifier.focusRequester(firstFocus) else Modifier,icon=Glyph.ARTIST,
            enabled=ids.first!=null) {ids.first?.let(onArtistClick)}
        NpMenuItem(tr(R.string.msg_133),modifier=if (ids.first == null && ids.second != null) Modifier.focusRequester(firstFocus) else Modifier,icon=Glyph.ALBUM,enabled=ids.second!=null) {ids.second?.let(onAlbumClick)}
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun NpMenuItem(
    label: String,
    modifier: Modifier = Modifier,
    icon: com.qqmusictv.ui.components.Glyph? = null,
    iconAlpha: Float = 1f,
    checked: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val ink = if (enabled) Color(0xFF1D1D1F) else Color(0x611D1D1F)
    val noBorder = Border(BorderStroke(0.dp, Color.Transparent))
    Surface(
        onClick = onClick, enabled = enabled,
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent,
            focusedContainerColor = Color(0xFFECECEF), pressedContainerColor = Color(0xFFE2E2E6),
            disabledContainerColor = Color.Transparent),
        scale = ClickableSurfaceDefaults.scale(scale = 1f, focusedScale = 1f, pressedScale = 1f,
            disabledScale = 1f, focusedDisabledScale = 1f),
        border = ClickableSurfaceDefaults.border(border = noBorder, focusedBorder = noBorder,
            pressedBorder = noBorder, disabledBorder = noBorder, focusedDisabledBorder = noBorder),
        glow = ClickableSurfaceDefaults.glow(glow = Glow.None, focusedGlow = Glow.None, pressedGlow = Glow.None),
        tonalElevation = 0.dp,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Box(Modifier.width(26.dp), contentAlignment = Alignment.CenterStart) {
                    com.qqmusictv.ui.components.Icon(icon, size = 16.dp, color = ink.copy(alpha = ink.alpha * iconAlpha))
                }
            }
            Text(label, fontSize = 14.sp, color = ink, modifier = Modifier.weight(1f))
            if (checked) com.qqmusictv.ui.components.Icon(
                com.qqmusictv.ui.components.Glyph.CHECK, size = 14.dp, color = Color(0xFF31C27C))
        }
    }
}

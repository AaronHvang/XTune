package com.qqmusictv.ui.screens

import com.qqmusictv.i18n.tr
import com.qqmusictv.R


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.qqmusictv.ui.components.touchClick

/**
 * Ambient screensaver — the drifting background (rendered behind) plus a small
 * now-playing chip bottom-left. Grabs focus so any key wakes it back to the player.
 */
@Composable
private fun AmbientScreensaver(song: com.qqmusictv.data.model.Song, onWake: () -> Unit) {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    Box(
        Modifier.fillMaxSize()
            .touchClick(onWake)
            .focusRequester(fr)
            .focusable()
            .onKeyEvent { onWake(); true },
    ) {
        // Heavy dim over the drifting background — colours + motion still faintly show.
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.68f)))
        Row(
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 64.dp, bottom = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (song.artworkUrl != null) {
                AsyncImage(
                    model = song.artworkUrl(160),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)),
                )
            }
            Column {
                Text(song.title, maxLines = 1, style = TextStyle(fontFamily = LocalTextStyle.current.fontFamily, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White))
                Text(song.artistName, maxLines = 1, style = TextStyle(fontFamily = LocalTextStyle.current.fontFamily, fontSize = 14.sp, color = PlayerSecondaryColor))
            }
        }
    }
}

/** "关闭" / "5 分钟" / "1 小时" / "2 小时" for a screensaver timeout in minutes. */
internal fun screensaverLabel(min: Int): String = when {
    min <= 0    -> tr(R.string.msg_004)
    min % 60 == 0 -> com.qqmusictv.i18n.AppLanguage.quantity(R.plurals.hour_count, min / 60)
    else        -> com.qqmusictv.i18n.AppLanguage.quantity(R.plurals.minute_count, min)
}

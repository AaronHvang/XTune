package com.qqmusictv.ui.screens

import com.qqmusictv.i18n.tr
import com.qqmusictv.R

import com.qqmusictv.ui.components.TouchSurface as Surface

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode as AnimRepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import androidx.tv.material3.Border
import com.qqmusictv.ui.components.Glyph
import com.qqmusictv.ui.components.TouchSeekBar
import com.qqmusictv.util.TvDevice

@Composable
internal fun PlayerAccessoryButton(
    glyph: Glyph, label: String, selected: Boolean = false,
    visualScale: Float = 1f, modifier: Modifier = Modifier, onContextMenu: (() -> Unit)? = null, onClick: () -> Unit,
) {
    Surface(
        onClick = onClick, onLongClick = onContextMenu, onContextMenu = onContextMenu,
        modifier = modifier.size(36.dp).semantics { contentDescription = label },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(9.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent,
            focusedContainerColor = Color(0x22FFFFFF)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        glow = ClickableSurfaceDefaults.glow(glow = Glow.None, focusedGlow = Glow.None, pressedGlow = Glow.None),
        tonalElevation = 0.dp,
    ) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            com.qqmusictv.ui.components.Icon(glyph, size = 18.dp * visualScale,
                color = if (selected) Color.White else PlayerControlInactiveColor)
        }
    }
}

/**
 * Seek bar + time labels. Reads the per-frame progress State in its own scope so the
 * smooth clock recomposes only this small composable, not the whole Now Playing screen.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun PlayerProgressBar(
    progressState: androidx.compose.runtime.State<Long>,
    song: com.qqmusictv.data.model.Song,
    playFocus: FocusRequester,
    player: androidx.media3.common.Player,
    visualScale: Float = 1f,
    loading: Boolean = false,
) {
    val smoothProgressMs = progressState.value
    val duration = song.durationMs.takeIf { it > 0 } ?: 1L
    val progress = (smoothProgressMs.toFloat() / duration).coerceIn(0f, 1f)
    var seekBarFocused by remember { mutableStateOf(false) }
    var scrubMs by remember(song.id) { mutableLongStateOf(smoothProgressMs) }
    // Keep scrub cursor in sync with playback when not focused
    LaunchedEffect(smoothProgressMs, seekBarFocused) {
        if (!seekBarFocused) scrubMs = smoothProgressMs
    }
    val scrubProgress = (scrubMs.toFloat() / duration).coerceIn(0f, 1f)
    val noBorder = Border(BorderStroke(0.dp, Color.Transparent))
    if (TvDevice.usesTouchControls(LocalContext.current)) {
        key(song.id) {
            TouchSeekBar(
                progressMs = smoothProgressMs,
                durationMs = song.durationMs,
                onSeek = player::seekTo,
                modifier = Modifier.fillMaxWidth().focusProperties { canFocus = false },
                trackHeight = 3.dp * visualScale,
                loading = loading,
            )
        }
    } else {
        Surface(
            enabled = !loading,
            onClick = { if (seekBarFocused) { player.seekTo(scrubMs); runCatching { playFocus.requestFocus() } } },
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(4.dp)),
            colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent, disabledContainerColor = Color.Transparent),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
            border = ClickableSurfaceDefaults.border(border = noBorder, focusedBorder = noBorder),
            modifier = Modifier.fillMaxWidth().focusProperties { canFocus = false }
                .onFocusChanged { seekBarFocused = it.isFocused }
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        when (event.key) {
                            Key.DirectionLeft  -> { scrubMs = (scrubMs - 10_000).coerceAtLeast(0); true }
                            Key.DirectionRight -> { scrubMs = (scrubMs + 10_000).coerceAtMost(duration); true }
                            else -> false
                        }
                    } else false
                },
        ) {
            Column(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp)) {
                val trackHeight = if (seekBarFocused) 6.dp else 4.dp
                Box(Modifier.fillMaxWidth().height(trackHeight), contentAlignment = Alignment.Center) {
                    val trackModifier = Modifier.fillMaxWidth().height(trackHeight * visualScale).clip(RoundedCornerShape(3.dp))
                    if (loading) com.qqmusictv.ui.components.PlayerLoadingTrack(trackModifier)
                    else Box(modifier = trackModifier.background(Color(0x44FFFFFF))) {
                        Box(modifier = Modifier.fillMaxWidth(progress).fillMaxHeight().background(Color.White))
                        if (seekBarFocused) {
                            Box(modifier = Modifier.fillMaxWidth(scrubProgress).fillMaxHeight().background(Color.White.copy(alpha = 0.35f)))
                        }
                    }
                }
            }
        }
    }
    val timeHeight = referenceTextHeight("−" + formatMs(song.durationMs), TextStyle(fontFamily = LocalTextStyle.current.fontFamily, fontSize = 11.sp))
    Row(Modifier.fillMaxWidth().heightIn(min = timeHeight), Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (seekBarFocused) formatMs(scrubMs) else formatMs(smoothProgressMs),
            style = TextStyle(fontFamily = LocalTextStyle.current.fontFamily, fontSize = 11.sp * visualScale, color = if (seekBarFocused) Color.White else PlayerSecondaryColor)
        )
        Text("−" + formatMs((song.durationMs - smoothProgressMs).coerceAtLeast(0)), style = TextStyle(fontFamily = LocalTextStyle.current.fontFamily, fontSize = 11.sp * visualScale, color = PlayerSecondaryColor))
    }
}

/** Secondary player chrome blends with the fluid backdrop rather than painting opaque grey. */
internal val PlayerControlInactiveColor = Color.White.copy(alpha = 0.50f)
internal val PlayerSecondaryColor = Color.White.copy(alpha = 0.70f)

internal enum class TransportIcon { Play, Pause, Prev, Next, Panel }

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun TransportButton(
    icon: TransportIcon,
    onClick: () -> Unit,
    large: Boolean = false,
    loading: Boolean = false,
    modifier: Modifier = Modifier,
    visualScale: Float = 1f,
    inactiveColor: Color = Color.White,
    active: Boolean = false,
) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val btnSize = if (large) 58.dp else 46.dp
    // SF Symbols sit inside an optical bounding box (~30% padding), so they render smaller than the
    // old edge-to-edge Canvas glyphs at the same size — bump to compensate and read as platform-sized.
    // forward.fill/backward.fill pad even more than play.fill, so the non-large size is generous.
    val canvasSize = (if (large) 34.dp else 26.dp) * visualScale
    val noBorder = Border(BorderStroke(0.dp, Color.Transparent))
    Surface(
        onClick = onClick,
        shape  = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor        = Color.Transparent,
            focusedContainerColor = Color(0x22FFFFFF),
        ),
        scale  = ClickableSurfaceDefaults.scale(focusedScale = 1.10f),
        glow   = ClickableSurfaceDefaults.glow(glow = Glow.None, focusedGlow = Glow.None, pressedGlow = Glow.None),
        border = ClickableSurfaceDefaults.border(border = noBorder, focusedBorder = noBorder),
        modifier = modifier.size(btnSize).semantics {
            contentDescription = when (icon) {
                TransportIcon.Play -> tr(R.string.msg_001)
                TransportIcon.Pause -> tr(R.string.msg_000)
                TransportIcon.Prev -> tr(R.string.msg_289)
                TransportIcon.Next -> tr(R.string.msg_283)
                TransportIcon.Panel -> tr(R.string.msg_290)
            }
        },
        interactionSource = interactions,
    ) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            val breath = if (loading) {
                val transition = rememberInfiniteTransition(label = "loadingBreath")
                val alpha by transition.animateFloat(
                    initialValue = 0.30f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(850,
                        easing = androidx.compose.animation.core.FastOutSlowInEasing), AnimRepeatMode.Reverse),
                    label = "loadingButtonAlpha",
                )
                alpha
            } else 1f
            val ink = if (active || pressed) Color.White else inactiveColor
            val color = ink.copy(alpha = ink.alpha * breath)
            // SF Symbol transport glyphs (same set as the rest of the app).
            val glyph = when (icon) {
                TransportIcon.Play  -> com.qqmusictv.ui.components.Glyph.PLAY
                TransportIcon.Pause -> com.qqmusictv.ui.components.Glyph.PAUSE
                TransportIcon.Prev  -> com.qqmusictv.ui.components.Glyph.PREV
                TransportIcon.Next  -> com.qqmusictv.ui.components.Glyph.NEXT
                TransportIcon.Panel -> com.qqmusictv.ui.components.Glyph.QUEUE
            }
            com.qqmusictv.ui.components.Icon(glyph, size = canvasSize, color = color)
        }
    }
}

private fun formatMs(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

package com.qqmusictv.ui.screens

import com.qqmusictv.i18n.tr
import com.qqmusictv.R

import com.qqmusictv.ui.components.TouchSurface as Surface

import androidx.compose.foundation.layout.*
import com.qqmusictv.ui.navigation.ReturnLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun QueuePanel(
    queue: List<com.qqmusictv.data.model.Song>,
    currentIndex: Int,
    userQueue: List<com.qqmusictv.data.model.Song> = emptyList(),
    onSelect: (Int) -> Unit,
    onSelectUserQueue: (Int) -> Unit = {},
    onMove: (from: Int, to: Int) -> Unit = { _, _ -> },
    leftFocus: androidx.compose.ui.focus.FocusRequester? = null,
) {
    val listState = rememberLazyListState()
    var movingIndex by remember { mutableStateOf<Int?>(null) }

    // Always scroll to top (current song) when index changes or userQueue gains items
    LaunchedEffect(currentIndex, userQueue.size) {
        listState.animateScrollToItem(0)
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(tr(R.string.msg_106), fontSize = 13.sp, color = PlayerSecondaryColor, fontWeight = FontWeight.Medium)
            if (movingIndex != null) Text(tr(R.string.msg_284), fontSize = 10.sp, color = PlayerSecondaryColor)
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (userQueue.isNotEmpty()) {
                item {
                    Text(tr(R.string.msg_285), fontSize = 10.sp, color = Color(0xFF31C27C), fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                }
                items(userQueue.size, key = { "uq_${userQueue[it].id}_$it" }) { idx ->
                    val song = userQueue[idx]
                    Surface(
                        onClick = { onSelectUserQueue(idx) },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
                        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0x1531C27C), focusedContainerColor = Color(0x2531C27C)),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                        modifier = Modifier.fillMaxWidth().animateItem().let { m ->
                            if (leftFocus != null) m.focusProperties { left = leftFocus } else m
                        },
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("◆", fontSize = 9.sp, color = Color(0xFF31C27C), modifier = Modifier.width(20.dp))
                            Column(Modifier.weight(1f)) {
                                Text(song.title + if (song.isMusicVideo) " (MV)" else "", fontSize = 13.sp, color = PlayerSecondaryColor, maxLines = 1)
                                Text(song.artistName, fontSize = 11.sp, color = PlayerSecondaryColor, maxLines = 1)
                            }
                            Text(song.durationFormatted, fontSize = 11.sp, color = PlayerSecondaryColor)
                        }
                    }
                }
                item {
                    Text(tr(R.string.msg_286), fontSize = 10.sp, color = PlayerSecondaryColor, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                }
            }
            val visibleQueue = queue.drop(currentIndex + 1)
            items(visibleQueue.size, key = { rel -> "${queue.getOrNull(currentIndex + 1 + rel)?.id}_${currentIndex + 1 + rel}" }) { rel ->
                // visibleQueue[rel] == queue[currentIndex + 1 + rel]. The +1 was missing,
                // so tapping a row played the track before it.
                val idx = currentIndex + 1 + rel
                val song = visibleQueue[rel]
                val isCurrent = false
                val isMoving = idx == movingIndex
                val movingMod = if (isMoving) Modifier.onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        Key.DirectionUp -> {
                            val target = (movingIndex!! - 1).coerceAtLeast(currentIndex + 1)
                            if (target != movingIndex) { onMove(movingIndex!!, target); movingIndex = target }
                            true
                        }
                        Key.DirectionDown -> {
                            val target = (movingIndex!! + 1).coerceAtMost(queue.lastIndex)
                            if (target != movingIndex) { onMove(movingIndex!!, target); movingIndex = target }
                            true
                        }
                        Key.Enter -> { movingIndex = null; true }
                        else -> false
                    }
                } else Modifier
                Surface(
                    onClick = { if (movingIndex == idx) movingIndex = null else onSelect(idx) },
                    onLongClick = { if (rel > 0) movingIndex = idx },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor        = when { isMoving -> Color(0x4431C27C); isCurrent -> Color(0x26FFFFFF); else -> Color.Transparent },
                        focusedContainerColor = if (isMoving) Color(0x5531C27C) else Color(0x33FFFFFF),
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                    modifier = Modifier.fillMaxWidth().animateItem().then(movingMod).let { m ->
                        if (leftFocus != null) m.focusProperties { left = leftFocus } else m
                    },
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            if (isMoving) "⠿" else "${idx + 1}",
                            fontSize = 11.sp,
                            color = if (isMoving) Color(0xFF31C27C) else if (isCurrent) Color(0xFF31C27C) else PlayerSecondaryColor,
                            modifier = Modifier.width(20.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(song.title + if (song.isMusicVideo) " (MV)" else "", fontSize = 13.sp, color = if (isCurrent || isMoving) Color.White else PlayerSecondaryColor, maxLines = 1, fontWeight = if (isCurrent) FontWeight.Medium else FontWeight.Normal)
                            Text(song.artistName, fontSize = 11.sp, color = PlayerSecondaryColor, maxLines = 1)
                        }
                        Text(song.durationFormatted, fontSize = 11.sp, color = PlayerSecondaryColor)
                    }
                }
            }
        }
    }
}

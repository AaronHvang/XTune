package com.qqmusictv.ui.screens


import com.qqmusictv.ui.components.LocalUiMotion
import com.qqmusictv.ui.components.motionInput
import com.qqmusictv.ui.components.TouchSurface as Surface

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import com.qqmusictv.ui.navigation.ReturnLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.tv.material3.*
import androidx.tv.material3.Border
import coil.compose.AsyncImage
import com.qqmusictv.data.network.LyricLine
import com.qqmusictv.data.network.LyricWord
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.isActive

/**
 * Full-screen lyrics view — hides the split layout, shows only the karaoke lyrics with a
 * small now-playing chip. Portrait forces this view; Back keeps the shell's normal behavior.
 */
@Composable
internal fun FullScreenLyrics(
    lyrics: List<LyricLine>,
    progressState: androidx.compose.runtime.State<Long>,
    offsetMs: Long,
    song: com.qqmusictv.data.model.Song,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    onPrev: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    lyricsScale: Float = 1f,
    chromeVisible: Boolean = true,
    loading: Boolean = false,
    showEmptyLyrics: Boolean = true,
    stationLaunch: com.qqmusictv.ui.viewmodel.StationLaunch? = null,
    lyricsHidden: Boolean = false,
    suppressPlayFocus: Boolean = false,
    footerVisible: Boolean = true,
    playFocusRequester: FocusRequester? = null,
    onSongInfoClick: () -> Unit = {},
    songInfoMenu: @Composable () -> Unit = {},
) {
    // Focus lands on the play button (not the top lyric line). Passing it to LyricsPanel
    // as playFocus also makes RIGHT jump here and the 7s idle auto-return work, so the
    // lyrics don't get stranded scrolled-away after you fiddle with the D-pad.
    val playFocus = playFocusRequester ?: remember { FocusRequester() }
    PlayerPlayFocusEffect(playFocus,chromeVisible, suppressInitialFocus = suppressPlayFocus)

    val density = LocalDensity.current
    var landscapeInfoHeight by remember(density) { mutableStateOf(64.dp) }
    var landscapeControlsHeight by remember(density) { mutableStateOf(46.dp) }
    val uiMotion = LocalUiMotion.current
    val footerAlpha by animateFloatAsState(if (footerVisible) 1f else 0f, tween(300), label = "wallFooterContents")
    val ctlAlpha by animateFloatAsState(if (chromeVisible) 1f else 0f, tween(playerMotionDuration(uiMotion.enabled)), label = "fsControls")
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val portrait = maxHeight > maxWidth
        val portraitSide = (maxWidth * 0.07f).coerceIn(20.dp, 48.dp)
        val landscapeTitleWidth = maxWidth * 0.42f
        AnimatedVisibility(visible = !lyricsHidden, enter = fadeIn(tween(350)), exit = fadeOut(tween(350))) {
        Column(Modifier.fillMaxSize().padding(
            start = if (portrait) portraitSide else 72.dp,
            end = if (portrait) portraitSide else 128.dp,
            top = if (portrait) 24.dp else 36.dp,
            bottom = 120.dp)) {
            if (stationLaunch != null) StationLaunchMessage(stationLaunch.failed, 1.3f * lyricsScale)
            else if(!lyricsHidden) com.qqmusictv.ui.components.FontGatedLyrics(com.qqmusictv.ui.theme.LocalAppFontState.current, fontScale = 1.3f * lyricsScale) {
            if (lyrics.isNotEmpty()) LyricsPanel(
                lyrics = lyrics,
                progressState = progressState,
                offsetMs = offsetMs,
                onSeek = onSeek,
                playFocus = playFocus,
                fontScale = 1.3f * lyricsScale,
                autoReturnMs = 5_000L,
            )
            else if (!loading) EmptyLyrics(song.id, 1.3f * lyricsScale, showEmptyLyrics)
            }
        }
        }
        if (footerVisible || footerAlpha > 0f) {
        CompositionLocalProvider(com.qqmusictv.ui.components.LocalMotionInteractive provides
            (com.qqmusictv.ui.components.LocalMotionInteractive.current && footerVisible)) {
        if (portrait) {
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .graphicsLayer { alpha = footerAlpha }.motionInput(footerVisible)
                    .padding(start = portraitSide, end = portraitSide, bottom = portraitSide),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (song.artworkUrl != null) AsyncImage(
                    model = song.artworkUrl(160), contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.size(64.dp)
                        .clip(RoundedCornerShape(10.dp)).testTag("fullscreenArtwork"),
                )
                Box(Modifier.weight(1f)) {
                    SongInfoFocusSurface(chromeAlpha = ctlAlpha, onClick = onSongInfoClick, compact = true) {
                        Column {
                            Text(song.title, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                style = TextStyle(fontFamily = LocalTextStyle.current.fontFamily, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White))
                            Text(song.artistName, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                style = TextStyle(fontFamily = LocalTextStyle.current.fontFamily, fontSize = 14.sp, color = PlayerSecondaryColor))
                        }
                    }
                    songInfoMenu()
                }
                Row(Modifier.graphicsLayer { alpha = ctlAlpha },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TransportButton(TransportIcon.Prev, onClick = onPrev)
                    TransportButton(if (isPlaying) TransportIcon.Pause else TransportIcon.Play, onClick = onPlayPause,
                        loading = loading, active = isPlaying, modifier = Modifier.focusRequester(playFocus))
                    TransportButton(TransportIcon.Next, onClick = onNext)
                }
            }
        } else {
        // Now-playing chip, bottom-left — same placement as the screensaver.
        Row(
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 64.dp, bottom = 48.dp)
                .graphicsLayer { alpha = footerAlpha }.motionInput(footerVisible)
                .onSizeChanged { landscapeInfoHeight = with(density) { it.height.toDp() } },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (song.artworkUrl != null) {
                AsyncImage(
                    model = song.artworkUrl(160),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(64.dp)
                        .clip(RoundedCornerShape(10.dp)).testTag("fullscreenArtwork"),
                )
            }
            Box(Modifier.widthIn(max = landscapeTitleWidth)) {
                SongInfoFocusSurface(chromeAlpha = ctlAlpha, onClick = onSongInfoClick, compact = true) {
                    Column {
                        Text(song.title, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Clip, style = TextStyle(fontFamily = LocalTextStyle.current.fontFamily, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White))
                        Text(song.artistName, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Clip, style = TextStyle(fontFamily = LocalTextStyle.current.fontFamily, fontSize = 14.sp, color = PlayerSecondaryColor))
                    }
                }
                songInfoMenu()
            }
        }
        // Transport controls, bottom-right (where "Back to exit" used to be). Fades on idle.
        Row(
            modifier = Modifier.align(Alignment.BottomEnd)
                .padding(end = 56.dp, bottom = 48.dp + (landscapeInfoHeight - landscapeControlsHeight) / 2)
                .onSizeChanged { landscapeControlsHeight = with(density) { it.height.toDp() } }
                .graphicsLayer { alpha = ctlAlpha * footerAlpha }.motionInput(footerVisible),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TransportButton(TransportIcon.Prev, onClick = onPrev)
            TransportButton(if (isPlaying) TransportIcon.Pause else TransportIcon.Play, onClick = onPlayPause,
                loading = loading, active = isPlaying, modifier = Modifier.focusRequester(playFocus))
            TransportButton(TransportIcon.Next, onClick = onNext)
        }
        }
        }
        }
    }
}

/**
 * Interpolates smooth 60fps playback position between the ~200ms server
 * polling ticks, so word-by-word lyric animation doesn't stutter.
 */
@Composable
internal fun rememberSmoothProgressMs(reportedMs: Long, isPlaying: Boolean, enabled: Boolean = true): androidx.compose.runtime.State<Long> {
    val anchorRealMs = remember { mutableLongStateOf(System.currentTimeMillis()) }
    val anchorPosMs  = remember { mutableLongStateOf(reportedMs) }
    // Returned as State so callers can defer the read to leaf composables — only the
    // one thing that reads .value (the seek bar, the active word wipe) recomposes per
    // frame, instead of the whole Now Playing tree.
    val smoothMs = remember { mutableLongStateOf(reportedMs) }

    LaunchedEffect(reportedMs) {
        anchorPosMs.longValue  = reportedMs
        anchorRealMs.longValue = System.currentTimeMillis()
        smoothMs.longValue = reportedMs
    }

    LaunchedEffect(isPlaying, enabled) {
        if (!isPlaying || !enabled) return@LaunchedEffect
        // Resume from the frozen position instead of counting wall time spent paused.
        anchorPosMs.longValue = smoothMs.longValue
        anchorRealMs.longValue = System.currentTimeMillis()
        while (isActive) {
            withFrameMillis {
                smoothMs.longValue = anchorPosMs.longValue + (System.currentTimeMillis() - anchorRealMs.longValue)
            }
        }
    }

    return smoothMs
}

/** How many lines of already-sung context stay above the active line after a scroll. */
private const val LYRIC_LEAD_LINES = 2
/** Scroll once the active line gets this close to the bottom of the viewport. */
private const val LYRIC_BOTTOM_MARGIN_PX = 40

private const val GAP_THRESHOLD_MS = 4000L
private const val LINE_END_GRACE_MS = 250L
private const val GAP_DOTS_PREROLL_MS = 400L

@OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun LyricsPanel(
    lyrics: List<LyricLine>,
    progressState: androidx.compose.runtime.State<Long>,
    offsetMs: Long,
    onSeek: (Long) -> Unit,
    playFocus: FocusRequester? = null,
    fontScale: Float = 1f,
    autoReturnMs: Long = 7000L,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val activeLineFocus = remember { FocusRequester() }

    // Structure (which line is active, gaps, auto-scroll) only needs coarse progress —
    // derivedStateOf quantises to 200ms so the list body recomposes ~5x/s, not 60x/s.
    // The active word wipe reads the raw per-frame State itself, staying smooth.
    val progressMs by remember(offsetMs) {
        androidx.compose.runtime.derivedStateOf { ((progressState.value + offsetMs) / 200L) * 200L }
    }
    val liveProgress = { progressState.value + offsetMs }

    val passedIndex = lyrics.indexOfLast { it.startMs <= progressMs }
    // Keep a line lit until its background vocals finish too — the lead line shouldn't
    // dim out while its (call-and-response) background part is still being sung.
    val activeIndex = if (passedIndex >= 0) {
        val ln = lyrics[passedIndex]
        val activeUntil = maxOf(ln.endMs, ln.background?.endMs ?: Long.MIN_VALUE) + LINE_END_GRACE_MS
        if (progressMs <= activeUntil) passedIndex else -1
    } else -1

    val scrollAnchor = passedIndex.coerceAtLeast(0)
    val firstLoad = remember(lyrics) { mutableStateOf(true) }
    val lyricScrollMotion = remember { LyricScrollMotion() }

    // Track user-initiated scrolls so auto-scroll doesn't fight them. Auto-scroll
    // resumes the way the service does: not on a timer, but when you bring the
    // current line back on screen — and then only at the next line change, so it
    // rejoins the flow instead of yanking mid-line.
    var userScrolled by remember { mutableStateOf(false) }
    // Also arm the auto-return when the lyric list simply HAS focus (you D-padded LEFT
    // into it) — not only after a scroll. Focus-in alone used to leave the timer disarmed,
    // so it never handed focus back to the play button.
    var listFocused by remember { mutableStateOf(false) }
    // After the lyrics have been focused/centred for a bit, hand focus back to the
    // play button so the D-pad isn't stranded in the lyric list.
    LaunchedEffect(userScrolled, listFocused) {
        if (playFocus == null || (!userScrolled && !listFocused)) return@LaunchedEffect
        kotlinx.coroutines.delay(autoReturnMs)
        if (!listState.isScrollInProgress) { runCatching { playFocus.requestFocus() }; userScrolled = false }
    }
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) userScrolled = true
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(scrollAnchor, lyrics) {
        if (lyrics.isEmpty()) return@LaunchedEffect
        val target = (scrollAnchor - LYRIC_LEAD_LINES).coerceAtLeast(0)
        if (firstLoad.value) {
            lyricScrollMotion.reset()
            listState.scrollToItem(target)
            firstLoad.value = false
            return@LaunchedEffect
        }
        if (userScrolled) {
            lyricScrollMotion.reset()
            // Still browsing elsewhere — leave the list alone. Once the active line is
            // visible again this same effect fires on the next line change and takes over.
            val backInView = listState.layoutInfo.visibleItemsInfo.any { it.index == scrollAnchor }
            if (!backInView || listState.isScrollInProgress) return@LaunchedEffect
            userScrolled = false
            listState.animateScrollToItem(target)
            return@LaunchedEffect
        }

        // Centre the active line on every line change (service behaviour) — the
        // old "only scroll near the bottom" logic is what made it jump every few lines.
        val info = listState.layoutInfo
        val activeItem = info.visibleItemsInfo.firstOrNull { it.index == scrollAnchor }
        if (activeItem == null) {
            lyricScrollMotion.reset()
            listState.animateScrollToItem(target)
        } else {
            val viewportH = info.viewportEndOffset - info.viewportStartOffset
            val itemCentre = activeItem.offset + activeItem.size / 2
            // Sit the active line high — around the album-art midline (~30% down), not centre.
            val delta = (itemCentre - viewportH * 0.30f)
            if (kotlin.math.abs(delta) > 8f) lyricScrollMotion.scrollBy(listState, delta)
        }
    }

    // When navigating back to this screen, scroll to the active line.
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (!firstLoad.value && lyrics.isNotEmpty()) {
                val target = (passedIndex - 3).coerceAtLeast(0)
                userScrolled = false
                listState.scrollToItem(target)
            }
        }
    }

    // No pinned/sticky line. It was tried for Get Lucky's held phrase, but
    // line-synced sources set endMs to the next line's startMs, so nearly every line
    // matched and one was permanently stuck at the top, fading in and out.
    // Widen only the drawing viewport. End padding preserves the paragraph's measured
    // width while leaving room for focus scaling outside its nominal container.
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val focusOverflow = maxWidth * 0.10f
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxHeight().wrapContentWidth(Alignment.Start, unbounded = true)
            .requiredWidth(maxWidth + focusOverflow)
            .align(Alignment.TopStart).padding(end = 16.dp).nestedScroll(nestedScrollConnection)
            .onFocusChanged { listFocused = it.hasFocus }
            // Soft fade the top + bottom edges so lines dissolve in/out instead of popping.
            .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val h = size.height
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        (48f / h) to Color.Black,
                        ((h - 150f) / h).coerceIn(0f, 1f) to Color.Black,
                        1f to Color.Transparent,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            }
            .then(if (playFocus != null) Modifier.focusProperties {
                right = playFocus
                // LEFT from a lyric line always lands on play/pause too — user wants
                // either horizontal press out of the lyrics to go straight to transport.
                left = playFocus
                // Entering the list (e.g. LEFT from the play button) lands on the current
                // line, not whichever line happens to sit nearest the button.
                // Only steer focus to the active line if it is actually laid out right now.
                // Requesting focus on a detached FocusRequester leaves the window with no
                // focused view → input dispatch times out → ANR. Fall back to Default.
                enter = {
                    val activeVisible = activeIndex >= 0 &&
                        listState.layoutInfo.visibleItemsInfo.any { it.index == activeIndex }
                    if (activeVisible) activeLineFocus else androidx.compose.ui.focus.FocusRequester.Default
                }
            } else Modifier)
            .onPreviewKeyEvent { ev: androidx.compose.ui.input.key.KeyEvent ->
                // Block upward D-pad escape to top nav bar from lyrics
                ev.key == Key.DirectionUp && ev.type == KeyEventType.KeyDown &&
                    listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
            },
        contentPadding = PaddingValues(top = 32.dp, bottom = 120.dp, end = focusOverflow),
        verticalArrangement = Arrangement.spacedBy(36.dp * (fontScale / 1.25f)),
    ) {
        // Key MUST be unique: two synced lines can share a startMs (a blank + first line both at 0ms
        // is common), and duplicate keys crash LazyColumn on the scroll-to-active remeasure. Fold in
        // the index — the list never reorders within a song, so index keeps keys stable and unique.
        items(lyrics.size, key = { "$it:${lyrics[it].startMs}" }) { idx ->
            val line = lyrics[idx]
            // One line lit at a time. Holding sustained lines lit was tried and removed:
            // line-synced sources set endMs to the next line's startMs, so "still within
            // its window" matched almost everything and the list scrolled and faded
            // wrongly on ordinary songs.
            // Unsynced (plain-text) lyrics come back with startMs = -1. Show them so
            // the panel isn't empty, but never highlighted and never seekable.
            val unsynced = line.startMs < 0
            // Sustained/held line stays lit in-flow while its own window still covers now (Get Lucky).
            val overlapActive = !unsynced && line.words.isNotEmpty() &&
                progressMs >= line.startMs && progressMs <= line.endMs &&
                (idx + 1 >= lyrics.size || line.endMs > lyrics[idx + 1].startMs)
            val isActive = (!unsynced && idx == activeIndex) || overlapActive
            val isPast = !unsynced && (idx < passedIndex || (idx == passedIndex && activeIndex == -1))
            // Only the active line reads the live per-frame clock (so only it recomposes
            // at 60fps). Inactive lines get a fixed value and never re-run on tick.
            val staticLineProgress = if (isPast) line.endMs else line.startMs - 1L
            val progressProvider: () -> Long = if (isActive) liveProgress else ({ staticLineProgress })

            val prevEnd = if (idx > 0) lyrics[idx - 1].endMs else 0L
            val gapMs = line.startMs - prevEnd
            // Mount before the gap so the live-clock fade starts at zero, even though
            // list structure is only checked every 200ms. Removal follows the fade.
            val nearGap = progressMs >= prevEnd - GAP_DOTS_PREROLL_MS && progressMs < line.startMs
            if (gapMs >= GAP_THRESHOLD_MS && nearGap && idx == passedIndex + 1) {
                MusicalDots(
                    progressState = progressState,
                    offsetMs = offsetMs,
                    gapStartMs = prevEnd,
                    gapEndMs = line.startMs,
                    modifier = Modifier.padding(start = 12.dp, bottom = 10.dp),
                )
            }

            if (unsynced) {
                Text(
                    line.text,
                    style = TextStyle(fontFamily = LocalTextStyle.current.fontFamily, fontSize = (32f * fontScale).sp, lineHeight = (44f * fontScale).sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.40f)),
                    modifier = Modifier.fillMaxWidth().padding(end = 16.dp),
                )
            } else {
                LyricLineRow(
                    line = line,
                    isActive = isActive,
                    isPast = isPast,
                    progress = progressProvider,
                    fontScale = fontScale,
                    focusRequester = if (isActive) activeLineFocus else null,
                    onSeek = { onSeek(line.startMs) },
                )
            }
        }
    }
    }
}

@Composable
private fun WordWipeLine(
    text: String,
    words: List<LyricWord>,
    activeIdx: Int,
    progressMs: Long,
    live: Boolean = true,
    fontSize: androidx.compose.ui.unit.TextUnit = 32.sp,
    lineHeight: androidx.compose.ui.unit.TextUnit = 44.sp,
    weight: FontWeight = FontWeight.Bold,
    sungColor: Color = Color.White,
    unsungColor: Color = Color.White.copy(alpha = 0.40f),
) {
    val paragraph = buildAnnotatedString {
        append(text)
        var cursor = 0
        words.forEachIndexed { index, word ->
            val start = text.indexOf(word.text, startIndex = cursor)
            if (start >= 0) {
                val end = start + word.text.length
                when {
                    index < activeIdx -> {
                        addStyle(SpanStyle(color = sungColor), start, end)
                    }
                    // 逐字点亮：当前词内部按字符位置做子插值，形成从左到右的卡拉OK式擦除。
                    // 单字词退化为原有的整词插值，行为不变。
                    index == activeIdx && live -> {
                        val wordFraction = ((progressMs - word.startMs).toFloat() /
                            (word.endMs - word.startMs).coerceAtLeast(1L)).coerceIn(0f, 1f)
                        val wordLen = word.text.length
                        if (wordLen <= 1) {
                            addStyle(SpanStyle(color = androidx.compose.ui.graphics.lerp(unsungColor, sungColor, wordFraction)), start, end)
                        } else {
                            for (i in 0 until wordLen) {
                                val charFraction = (wordFraction * wordLen - i).coerceIn(0f, 1f)
                                addStyle(
                                    SpanStyle(color = androidx.compose.ui.graphics.lerp(unsungColor, sungColor, charFraction)),
                                    start + i, start + i + 1
                                )
                            }
                        }
                    }
                    else -> {
                        addStyle(SpanStyle(color = unsungColor), start, end)
                    }
                }
                cursor = end
            }
        }
    }
    Text(paragraph, style = TextStyle(fontFamily = LocalTextStyle.current.fontFamily, 
                fontSize = fontSize, fontWeight = FontWeight.Bold, lineHeight = lineHeight,
        color = sungColor, letterSpacing = (-0.25).sp,
    ), modifier = Modifier.fillMaxWidth())
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun LyricLineRow(
    line: LyricLine,
    isActive: Boolean,
    isPast: Boolean,
    progress: () -> Long,
    onSeek: () -> Unit,
    fontScale: Float = 1f,
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val focusScale by animateFloatAsState(if (focused) 1.06f else 1f, tween(180), label = "lyricFocusScale")
    // Reading the provider here subscribes only this row: the active row (live clock)
    // recomposes per frame; inactive rows read a constant and never re-run on tick.
    val progressMs = progress()
    val targetOpacity = when {
        isActive -> 1f
        isPast   -> 0.35f
        else     -> 0.40f
    }
    // Keep paragraph metrics stable; time changes brightness without resizing the sentence.
    val opacity by animateFloatAsState(targetOpacity, tween(300), label = "lineOpacity")

    Box(
        Modifier.fillMaxWidth().graphicsLayer {
            alpha = opacity
            compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
            scaleX = focusScale
            scaleY = focusScale
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
            clip = false
        }
    ) {
        Surface(
            onClick = onSeek,
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
            glow = ClickableSurfaceDefaults.glow(glow = Glow.None, focusedGlow = Glow.None, pressedGlow = Glow.None),
            border = ClickableSurfaceDefaults.border(border = Border.None, focusedBorder = Border.None),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                if (isActive && line.words.isNotEmpty()) {
                    // Timing highlights live inside one naturally wrapped paragraph.
                    val activeIdx = line.words.indexOfLast { it.startMs <= progressMs }
                    WordWipeLine(text = line.text, words = line.words, activeIdx = activeIdx, progressMs = progressMs, fontSize = (32f * fontScale).sp, lineHeight = (44f * fontScale).sp)
                } else {
                    // Inactive sentences are pure white; only the animated row alpha dims them.
                    val style = when {
                        isActive -> TextStyle(fontFamily = LocalTextStyle.current.fontFamily, fontSize = (32f * fontScale).sp, fontWeight = FontWeight.Bold, lineHeight = (44f * fontScale).sp, letterSpacing = (-0.4).sp, color = Color.White)
                        isPast   -> TextStyle(fontFamily = LocalTextStyle.current.fontFamily, color = Color.White, fontSize = (32f * fontScale).sp, fontWeight = FontWeight.Bold, lineHeight = (44f * fontScale).sp, letterSpacing = (-0.2).sp)
                        else     -> TextStyle(fontFamily = LocalTextStyle.current.fontFamily, color = Color.White, fontSize = (32f * fontScale).sp, fontWeight = FontWeight.Bold, lineHeight = (44f * fontScale).sp, letterSpacing = (-0.2).sp)
                    }
                    Text(text = AnnotatedString(line.text), style = style)
                }

                val bg = line.background
                if (bg != null && bg.text.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    val bgProgress = progressMs + 300L  // -300ms early start
                    val bgLive = bgProgress in bg.startMs..(bg.endMs + 600L)
                    val bgTargetScale = if (bgLive) 1.08f else 0.93f
                    val bgScale by animateFloatAsState(bgTargetScale, tween(375), label = "bgScale")

                    Box(
                        Modifier.graphicsLayer {
                            scaleX = bgScale; scaleY = bgScale
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                        }
                    ) {
                        if (bg.words.isNotEmpty()) {
                            // Same soft left→right wipe as the lead line, just smaller/dimmer.
                            val bgActiveIdx = bg.words.indexOfLast { it.startMs <= bgProgress }
                            WordWipeLine(
                                text = bg.text, words = bg.words, activeIdx = bgActiveIdx, progressMs = bgProgress,
                                live = bgLive,
                                fontSize = 19.sp, lineHeight = 24.sp, weight = FontWeight.SemiBold,
                                sungColor = Color.White,
                                unsungColor = Color.White.copy(alpha = if (isActive) 0.40f else 1f),
                            )
                        } else {
                            Text(
                                text = AnnotatedString(bg.text),
                                style = TextStyle(fontFamily = LocalTextStyle.current.fontFamily, 
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White.copy(alpha = if (!isActive || bgLive) 1f else 0.40f),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MarqueeText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 16.sp,
    fontWeight: FontWeight = FontWeight.Normal,
    color: Color = Color.White,
    /** Tracking is size-specific: large text reads too loose at 0, so tighten it. */
    letterSpacing: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
) {
    val scrollState = rememberScrollState()
    var overflows by remember(text) { mutableStateOf(false) }
    var measured  by remember(text) { mutableStateOf(false) }
    // Ping-pong: glide left→right to reveal the tail, pause, then glide back right→left — smooth
    // both ways, no fade-and-jump. Ease-in-out easing softens each turnaround.
    LaunchedEffect(text, overflows) {
        if (!overflows) return@LaunchedEffect
        val ease = androidx.compose.animation.core.CubicBezierEasing(0.4f, 0f, 0.6f, 1f)
        scrollState.scrollTo(0)
        while (true) {
            kotlinx.coroutines.delay(1400)
            scrollState.animateScrollTo(scrollState.maxValue, androidx.compose.animation.core.tween(6000, easing = ease))
            kotlinx.coroutines.delay(1400)
            scrollState.animateScrollTo(0, androidx.compose.animation.core.tween(6000, easing = ease))
        }
    }
    Box(
        modifier = modifier.clipToBounds().then(
            // When the text is scrolling, dissolve it at BOTH edges instead of hard-cutting.
            if (overflows) Modifier
                .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val w = size.width
                    val edge = 22f
                    // Only fade the LEFT edge once the text has actually scrolled off it —
                    // at rest (start of the title) the first letters must be fully solid.
                    val leftFade = if (scrollState.value > 2) Color.Transparent else Color.Black
                    val rightFade = if (scrollState.value < scrollState.maxValue - 2) Color.Transparent else Color.Black
                    drawRect(
                        Brush.horizontalGradient(
                            0f to leftFade,
                            (edge / w).coerceIn(0f, 0.5f) to Color.Black,
                            (1f - edge / w).coerceIn(0.5f, 1f) to Color.Black,
                            1f to rightFade,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                }
            else Modifier,
        ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TextStyle(fontFamily = LocalTextStyle.current.fontFamily, fontSize = fontSize, fontWeight = fontWeight, color = color,
                letterSpacing = letterSpacing),
            maxLines = 1, softWrap = false,
            textAlign = if (overflows) androidx.compose.ui.text.style.TextAlign.Start else androidx.compose.ui.text.style.TextAlign.Center,
            overflow = if (overflows) androidx.compose.ui.text.style.TextOverflow.Visible else androidx.compose.ui.text.style.TextOverflow.Clip,
            modifier = if (overflows)
                Modifier.horizontalScroll(scrollState, enabled = false)
            else
                Modifier.fillMaxWidth(),
            onTextLayout = { result ->
                if (!measured) { measured = true; overflows = result.didOverflowWidth }
            },
        )
    }
}

package com.qqmusictv.ui.screens

import com.qqmusictv.i18n.tr
import com.qqmusictv.R
import kotlinx.coroutines.launch

import com.qqmusictv.ui.components.FadeThrough
import com.qqmusictv.ui.components.FadeVisibility
import com.qqmusictv.ui.components.LocalUiMotion
import com.qqmusictv.ui.components.TouchSurface as Surface

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import com.qqmusictv.ui.viewmodel.stationPresentation
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.qqmusictv.ui.viewmodel.playbackOrder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.qqmusictv.ui.viewmodel.NavigationViewModel
import com.qqmusictv.ui.viewmodel.PlayerViewModel
import com.qqmusictv.ui.components.Glyph
import com.qqmusictv.ui.background.MacBackground

// Only the foreground below the cover is reduced; layout and focus targets retain their size.
private const val UnderCoverVisualScale = 0.77f
private const val ENTRY_CHOREO_MS = 800  // 居中停留 0.8s，期间预热播放缓存，避免滑入左侧时卡顿

/** DIAGNOSTIC: off = don't spin up the motion-artwork video decoder (CPU test). */
private const val MOTION_ENABLED = true

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    playerVm: PlayerViewModel,
    navVm: NavigationViewModel,
    onArtistClick: (String) -> Unit = {},
    onAlbumClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    onPlaylistClick: (String, String, String?) -> Unit = { _, _, _ -> },
    /** False while the shell keeps this page composed but parked off-screen between visits. */
    active: Boolean = true,
    /** True only when the shell entered the player from a browsing tab, never from a detail. */
    entryReplay: Boolean = true,
    /** 顶部胶囊按下 DOWN 时的落点：由 shell 持有，回调到这里进入播放按钮。 */
    contentFocusLink: PlayerContentFocusLink = remember { PlayerContentFocusLink() },
) {
    // Parked behind another page: hold the last snapshot instead of recomposing this whole
    // page five times a second on progress ticks.
    val playbackState by remember(active) {
        if (active) playerVm.state else kotlinx.coroutines.flow.flowOf(playerVm.state.value)
    }.collectAsState(initial = playerVm.state.value)
    val artworkCentered by playerVm.artworkCentered.collectAsState()

    // 焦点相关声明前移：entryReplay effect 结束时需要调用 focusScope.enter()。
    val playFocus = remember { FocusRequester() }
    val fullPlayFocus = remember { FocusRequester() }
    // 声明式焦点 scope："进入某模式" → scope 自动在控件就绪后把焦点打到对应默认位置。
    val focusScope = remember { PlayerFocusScope() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(playFocus, fullPlayFocus) {
        focusScope.bind(playFocus, fullPlayFocus)
    }
    // 常驻层必须同步 active——active=false（用户在详情页/tab 页）时 scope 绝不抢焦点。
    LaunchedEffect(active) { focusScope.active = active }

    // Apple Music TV entry choreography: artwork sits centered alone while the rest
    // of the page slides in underneath, then slides left as lyrics + transport fade in.
    // Replays on every tab entry, never when coming back from a detail.
    // Duration must exceed the tab transition so the slide-left begins after the page settles.
    var entryCentered by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(active, entryReplay) {
        if (!active) return@LaunchedEffect
        // Back from a detail keeps the settled layout: only a tab entry replays the choreography.
        if (!entryReplay) { entryCentered = false; return@LaunchedEffect }
        entryCentered = true
        // 利用专辑居中停留的 0.8s 预热播放缓存（当前曲目网关地址 + 下一首完整预取），
        // 让稍后滑到左侧、歌词/控件淡入时音频已缓冲好，不出现卡顿。
        playerVm.warmPlaybackCache()
        kotlinx.coroutines.delay(ENTRY_CHOREO_MS.toLong())
        entryCentered = false
        // Tab 进入动画结束后，主动把焦点送到播放按钮；否则光标悬空在容器上，
        // 用户看不到焦点，直到 chrome 隐藏→再显示才被动落到 PlayButton。
        focusScope.enter(PlayerMode.PlayButton)
    }
    val contentAlpha by animateFloatAsState(
        targetValue = if (entryCentered) 0f else 1f,
        animationSpec = tween(420),
        label = "playerEntryContentAlpha",
    )
    val state = playbackState.stationPresentation()
    val fontState = com.qqmusictv.ui.theme.LocalAppFontState.current
    val song = state.currentSong
    val toggleCount by navVm.toggleQueue.collectAsState()
    val interaction by navVm.playerInteraction.collectAsState()
    val showQueue = toggleCount % 2 == 1
    val smoothProgress = rememberSmoothProgressMs(state.progressMs, state.isPlaying && !state.isLoading, active)
    val inputMode = androidx.compose.ui.platform.LocalInputModeManager.current
    var chromeVisible by remember { mutableStateOf(true) }
    var lastInteractionMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showSongDestinations by remember(song?.id) { mutableStateOf(false) }
    var showLyricsMenu by remember { mutableStateOf(false) }
    var lyricsHidden by rememberSaveable { mutableStateOf(false) }
    // A portrait viewport overrides presentation without changing the user's normal choice.
    var preferredFullScreenLyrics by rememberSaveable { mutableStateOf(false) }
    var lyricsHiddenBeforeFullScreen by rememberSaveable { mutableStateOf(false) }
    val uiMotion = LocalUiMotion.current
    val chromeAlpha by animateFloatAsState(if (chromeVisible) 1f else 0f, tween(playerMotionDuration(uiMotion.enabled)), label = "playerChrome")
    val idle by animateFloatAsState(if (chromeVisible) 0f else 1f, tween(playerMotionDuration(uiMotion.enabled)), label = "playerIdle")

    DisposableEffect(active) {
        if (!active) return@DisposableEffect onDispose {}
        playerVm.nowPlayingVisible = true
        // 不再在进入"正在播放"时自动开始播放：用户手动按播放键时由 resume() 接管 pendingRestore。
        // Re-entering starts from a fresh chrome state instead of whatever was left parked.
        chromeVisible = true
        lastInteractionMs = System.currentTimeMillis()
        onDispose {
            playerVm.nowPlayingVisible = false
            navVm.setPlayerChromeVisible(true)
            navVm.fullScreenLyricsHasSplitOrigin = false
            // Reset while parked off-screen so the next entry starts from the centered frame
            // without visibly fading the already-settled layout back out.
            entryCentered = true
        }
    }
    LaunchedEffect(interaction) { lastInteractionMs = System.currentTimeMillis(); chromeVisible = true }
    val tabsFocused by navVm.tabsFocused.collectAsState()
    // A parked page must not keep its own auto-hide timer running.
    PlayerChromeAutoHide(song?.id, state.isLoading, state.isPlaying && active, lastInteractionMs,
        showSongDestinations || showLyricsMenu || !active) {
        chromeVisible = it
        navVm.setPlayerChromeVisible(it)
    }
    // Hiding the chrome also hides the capsule bar. If the capsule still owns the cursor at that
    // moment the pointer would be left on an invisible node, so park it on Play first.
    LaunchedEffect(chromeVisible, tabsFocused) {
        if (!chromeVisible && tabsFocused) scope.launch { focusScope.enter(PlayerMode.PlayButton) }
    }
    LaunchedEffect(chromeVisible) { navVm.setPlayerChromeVisible(chromeVisible) }
    // Transient queue closes first; the shell then returns to the exact source entry.

    // Background-only GLES2 integration; the approved foreground layout stays in place.
    BoxWithConstraints(
        modifier.fillMaxSize().background(Color.Black)
            // The wake-up gate belongs only to the player, never to the floating tabs.
            .onPreviewKeyEvent { event ->
                if(navVm.consumePlayerWakeKey(event.nativeKeyEvent)) true
                else {
                    if(event.type==KeyEventType.KeyDown && isNavKey(event.key)) {
                        lastInteractionMs=System.currentTimeMillis()
                        chromeVisible=true
                    }
                    false
                }
            },
    ) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight
        // Use the actual page viewport: every portrait size presents full-screen lyrics.
        val forceFullScreenLyrics = constraints.maxWidth > 0 &&
            constraints.maxHeight > constraints.maxWidth
        val fullScreenLyrics = forceFullScreenLyrics || preferredFullScreenLyrics
        // 全屏歌词进出（用户手动 preferred 或屏幕旋转 force）：scope 自动把焦点
        // 切到当前模式下的播放按钮。
        // 覆盖了原来 transferCoverFocus 时代没处理的三个缺口：C1(preferred 进全屏)、
        // C2(preferred 退出全屏)、F1(forceFullScreenLyrics 屏幕旋转)。
        LaunchedEffect(fullScreenLyrics) {
            if (fullScreenLyrics) scope.launch { focusScope.enter(PlayerMode.FullScreen) }
            else scope.launch { focusScope.enter(PlayerMode.Split) }
        }
        SideEffect { navVm.fullScreenLyricsHasSplitOrigin = preferredFullScreenLyrics && !forceFullScreenLyrics }
        // 顶部胶囊的 DOWN 落点：在当前模式（分屏/全屏歌词）下回到播放按钮。
        SideEffect {
            contentFocusLink.request = if (active) ({
                scope.launch { focusScope.enter(if (fullScreenLyrics) PlayerMode.FullScreen else PlayerMode.Split) }
            }) else null
        }
        DisposableEffect(Unit) { onDispose { contentFocusLink.request = null } }
        // Parked off-screen the page still composes, so every Back it registers must be inert:
        // otherwise it would swallow the Back meant for the page the user is actually on.
        androidx.activity.compose.BackHandler(enabled = active && !chromeVisible) { navVm.wakePlayerControls() }
        androidx.activity.compose.BackHandler(enabled = active && showQueue && chromeVisible && !fullScreenLyrics) { navVm.toggleQueuePanel() }
        FullScreenLyricsBackHandler(preferredFullScreenLyrics, forceFullScreenLyrics, enabled = active) {
            preferredFullScreenLyrics = false
            lyricsHidden = lyricsHiddenBeforeFullScreen
            navVm.wakePlayerControls()
        }
        LaunchedEffect(forceFullScreenLyrics) { showSongDestinations = false }
        val lyricsPanelVisible = !lyricsHidden && (fullScreenLyrics || !(showQueue && chromeVisible))
        LaunchedEffect(song?.id, state.lyricsNotice, state.lyricsNoticeGeneration, state.isLoading, fontState.ready, lyricsPanelVisible) {
            if (state.lyricsNotice == com.qqmusictv.ui.viewmodel.LyricsNoticePhase.Pending &&
                !state.isLoading && fontState.ready && state.lyrics.isEmpty() && lyricsPanelVisible &&
                !state.isLiveRadio && song?.isMusicVideo == false && state.lyricsNoticeSongId == song.id)
                playerVm.showEmptyLyricsNotice(song.id,state.lyricsNoticeGeneration)
        }
        FadeThrough(state, Modifier.fillMaxSize(), contentKey = { it.currentSong != null }) { state ->
            val song = state.currentSong
        if (song == null) {
            IdlePlayerBackground(state.fluidQuality,state.reduceMotion,state.lowPowerMode)
            Box(Modifier.fillMaxSize(),Alignment.Center) {PlayerStatusText(tr(R.string.msg_279),state.lyricsScale,Modifier)}
            return@FadeThrough
        }
        MacBackground(
            artworkUrl = song.artworkUrl(500), quality = state.fluidQuality,
            playing = state.isPlaying && active, reduceMotion = state.reduceMotion, lowPower = state.lowPowerMode,
            modifier = Modifier.fillMaxSize(),
        )
        // Remount when switching orientation so the old split view cannot flash in portrait.
        // Playback, queue, progress and chrome timing remain outside this presentation key.
        key(forceFullScreenLyrics) {
        FadeThrough(fullScreenLyrics, Modifier.fillMaxSize()) { displayedFullScreen ->
            if (displayedFullScreen) {
                FullScreenLyrics(
                    lyrics = state.lyrics, progressState = smoothProgress, offsetMs = state.avLyricsMs,
                    song = state.currentSong ?: song, isPlaying = state.isPlaying, onSeek = { playerVm.player.seekTo(it) },
                    onPrev = playerVm::prev, onPlayPause = playerVm::togglePlayPause, onNext = playerVm::next,
                    lyricsScale = state.lyricsScale, chromeVisible = chromeVisible,
                    loading = state.isLoading && !state.isPlaying,
                    showEmptyLyrics = emptyLyricsNoticeVisible(state,state),
                    stationLaunch = state.stationLaunch,
                    lyricsHidden = lyricsHidden,
                    suppressPlayFocus = tabsFocused,
                    playFocusRequester = fullPlayFocus,
                    onSongInfoClick = { if (state.stationLaunch == null) showSongDestinations = !showSongDestinations },
                    songInfoMenu = {
                        SongDestinationsPopup(visible = showSongDestinations && state.stationLaunch == null,
                            song = song, playerVm = playerVm, onDismiss = { showSongDestinations = false },
                            onArtistClick = { showSongDestinations = false; onArtistClick(it) },
                            onAlbumClick = { showSongDestinations = false; onAlbumClick(it) })
                    },
                )
            } else {
                PlayerPlayFocusEffect(playFocus,chromeVisible, suppressInitialFocus = tabsFocused)
                val originalSide = (screenWidth * 0.075f).coerceIn(32.dp, 96.dp)
                val originalGap = (screenWidth * 0.07f).coerceIn(36.dp, 112.dp)
                val columnWidth = (screenWidth * 0.34f).coerceIn(260.dp, 380.dp)
                val coverSize = minOf(columnWidth, (screenWidth * 0.29f).coerceAtMost(360.dp),
                    (screenHeight - 316.dp).coerceAtLeast(120.dp))
                val spacing = playerColumnSpacing(screenWidth, originalSide, originalGap, coverSize)
                // Retain the former metadata height so smaller text adds breathing room without
                // moving the vertically centred cover or the neighbouring lyrics column.
                val metadataHeight = referenceTextHeight(song.title,
                    TextStyle(fontFamily = LocalTextStyle.current.fontFamily, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)) +
                    referenceTextHeight(song.artistName,
                        TextStyle(fontFamily = LocalTextStyle.current.fontFamily, fontSize = 14.sp, fontWeight = FontWeight.Normal)) + 10.dp
                val centeredControls=centerPlayerControls(state,lyricsHidden,showQueue&&chromeVisible,fontState.ready,artworkCentered) || entryCentered
                PlayerSplitLayout(screenWidth,coverSize,spacing,
                    centered=centeredControls,
                    controls={
                    Column(
                        Modifier.width(coverSize).graphicsLayer {
                            translationY = idle * 36.dp.toPx()
                        },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.size(coverSize)
                                .clip(RoundedCornerShape(if (state.artworkRounded) 10.dp else 0.dp))
                                .background(Color(0xFF181818)),
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current).data(song.artworkUrl(900))
                                    .crossfade(675).build(),
                                contentDescription = song.title, contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                            if (state.motionArtworkEnabled && !state.lowPowerMode) state.motionUrl?.let { url ->
                                com.qqmusictv.ui.components.MotionArtwork(
                                    url, play = state.isPlaying, modifier = Modifier.fillMaxSize(),
                                    startDelayMs = 250L, maxVideoSize = 640,
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth().heightIn(min = metadataHeight).graphicsLayer { alpha = contentAlpha }, verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(36.dp)) {
                                FadeVisibility(chromeVisible, durationMs = PlayerMotionDurationMs) { PlayerAccessoryButton(Glyph.QUEUE, if (showQueue) tr(R.string.msg_141) else tr(R.string.msg_280), showQueue,
                                    visualScale = UnderCoverVisualScale) {
                                    navVm.toggleQueuePanel()
                                } }
                            }
                            Box(Modifier.weight(1f)) {
                                SongInfoFocusSurface(
                                    onClick = { if (state.stationLaunch == null) showSongDestinations = !showSongDestinations },
                                    chromeAlpha = chromeAlpha,
                                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = tr(R.string.msg_281, song.title, song.artistName) },
                                ) {
                                    FadeThrough(song, Modifier.fillMaxWidth(), contentKey = { it.id }) { song ->
                                        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally) {
                                            // tvOS Title3（24dp）：正在播放歌名字号
                                            MarqueeText(song.title, fontSize = 20.sp * UnderCoverVisualScale, fontWeight = FontWeight.SemiBold,
                                                color = Color.White, modifier = Modifier.fillMaxWidth())
                                            MarqueeText(song.artistName, fontSize = 14.sp * UnderCoverVisualScale, color = PlayerSecondaryColor,
                                                modifier = Modifier.fillMaxWidth().padding(top = 2.dp))
                                        }
                                    }
                                }
                                SongDestinationsPopup(
                                    visible = showSongDestinations && state.stationLaunch == null,
                                    song = song, playerVm = playerVm,
                                    onDismiss = { showSongDestinations = false },
                                    onArtistClick = { showSongDestinations = false; onArtistClick(it) },
                                    onAlbumClick = { showSongDestinations = false; onAlbumClick(it) },
                                )
                            }
                            Box(Modifier.size(36.dp)) {
                                FadeVisibility(chromeVisible, durationMs = PlayerMotionDurationMs) {
                                    LyricsOptionsButton(lyricsHidden,showLyricsMenu,{showLyricsMenu=it},
                                        onToggleLyrics={lyricsHidden=!lyricsHidden;navVm.closeQueuePanel()},
                                        onFullScreen={lyricsHiddenBeforeFullScreen=lyricsHidden;lyricsHidden=false;navVm.closeQueuePanel();preferredFullScreenLyrics=true},
                                        visualScale=UnderCoverVisualScale)
                                }
                            }
                        }
                        Spacer(Modifier.height(0.dp))
                        Column(Modifier.fillMaxWidth().graphicsLayer { alpha = chromeAlpha * contentAlpha }) {
                            PlayerProgressBar(smoothProgress, song, playFocus, playerVm.player,
                                visualScale = UnderCoverVisualScale, loading = state.isLoading && !state.isPlaying)
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                PlayerFavoriteButton(enabled=state.stationLaunch==null,visualScale=UnderCoverVisualScale) {playerVm.addToLibrary(song)}
                                TransportButton(TransportIcon.Prev, playerVm::prev, visualScale = UnderCoverVisualScale, inactiveColor = PlayerControlInactiveColor)
                                TransportButton(if (state.isPlaying) TransportIcon.Pause else TransportIcon.Play,
                                    playerVm::togglePlayPause, large = true, loading = state.isLoading && !state.isPlaying,
                                    active = state.isPlaying,
                                    modifier = Modifier.focusRequester(playFocus), visualScale = UnderCoverVisualScale, inactiveColor = PlayerControlInactiveColor)
                                TransportButton(TransportIcon.Next, playerVm::next, visualScale = UnderCoverVisualScale, inactiveColor = PlayerControlInactiveColor)
                                PlaybackOrderButton(state.playbackOrder(),visualScale=UnderCoverVisualScale,
                                    onClick=playerVm::cyclePlaybackOrder)
                            }
                        }
                    }
                    }, panel={
                        val panel = when {
                            state.stationLaunch != null -> "station"
                            showQueue && chromeVisible -> "queue"
                            lyricsHidden -> "hidden"
                            state.lyrics.isNotEmpty() -> "lyrics"
                            else -> "empty"
                        }
                        val currentLyricsState = state
                        val emptyPanelIsCurrent = panel == "empty"
                        FadeThrough(panel to state, Modifier.fillMaxSize().graphicsLayer { alpha = contentAlpha }, contentKey = { it.first }) { (panel, state) ->
                            when {
                                panel == "hidden" -> Unit
                                panel == "station" -> StationLaunchMessage(state.stationLaunch?.failed == true, state.lyricsScale)
                                panel == "queue" -> QueuePanel(
                                    state.queue, state.queueIndex, state.userQueue,
                                    onSelect = playerVm::playFromQueue,
                                    onSelectUserQueue = playerVm::playFromUserQueue,
                                    onMove = playerVm::moveQueueItem, leftFocus = playFocus,
                                )
                                else -> com.qqmusictv.ui.components.FontGatedLyrics(fontState, fontScale = state.lyricsScale) {
                                if (panel == "lyrics") LyricsPanel(
                                    lyrics = state.lyrics, progressState = smoothProgress, offsetMs = state.avLyricsMs,
                                    onSeek = { playerVm.player.seekTo(it) }, playFocus = playFocus,
                                    fontScale = state.lyricsScale,
                                )
                                else if (panel == "empty" && !state.isLoading) EmptyLyrics(song.id, state.lyricsScale, emptyPanelIsCurrent && emptyLyricsNoticeVisible(state,currentLyricsState), centered=centeredControls)
                                }
                            }
                        }
                    })
            }
        }
        }
        NextUpToast(
            smoothProgress, song.durationMs,
            (state.userQueue.firstOrNull() ?: state.queue.getOrNull(state.queueIndex + 1))?.title, song.id, visible = chromeVisible,
        )
        }
    }
}

/** Keep the song information readable while its focus backing follows control visibility. */

@Composable
internal fun SongInfoFocusSurface(chromeAlpha: Float, onClick: () -> Unit,
    modifier: Modifier = Modifier, compact: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Surface(onClick = onClick, onLongClick = onClick, onContextMenu = onClick, modifier = modifier.onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent, pressedContainerColor = Color.Transparent),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        glow = ClickableSurfaceDefaults.glow(glow = Glow.None, focusedGlow = Glow.None, pressedGlow = Glow.None)) {
        val bounds = if (compact) Modifier.height(64.dp) else Modifier.fillMaxWidth()
        Box(bounds.background(
            Color.White.copy(alpha = if (focused) (0x1A / 255f) * chromeAlpha.coerceIn(0f, 1f) else 0f), shape)
            .then(if (compact) Modifier.padding(horizontal = 12.dp) else Modifier),
            contentAlignment = if (compact) Alignment.CenterStart else Alignment.TopStart,
            content = content)
    }
}

/** Idle time (no D-pad navigation) before the Now Playing chrome auto-hides (then a 2 s settle). */

/** D-pad navigation / select keys — the ones allowed to wake the auto-hidden chrome. Media transport
 *  keys are deliberately excluded so play/pause/skip don't bring the controls back. */
private fun isNavKey(key: Key): Boolean =
    key == Key.DirectionUp || key == Key.DirectionDown ||
        key == Key.DirectionLeft || key == Key.DirectionRight ||
        key == Key.DirectionCenter || key == Key.Enter || key == Key.NumPadEnter

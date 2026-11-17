package com.qqmusictv.ui

import com.qqmusictv.i18n.tr
import com.qqmusictv.R

import com.qqmusictv.ui.navigation.contentFocusExitPolicy

import com.qqmusictv.ui.components.FadeThrough
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import com.qqmusictv.ui.components.UiMotion
import com.qqmusictv.ui.components.LocalUiMotion
import com.qqmusictv.ui.components.motionInput
import android.app.Activity
import android.view.WindowManager
import com.qqmusictv.ui.navigation.RootPageBackHandler
import com.qqmusictv.ui.navigation.NavigationReturnState
import com.qqmusictv.ui.navigation.LocalNavigationReturn
import com.qqmusictv.ui.navigation.returningComposable
import com.qqmusictv.ui.navigation.openDetail
import com.qqmusictv.ui.navigation.returnToPlayerAfterPlayback
import com.qqmusictv.ui.navigation.selectTab
import com.qqmusictv.ui.navigation.route
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.qqmusictv.ui.viewmodel.shellUiState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.qqmusictv.ui.components.TopNavBar
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import com.qqmusictv.ui.navigation.BrowsingPageMotion
import com.qqmusictv.ui.navigation.Screen
import com.qqmusictv.ui.navigation.TopNavTab
import com.qqmusictv.ui.screens.*
import java.net.URLDecoder
import com.qqmusictv.ui.viewmodel.LibraryViewModel
import com.qqmusictv.ui.viewmodel.NavigationViewModel
import com.qqmusictv.ui.viewmodel.PlayerViewModel
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import com.qqmusictv.data.model.Song
import androidx.compose.material3.Text

// Tab switches are instant (no slide, no snapshot): a full-screen PixelCopy plus a second
// full-screen bitmap layer cost ~700–800ms in the draw phase on this device's GPU, while the
// page rebuild itself is nearly free once warm.

@Composable
fun AppShell(modifier: Modifier = Modifier, onStartupReady: () -> Unit = {}) {
    val navController = rememberNavController()
    val returnState = remember(navController) { NavigationReturnState(navController) }
    var selectedTab   by remember { mutableStateOf(TopNavTab.Browse) }
    // The pill must leave on the keypress, not when the snapshot copy and navigation land:
    // show the requested tab immediately and let the route catch up.
    var pendingTab    by remember { mutableStateOf<TopNavTab?>(null) }
    val playerVm: PlayerViewModel  = hiltViewModel()
    val navVm: NavigationViewModel = hiltViewModel()
    val tabFocus = remember { TopNavTab.entries.associateWith { FocusRequester() } }
    var focusedTopTab by remember { mutableStateOf<TopNavTab?>(null) }
    var navBarHasFocus by remember { mutableStateOf(false) }
    val libraryContentFocus = remember { FocusRequester() }
    fun requestTabFocus(tab: TopNavTab) {
        returnState.cancel()
        selectedTab = tab
        tabFocus.getValue(tab).requestFocus()
    }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val inputModeManager = androidx.compose.ui.platform.LocalInputModeManager.current
    val contentFocusHandoff = com.qqmusictv.ui.navigation.rememberNavigationFocusHandoff(navController, returnState)
    val profileVm: com.qqmusictv.ui.viewmodel.UserProfileViewModel = hiltViewModel()
    val avatarUrl by profileVm.avatar.collectAsState()
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, profileVm) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) profileVm.onResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val contentFocus = remember { FocusRequester() }
    // 播放页常驻层的焦点落点：顶部胶囊按 DOWN 时把光标交回播放按钮，而不是 park 在屏幕外的 NavHost。
    val playerContentFocusLink = remember { com.qqmusictv.ui.screens.PlayerContentFocusLink() }
    // Keep library identity across tabs; its cache/network work starts on Library entry only.
    val libraryVm: LibraryViewModel = hiltViewModel()
    // Hoisted so a server re-check from the Dev menu can refetch both screens —
    // reconnecting is useless if the stale "server down" content stays on screen.
    val homeVm: com.qqmusictv.ui.viewmodel.HomeViewModel = hiltViewModel()

    // First run: setup owns the whole screen. Nothing behind it is usable until the
    // server and token are configured, so there is no nav bar and nothing to browse.
    val onboardingVm: com.qqmusictv.ui.viewmodel.OnboardingViewModel = hiltViewModel()
    var showOnboarding by remember { mutableStateOf(!playerVm.onboardingCompleted()) }
    var postLoginHomeReturnPending by rememberSaveable { mutableStateOf(false) }
    var postLoginHomeScrollPending by rememberSaveable { mutableStateOf(false) }
    val authorizationRevision by playerVm.authorizationRevision.collectAsState()
    val warmPages = !showOnboarding || authorizationRevision > 0L
    val browseVm: BrowseViewModel? = if (warmPages) hiltViewModel<BrowseViewModel>() else null
    val searchVm: com.qqmusictv.ui.viewmodel.SearchViewModel? = if (warmPages) hiltViewModel<com.qqmusictv.ui.viewmodel.SearchViewModel>() else null
    // Authorization arrival, not leaving the login screen, starts each root page's first load.
    LaunchedEffect(authorizationRevision, browseVm, searchVm) {
        if (authorizationRevision > 0L) {
            homeVm.load(clearForLogin = true).invokeOnCompletion { cause -> if (cause == null) postLoginHomeReturnPending = true }
            browseVm?.load(force = true)
            libraryVm.preloadAfterLogin()
        }
    }
    // "重新设置" in Settings resets the pref and fires this — bring setup up right away
    // instead of only on the next launch.
    val replay by playerVm.replayOnboarding.collectAsState()
    LaunchedEffect(replay) {
        if (replay) { showOnboarding = true; playerVm.consumeReplayOnboarding() }
    }
    val initialHome by homeVm.state.collectAsState()
    val startupCanDismiss = showOnboarding || initialHome.sections.isNotEmpty() || !initialHome.isLoading
    LaunchedEffect(startupCanDismiss) {
        if (startupCanDismiss) { withFrameNanos { }; onStartupReady() }
    }
    val uiMotion by remember(playerVm) {
        playerVm.state.map { state ->
            UiMotion(enabled = !state.reduceMotion)
        }.distinctUntilChanged()
    }.collectAsState(UiMotion(enabled = !playerVm.state.value.reduceMotion))
    com.qqmusictv.ui.components.PageLayout {
    CompositionLocalProvider(LocalUiMotion provides uiMotion, LocalNavigationReturn provides returnState) {
    FadeThrough(showOnboarding, Modifier.fillMaxSize()) { displayedSetup ->
    if (displayedSetup) {
        OnboardingScreen(
            vm = onboardingVm,
            onDone = {
                showOnboarding = false
                playerVm.onOnboardingFinished()
                // Observe this login's actual load completion, not the pre-login cached feed.
                if (authorizationRevision == 0L) {
                    homeVm.load().invokeOnCompletion { cause ->
                        if (cause == null) postLoginHomeReturnPending = true
                    }
                    libraryVm.refresh()
                }
            },
            modifier = modifier,
        )
        return@FadeThrough
    }

    // Transient errors surface as a toast: a TV user is across the room and won't
    // find an inline banner, and silence reads as a crash.
    val appContext = LocalContext.current.applicationContext
    LaunchedEffect(Unit) {
        playerVm.toasts.collect { msg ->
            android.widget.Toast.makeText(appContext, com.qqmusictv.i18n.AppLanguage.message(msg), android.widget.Toast.LENGTH_LONG).show()
        }
    }

    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route
    LaunchedEffect(postLoginHomeReturnPending, initialHome.isLoading, initialHome.sections.isNotEmpty()) {
        if (postLoginHomeReturnPending && !initialHome.isLoading && initialHome.sections.isNotEmpty()) {
            navController.navigate(Screen.Browse.route) {
                popUpTo(navController.graph.findStartDestination().id)
                launchSingleTop = true
            }
            postLoginHomeScrollPending = true
            requestTabFocus(TopNavTab.Browse)
            postLoginHomeReturnPending = false
        }
    }
    var tabFocusEntryId by remember { mutableIntStateOf(0) }
    val isOnNowPlaying = currentRoute == Screen.NowPlaying.route
    // On Now Playing the tab bar rides the player chrome: visible on entry and on wake,
    // auto-hidden together with the transport controls after the idle timeout.
    val playerChromeVisible by navVm.playerChromeVisible.collectAsState()
    val tabBarVisible = !isOnNowPlaying || playerChromeVisible
    val destinationTab = returnState.owner(currentEntry)
    // Once the route reaches the tab the pill already moved to, the route owns the pill again.
    LaunchedEffect(destinationTab) { if (pendingTab == destinationTab) pendingTab = null }
    val idleLifecycle = remember { kotlinx.coroutines.flow.MutableStateFlow(androidx.lifecycle.Lifecycle.State.INITIALIZED) }
    val entryLifecycle by (currentEntry?.lifecycle?.currentStateFlow ?: idleLifecycle).collectAsState()
    // Route and actual focus share the same stable per-tab targets. The capsule bar
    // is always mounted (alpha-gated, not AnimatedVisibility) so its focus nodes are
    // never destroyed — no retry loop needed to restore focus after a remount.
    LaunchedEffect(isOnNowPlaying) {
        if (isOnNowPlaying) { navBarHasFocus = false; focusedTopTab = null }
    }
    val browsingRoot = currentRoute in setOf(Screen.Browse.route,
        Screen.Library.route, Screen.Search.route)

    // The player page is kept composed behind the NavHost once visited. Its destination is an
    // empty placeholder, so re-entering costs a 300ms slide instead of re-composing the whole
    // page (~700ms) and swallowing the keypresses that arrive during that frame.
    val tabRoots = setOf(Screen.Browse.route, Screen.Library.route,
        Screen.Search.route, Screen.DevMenu.route)
    // The four tab roots are kept composed for the whole session, exactly like Bolt IPTV keeps
    // its pages alive and only flips which one is visible. Rebuilding one here cost 700–860ms of
    // main-thread work in a single frame, which squeezed the 300ms capsule slide into two frames
    // — the reason a tab switch read as "no animation". A switch now only flips which page draws.
    val activeTabRoot = when (currentRoute) {
        Screen.Browse.route -> TopNavTab.Browse
        Screen.Library.route -> TopNavTab.Library
        Screen.Search.route -> TopNavTab.Search
        Screen.DevMenu.route -> TopNavTab.Dev
        else -> null
    }
    // Joined on first visit, so start-up does not compose four pages at once.
    val mountedTabs = remember { androidx.compose.runtime.mutableStateMapOf(TopNavTab.Browse to true) }
    LaunchedEffect(activeTabRoot) { activeTabRoot?.let { mountedTabs[it] = true } }
    // 启动页消失后，在后台把其余 tab 依次挂载一遍。这样用户第一次切到任意 tab 时，
    // 页面早已 compose/measure 完毕，不会出现切 tab 卡顿。错开时间避免一次性重组冲击当前 Browse 页。
    LaunchedEffect(startupCanDismiss) {
        if (!startupCanDismiss) return@LaunchedEffect
        val rest = listOf(TopNavTab.Library, TopNavTab.Search, TopNavTab.Dev)
        for ((i, tab) in rest.withIndex()) {
            kotlinx.coroutines.delay(250L * (i + 1))
            mountedTabs[tab] = true
        }
    }
    // While a detail page is on top the layer fades out; keep drawing the page it came from.
    var lastTabRoot by remember { mutableStateOf(TopNavTab.Browse) }
    LaunchedEffect(activeTabRoot) { activeTabRoot?.let { lastTabRoot = it } }
    val drawnTab = activeTabRoot ?: lastTabRoot
    // The window's first focus has to land on a page, so Browse must be focusable during the
    // single frame before the NavHost resolves its start route — otherwise the session starts
    // with no focused node and every key is swallowed.
    val tabFocusOwner = activeTabRoot ?: if (currentRoute == null) TopNavTab.Browse else null
    val tabLayerAlpha = remember {
        androidx.compose.animation.core.Animatable(if (activeTabRoot != null) 1f else 0f)
    }
    LaunchedEffect(activeTabRoot, isOnNowPlaying) {
        tabLayerAlpha.animateTo(
            if (activeTabRoot != null || isOnNowPlaying) 1f else 0f,
            tween(if (uiMotion.enabled) uiMotion.exitMs else 0),
        )
    }
    val tabContentFocus = remember { FocusRequester() }
    val browseReturnPage = remember { com.qqmusictv.ui.navigation.ReturnPageState() }
    val libraryReturnPage = remember { com.qqmusictv.ui.navigation.ReturnPageState() }
    val searchReturnPage = remember { com.qqmusictv.ui.navigation.ReturnPageState() }
    val devReturnPage = remember { com.qqmusictv.ui.navigation.ReturnPageState() }
    var playerLayerMounted by remember { mutableStateOf(isOnNowPlaying) }
    var playerEntryReplay by remember { mutableStateOf(true) }
    // One focus-memory page for the player for the whole session, shared with its placeholder
    // destination. It must keep its identity when the entry pops: swapping the provider back to
    // null would invalidate the parked page and undo the point of keeping it composed.
    val playerReturnPage = remember { com.qqmusictv.ui.navigation.ReturnPageState() }
    // The player layer is drawn ABOVE the NavHost: those pages paint no backdrop of their own
    // (they share the shell colour), so a layer underneath would bleed through them. 1 = home,
    // 0 = parked a full width to the left.
    val playerSlide = remember { androidx.compose.animation.core.Animatable(if (isOnNowPlaying) 1f else 0f) }
    // Under a detail page: the page crossfades in over this layer, so it fades out with it.
    val playerAlpha = remember { androidx.compose.animation.core.Animatable(1f) }
    // NavHost offset in widths: the incoming page slides in from the right as the player slides
    // out left, and back out again as the player slides home.
    val navShift = remember { androidx.compose.animation.core.Animatable(if (isOnNowPlaying) 1f else 0f) }
    val playerTransition = tween<Float>(
        durationMillis = if (uiMotion.enabled) BrowsingPageMotion.tabSlideMs else 0,
        easing = BrowsingPageMotion.pageEasing,
    )
    val playerCrossfadeMs = if (uiMotion.enabled) 300 else 0
    var previousRoute by remember { mutableStateOf(currentRoute) }
    val playerStackEntry = remember(currentRoute) {
        runCatching { navController.getBackStackEntry(Screen.NowPlaying.route) }.getOrNull()
    }
    LaunchedEffect(currentRoute) {
        val from = previousRoute
        val to = currentRoute
        // Both layers move on the same progress: one keypress must never show a half-page.
        suspend fun slideTo(target: Float): Unit = kotlinx.coroutines.coroutineScope {
            launch { navShift.animateTo(target, playerTransition) }
            launch { playerSlide.animateTo(target, playerTransition) }
            Unit
        }
        if (to == Screen.NowPlaying.route) {
            playerLayerMounted = true
            if (from != Screen.NowPlaying.route) {
                val fromTab = from in tabRoots
                playerEntryReplay = fromTab
                if (fromTab) {
                    // The tab page is carried out by the push snapshot, so the player's own slide
                    // is what the user reads as the incoming page.
                    playerAlpha.snapTo(1f)
                    playerSlide.snapTo(0f)
                    navShift.snapTo(0f)
                    slideTo(1f)
                } else {
                    // Back from a detail: it crossfades out of the way while this page fades in.
                    playerSlide.snapTo(1f)
                    playerAlpha.animateTo(1f, tween(playerCrossfadeMs))
                    kotlinx.coroutines.delay((playerCrossfadeMs + 20).toLong())
                    navShift.snapTo(1f)
                }
            }
        } else if (from == Screen.NowPlaying.route) {
            if (to in tabRoots) {
                slideTo(0f)
            } else {
                // A detail crossfades in: fade this page out, then park it behind the detail.
                navShift.snapTo(0f)
                playerAlpha.animateTo(0f, tween(playerCrossfadeMs))
                playerSlide.snapTo(0f)
            }
        } else {
            // Neither side is the player: leave it parked. Snapping also repairs an exit that a
            // second rapid keypress cancelled halfway.
            playerSlide.snapTo(0f)
            playerAlpha.snapTo(1f)
            navShift.snapTo(0f)
        }
        previousRoute = to
    }
    LaunchedEffect(Unit) {
        playerVm.userPlaybackRequests.drop(1).collect {
            navVm.notePlayerInteraction()
            if (returnState.returnToPlayerAfterPlayback()) {
                selectedTab = TopNavTab.NowPlaying
                navVm.consumeNowPlayingNavigation()
                navVm.wakePlayerControls()
            } else navVm.navigateToNowPlaying()
        }
    }
    // Keep the nav-bar highlight in sync with the ACTUAL route — otherwise popping back from
    // a pushed screen (e.g. artist opened from the video player) left the white pill stuck on
    // Now Playing. Detail routes leave the pill on whatever top tab we came from.
    LaunchedEffect(currentRoute) {
        when (currentRoute) {
            Screen.Browse.route    -> selectedTab = TopNavTab.Browse
            Screen.Library.route   -> selectedTab = TopNavTab.Library
            Screen.Search.route    -> selectedTab = TopNavTab.Search
            Screen.NowPlaying.route -> selectedTab = TopNavTab.NowPlaying
            Screen.DevMenu.route   -> selectedTab = TopNavTab.Dev
            else -> {}
        }
    }
    LaunchedEffect(isOnNowPlaying) { navVm.isOnNowPlaying = isOnNowPlaying }

    // Keep the screen awake ONLY while music is actually playing. When paused, drop the
    // flag so Fire TV's own screensaver / sleep can take over (our ambient screensaver only
    // ever runs while playing).
    // Shell navigation does not render the playback clock. Ignore progress-only updates so
    // the entire navigation host is not invalidated five times a second during playback.
    val shellState = remember(playerVm) {
        playerVm.state.map { it.shellUiState() }.distinctUntilChanged()
    }
    val playerState by shellState.collectAsStateWithLifecycle(playerVm.state.value.shellUiState())
    val keepScreenOn = playerState.isPlaying
    val activity = LocalContext.current as? Activity
    DisposableEffect(keepScreenOn) {
        if (keepScreenOn) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    val goToNowPlaying by navVm.goToNowPlaying.collectAsState()
    LaunchedEffect(goToNowPlaying) {
        if (goToNowPlaying) {
            selectedTab = TopNavTab.NowPlaying
            if (navController.currentDestination?.route != Screen.NowPlaying.route) {
                navController.navigate(Screen.NowPlaying.route) { launchSingleTop = true }
            }
            navVm.consumeNowPlayingNavigation()
        }
    }

    // Overlay layout: content fills the whole screen (so Now Playing's
    // background can run fullscreen to the very top), with the nav bar drawn
    // last → it sits on a higher layer. Non-fullscreen screens get top padding
    // equal to the bar so their content isn't hidden underneath it.

    var showExitDialog by remember { mutableStateOf(false) }
    BackHandler(enabled = showExitDialog) { showExitDialog = false }
    val returnToBrowse: () -> Unit = {
        requestTabFocus(TopNavTab.Browse)
        navController.navigate(Screen.Browse.route) {
            popUpTo(navController.graph.findStartDestination().id)
            launchSingleTop = true
        }
    }
    val returnFromPlayer: () -> Unit = {
        focusManager.clearFocus(force = true)
        returnState.back()
    }
    LaunchedEffect(returnState.returnToTab) {
        returnState.returnToTab?.let { requestTabFocus(it); returnState.returnToTab=null }
    }
    // Register root-page handlers INSIDE each NavHost destination, before its
    // content. This beats NavHost's pop handler while preserving child dialogs/IME.
    BackHandler(enabled = !showExitDialog &&
        currentRoute == Screen.Radio.route) {
        returnToBrowse()
    }

    // Keep a stable light underlay. The player destination paints its own black
    // canvas, which now enters/exits as part of the same NavHost crossfade.
    val shellColor = com.qqmusictv.ui.theme.AppPalette.background

    androidx.compose.runtime.CompositionLocalProvider(
        com.qqmusictv.ui.components.LocalMotionArtworkEnabled provides
            (playerState.motionArtworkEnabled && !playerState.lowPowerMode),
    ) {
    Box(modifier = modifier.fillMaxSize().background(shellColor)
        .onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown && event.key !in setOf(Key.Back, Key.Escape)) returnState.cancel()
            false
        }
        .pointerInput(returnState) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial)
                returnState.cancel()
            }
        }
        .pointerInput(isOnNowPlaying) {
            if (!isOnNowPlaying) return@pointerInput
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                navVm.notePlayerInteraction()
            }
        }) {
        // Persistent tab layer, BELOW the NavHost: every tab root stays composed, only the page
        // belonging to the current route draws. Details/player ride above it in the NavHost, so a
        // tab switch never composes a page and never eats the capsule animation's frames.
        Box(
            Modifier
                .fillMaxSize()
                .clipToBounds()
                .graphicsLayer {
                    translationX = navShift.value * size.width
                    alpha = tabLayerAlpha.value
                }
                // Skip the whole subtree's draw while parked: composition and scroll state stay,
                // nothing is recorded. Mirrors the player layer below the page it belongs to.
                .drawWithContent {
                    if (tabLayerAlpha.value > 0.005f &&
                        (activeTabRoot != null || playerSlide.value < 0.999f)) drawContent()
                }
                .focusRequester(tabContentFocus)
                .contentFocusExitPolicy(isOnNowPlaying, browsingRoot,
                    tabFocus.getValue(pendingTab ?: destinationTab), tabsReachable = tabBarVisible)
                .focusGroup(),
        ) {
            // Memoised page bodies: the shell recomposes several times per switch (pill, focus,
            // route), and an unmemoised lambda would re-run the whole page each time — that tail
            // of recompositions is what kept the capsule slide from ever reaching 60fps.
            val liveTab = androidx.compose.runtime.rememberUpdatedState(destinationTab)
            val browseContent: @androidx.compose.runtime.Composable () -> Unit = remember {
                {
                    BrowseScreen(
                        vm = requireNotNull(browseVm),
                        homeVm = homeVm,
                        restoreTopAfterLogin = postLoginHomeScrollPending,
                        onPostLoginTopRestored = { postLoginHomeScrollPending = false },
                        onReturnToTab = { requestTabFocus(liveTab.value) },
                        tabBarFocused = navBarHasFocus,
                        playerVm       = playerVm,
                        onAlbumClick   = { returnState.openDetail(Screen.AlbumDetail.route(it)) },
                        onPlaylistClick = { id, name, art -> returnState.openDetail(Screen.PlaylistDetail.route(id, name, art)) },
                        onGenreClick   = { id, name -> navController.navigate(Screen.Genre.route(id, name)) },
                        onCuratorClick = { navController.navigate(Screen.Category.route(it)) },
                        // "Find Your Mood" cards are already prefixed (ac-/c-/mr-) for CategoryScreen.
                        onCategoryClick = { navController.navigate(Screen.Category.route(it)) },
                        // "更多" at the end of a shelf → that shelf's full editorial room page.
                        onSeeAll       = { navController.navigate(Screen.Category.route("room-$it")) },
                    )
                }
            }
            val libraryContent: @androidx.compose.runtime.Composable () -> Unit = remember {
                {
                    LibraryScreen(
                        onReturnToTab = { requestTabFocus(liveTab.value) },
                        tabBarFocused = navBarHasFocus,
                        vm         = libraryVm,
                        playerVm   = playerVm,
                        contentFocusRequester = libraryContentFocus,
                        onAlbumClick    = { returnState.openDetail(Screen.AlbumDetail.route(it)) },
                        onPlaylistClick = { id, name, artworkUrl ->
                            returnState.openDetail(Screen.PlaylistDetail.route(id, name, artworkUrl ?: ""))
                        },
                        onArtistClick = { returnState.openDetail(Screen.ArtistDetail.route(it)) },
                    )
                }
            }
            val searchContent: @androidx.compose.runtime.Composable () -> Unit = remember {
                {
                    SearchScreen(
                        vm = requireNotNull(searchVm),
                        onReturnToTab = { requestTabFocus(liveTab.value) },
                        tabBarFocused = navBarHasFocus,
                        playerVm = playerVm,
                        onAlbumClick  = { returnState.openDetail(Screen.AlbumDetail.route(it)) },
                        onArtistClick = { returnState.openDetail(Screen.ArtistDetail.route(it)) },
                        onPlaylistClick = { id, name, artworkUrl ->
                            returnState.openDetail(Screen.PlaylistDetail.route(id, name, artworkUrl))
                        },
                        onCuratorClick = { id, kind ->
                            val prefix = when (kind) {
                                "multiroom"     -> "mr-"
                                "apple-curator" -> "ac-"
                                "room"          -> "room-"
                                "grouping"      -> "grouping-"
                                else            -> "c-"
                            }
                            navController.navigate(Screen.Category.route(prefix + id))
                        },
                    )
                }
            }
            val devContent: @androidx.compose.runtime.Composable () -> Unit = remember {
                {
                    DevMenuScreen(
                        playerVm = playerVm,
                        onDataRefresh = { homeVm.load(); libraryVm.refresh() },
                        onReturnToTab = { requestTabFocus(TopNavTab.Dev) },
                        tabBarFocused = navBarHasFocus,
                    )
                }
            }
            if (mountedTabs[TopNavTab.Browse] == true) {
                TabPage(active = tabFocusOwner == TopNavTab.Browse,
                    drawn = drawnTab == TopNavTab.Browse, page = browseReturnPage,
                    content = browseContent,
                    foreign = { activeTabRoot?.let { requestTabFocus(it) } })
            }
            if (mountedTabs[TopNavTab.Library] == true) {
                TabPage(active = tabFocusOwner == TopNavTab.Library,
                    drawn = drawnTab == TopNavTab.Library, page = libraryReturnPage,
                    content = libraryContent,
                    foreign = { activeTabRoot?.let { requestTabFocus(it) } })
            }
            if (mountedTabs[TopNavTab.Search] == true) {
                TabPage(active = tabFocusOwner == TopNavTab.Search,
                    drawn = drawnTab == TopNavTab.Search, page = searchReturnPage,
                    content = searchContent,
                    foreign = { activeTabRoot?.let { requestTabFocus(it) } })
            }
            if (mountedTabs[TopNavTab.Dev] == true) {
                TabPage(active = tabFocusOwner == TopNavTab.Dev,
                    drawn = drawnTab == TopNavTab.Dev, page = devReturnPage,
                    content = devContent,
                    foreign = { activeTabRoot?.let { requestTabFocus(it) } })
            }
        }
        // Player <-> browsing tab is animated by the shell (the page itself is a persistent
        // layer), so the NavHost must not add a second motion on top of it. Tab <-> tab is
        // instant: no slide, no crossfade, no snapshot.
        val instantPair: (String?, String?) -> Boolean = { a, b ->
            (a in tabRoots && b in tabRoots) ||
                (a == Screen.NowPlaying.route && b in tabRoots) ||
                (b == Screen.NowPlaying.route && a in tabRoots)
        }
        NavHost(
            navController    = navController,
            startDestination = Screen.Browse.route,
            enterTransition = { if (instantPair(initialState.destination.route, targetState.destination.route)) EnterTransition.None else BrowsingPageMotion.enter(initialState.destination.route, targetState.destination.route, uiMotion) },
            exitTransition = { if (instantPair(initialState.destination.route, targetState.destination.route)) ExitTransition.None else BrowsingPageMotion.exit(initialState.destination.route, targetState.destination.route, uiMotion) },
            popEnterTransition = { if (instantPair(initialState.destination.route, targetState.destination.route)) EnterTransition.None else BrowsingPageMotion.enter(initialState.destination.route, targetState.destination.route, uiMotion) },
            popExitTransition = { if (instantPair(initialState.destination.route, targetState.destination.route)) ExitTransition.None else BrowsingPageMotion.exit(initialState.destination.route, targetState.destination.route, uiMotion) },
            modifier         = Modifier
                .fillMaxSize()
                .clipToBounds()
                // Player transitions: the page slides in from the right as the player slides out
                // left, and aside again as the player slides home.
                .graphicsLayer { translationX = navShift.value * size.width }
                .focusRequester(contentFocus)
                .contentFocusExitPolicy(isOnNowPlaying, browsingRoot, tabFocus.getValue(pendingTab ?: destinationTab), tabsReachable = tabBarVisible)
                .focusGroup(),
        ) {
            // The four tab roots live in the shell's persistent layer above; these entries only
            // carry the back stack, the return address and back-press semantics.
            returningComposable(Screen.Browse.route, sharedPage = browseReturnPage) {
                RootPageBackHandler(
                    enabled = !showExitDialog && currentRoute == Screen.Browse.route,
                    isHome = true,
                    navBarHasFocus = navBarHasFocus,
                    onFocusTab = { requestTabFocus(destinationTab) },
                    onReturnHome = returnToBrowse,
                    onRequestExit = { showExitDialog = true },
                )
            }
            returningComposable(
                route     = Screen.Genre.route,
                arguments = listOf(
                    navArgument("genreId")   { type = NavType.StringType },
                    navArgument("genreName") { type = NavType.StringType },
                ),
            ) { back ->
                val gid  = URLDecoder.decode(back.arguments?.getString("genreId")   ?: "", "UTF-8")
                val gnm  = URLDecoder.decode(back.arguments?.getString("genreName") ?: "", "UTF-8")
                GenreScreen(
                    onReturnToTab = { requestTabFocus(destinationTab) },
                    tabBarFocused = navBarHasFocus,
                    genreId = gid, genreName = gnm, playerVm = playerVm,
                    onAlbumClick    = { returnState.openDetail(Screen.AlbumDetail.route(it)) },
                    onPlaylistClick = { id, name, art -> returnState.openDetail(Screen.PlaylistDetail.route(id, name, art)) },
                )
            }
            returningComposable(Screen.Category.route) {
                CategoryScreen(
                    onReturnToTab = { requestTabFocus(destinationTab) },
                    tabBarFocused = navBarHasFocus,
                    onAlbumClick    = { returnState.openDetail(Screen.AlbumDetail.route(it)) },
                    onPlaylistClick = { id, name, art -> returnState.openDetail(Screen.PlaylistDetail.route(id, name, art)) },
                    onCuratorClick  = { navController.navigate(Screen.Category.route(it)) },
                    onArtistClick   = { returnState.openDetail(Screen.ArtistDetail.route(it)) },
                    playerVm        = playerVm,
                )
            }
            returningComposable(Screen.Library.route, sharedPage = libraryReturnPage) {
                RootPageBackHandler(
                    enabled = !showExitDialog && currentRoute == Screen.Library.route,
                    isHome = false,
                    navBarHasFocus = navBarHasFocus,
                    onFocusTab = { requestTabFocus(destinationTab) },
                    onReturnHome = returnToBrowse,
                    onRequestExit = { showExitDialog = true },
                )
            }
            returningComposable(Screen.Search.route, sharedPage = searchReturnPage) {
                RootPageBackHandler(
                    enabled = !showExitDialog && currentRoute == Screen.Search.route,
                    isHome = false,
                    navBarHasFocus = navBarHasFocus,
                    onFocusTab = { requestTabFocus(destinationTab) },
                    onReturnHome = returnToBrowse,
                    onRequestExit = { showExitDialog = true },
                )
            }
            // The page itself lives in the shell's persistent player layer above; this entry only
            // carries the back stack, the wall-return payload and back-press semantics.
            returningComposable(Screen.NowPlaying.route, sharedPage = playerReturnPage) {
                BackHandler(enabled = !showExitDialog && isOnNowPlaying,
                    onBack = returnFromPlayer)
            }
            returningComposable(Screen.Radio.route) {
                RadioScreen(playerVm = playerVm, onReturnToTab = { requestTabFocus(destinationTab) })
            }
            // Settings is a transient visit, not a browsing root: Back keeps popping as before.
            returningComposable(Screen.DevMenu.route, sharedPage = devReturnPage) {
            }
            returningComposable(
                route     = Screen.AlbumDetail.route,
                arguments = listOf(navArgument("albumId") { type = NavType.StringType }),
            ) {
                AlbumDetailScreen(onReturnToTab = { requestTabFocus(destinationTab) }, tabBarFocused = navBarHasFocus, playerVm = playerVm, onBack = returnState::back,
                    onArtistClick = { returnState.openDetail(Screen.ArtistDetail.route(it)) },
                    onAlbumClick  = { returnState.openDetail(Screen.AlbumDetail.route(it)) })
            }
            returningComposable(
                route     = Screen.ArtistDetail.route,
                arguments = listOf(navArgument("artistId") { type = NavType.StringType }),
            ) {
                ArtistDetailScreen(
                    onReturnToTab = { requestTabFocus(destinationTab) },
                    tabBarFocused = navBarHasFocus,
                    playerVm = playerVm,
                    onAlbumClick  = { returnState.openDetail(Screen.AlbumDetail.route(it)) },
                    onArtistClick = { returnState.openDetail(Screen.ArtistDetail.route(it)) },
                )
            }
            returningComposable(
                route     = Screen.PlaylistDetail.route,
                arguments = listOf(
                    navArgument("playlistId")   { type = NavType.StringType },
                    navArgument("playlistName") { type = NavType.StringType },
                    navArgument("artworkUrl")   { type = NavType.StringType },
                ),
            ) { back ->
                val id         = URLDecoder.decode(back.arguments?.getString("playlistId")   ?: "", "UTF-8")
                val name       = URLDecoder.decode(back.arguments?.getString("playlistName") ?: "", "UTF-8")
                val artworkUrl = URLDecoder.decode(back.arguments?.getString("artworkUrl")   ?: "", "UTF-8")
                PlaylistDetailScreen(
                    onReturnToTab = { requestTabFocus(destinationTab) },
                    tabBarFocused = navBarHasFocus,
                    playlistId    = id,
                    playlistName  = name,
                    artworkUrl    = artworkUrl.ifEmpty { null },
                    playerVm      = playerVm,
                    onBack        = returnState::back,
                    onArtistClick = { returnState.openDetail(Screen.ArtistDetail.route(it)) },
                    onAlbumClick  = { returnState.openDetail(Screen.AlbumDetail.route(it)) },
                )
            }
        }
        // Persistent player layer, ABOVE the NavHost: the browsing and detail pages paint no
        // backdrop of their own (they share the shell colour), so a layer underneath would bleed
        // through them. Parked a full width off the left edge — never merely transparent — so
        // focus search can never land on it while another page owns the screen.
        if (playerLayerMounted) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = (playerSlide.value - 1f) * size.width
                        alpha = playerAlpha.value
                    }
                    // canFocus=false also removes the whole subtree from focus search, so a parked
                    // player can never steal the cursor from the page the user is on.
                    .focusProperties { canFocus = isOnNowPlaying },
            ) {
                // The player is outside the NavHost, so the shell supplies the focus-memory page
                // that its placeholder destination would otherwise provide. Without it, coming
                // back from a detail would land on the capsule instead of the card that opened it.
                androidx.compose.runtime.CompositionLocalProvider(
                    com.qqmusictv.ui.navigation.LocalReturnPage provides playerReturnPage,
                    com.qqmusictv.ui.navigation.LocalReturnPath provides emptyList(),
                ) {
                    NowPlayingScreen(
                        playerVm = playerVm,
                        navVm = navVm,
                        active = isOnNowPlaying,
                        entryReplay = playerEntryReplay,
                        contentFocusLink = playerContentFocusLink,
                        onArtistClick = { returnState.openDetail(Screen.ArtistDetail.route(it), fromPlayer = true) },
                        onAlbumClick  = { returnState.openDetail(Screen.AlbumDetail.route(it), fromPlayer = true) },
                        onPlaylistClick = { id, name, art -> returnState.openDetail(Screen.PlaylistDetail.route(id, name, art ?: ""), fromPlayer = true) },
                    )
                }
            }
        }
        // MUT expired banner
        if (playerState.mutExpired) {
            Box(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 72.dp).fillMaxWidth(0.5f)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                    .background(androidx.compose.ui.graphics.Color(0xFFB22222))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.material3.Text(tr(R.string.msg_002), color = androidx.compose.ui.graphics.Color.White, fontSize = 13.sp)
                    com.qqmusictv.ui.components.TouchSurface(
                        onClick = { playerVm.dismissMutExpired() },
                        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(androidx.compose.foundation.shape.RoundedCornerShape(50)),
                        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(containerColor = androidx.compose.ui.graphics.Color(0x33FFFFFF), focusedContainerColor = androidx.compose.ui.graphics.Color(0x55FFFFFF)),
                    ) { androidx.compose.foundation.layout.Box(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                        com.qqmusictv.ui.components.Icon(com.qqmusictv.ui.components.Glyph.CLOSE, size = 13.dp, color = androidx.compose.ui.graphics.Color.White)
                    } }
                }
            }
        }

        // Preview mode banner
        if (!playerState.isFullStream && playerState.currentSong != null) {
            Box(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .background(androidx.compose.ui.graphics.Color(0xFFB22222))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                androidx.compose.material3.Text(
                    tr(R.string.msg_003),
                    color = androidx.compose.ui.graphics.Color.White,
                    fontSize = 11.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                )
            }
        }

        // Exit dialog
        FadeThrough(showExitDialog, contentKey = { if (it) true else null }) { visible ->
            if (visible) {
                com.qqmusictv.ui.components.ExitAppDialog(onDismiss = { showExitDialog = false }) {
                    showExitDialog = false
                    playerVm.stopPlayback()
                    playerVm.clearVideoRequest()
                    if (activity?.moveTaskToBack(true) != true) activity?.finish()
                }
            }
        }


        // Capsule bar stays mounted forever — only alpha changes — so focus nodes
        // survive player transitions. Fade matches Apple Music TV's overlay style.
        val tabBarAlpha by animateFloatAsState(
            if (tabBarVisible) 1f else 0f,
            animationSpec = tween(
                if (uiMotion.enabled) com.qqmusictv.ui.navigation.PlayerEntryMotion.navEnterMs else 0,
                easing = com.qqmusictv.ui.navigation.PlayerEntryMotion.easing,
            ),
            label = "tabBarAlpha",
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                // A bar that still owns focus must never become non-focusable: doing so dropped
                // the cursor into the player content (the play button) mid-navigation.
                .motionInput(tabBarVisible || navBarHasFocus)
                .graphicsLayer { alpha = tabBarAlpha }
                .onFocusChanged {
                    // Raw focus (not gated on visibility) keeps the player chrome awake while the
                    // capsule owns the cursor, so the bar cannot hide out from under it.
                    navVm.setTabsFocused(it.hasFocus)
                    val hasFocus = it.hasFocus && tabBarVisible && !contentFocusHandoff
                    if (hasFocus && !navBarHasFocus && inputModeManager.inputMode == androidx.compose.ui.input.InputMode.Keyboard)
                        tabFocusEntryId++
                    navBarHasFocus = hasFocus
                }
                .focusProperties {
                    onEnter = { if (!contentFocusHandoff && inputModeManager.inputMode == androidx.compose.ui.input.InputMode.Keyboard)
                        tabFocus.getValue(pendingTab ?: destinationTab).requestFocus() }
                }.focusGroup(),
        ) {
            TopNavBar(
                selected = pendingTab ?: destinationTab,
                focusEntryId = tabFocusEntryId,
                navigationHasFocus = navBarHasFocus && !contentFocusHandoff,
                focusEnabled = !contentFocusHandoff,
                avatarUrl = avatarUrl,
                onAvatarLoadError = profileVm::onAvatarDisplayError,
                tabFocusRequesters = tabFocus,
                onTabFocusChanged = { tab, focused ->
                    if (focused && tabBarVisible) focusedTopTab = tab else if (focusedTopTab == tab) focusedTopTab = null
                    // Browsing the tabs on top of the player counts as activity: keep the chrome
                    // (and with it the tab bar) awake while the user moves across them.
                    if (focused && isOnNowPlaying) navVm.wakePlayerControls()
                },
                contentFocusRequester = when (currentRoute) {
                    Screen.Library.route -> libraryContentFocus
                    Screen.Browse.route, Screen.Search.route, Screen.DevMenu.route -> tabContentFocus
                    else -> contentFocus
                },
                // 播放页没有可聚焦的内容容器，DOWN 回调进播放页把光标送回播放按钮。
                onDown = {
                    val request = playerContentFocusLink.request
                    if (isOnNowPlaying && request != null) { request(); true } else false
                },
                // Restore the original V12 beat-reactive marker for music and video.
                isPlaying = playerState.isPlaying,
                beatAnalyzer = playerVm.beatAnalyzer,
                onSelect = { tab ->
                    // Focus can move again before the next composition. Read the live
                    // destination so rapid arrows never select against a stale route.
                    val activeRoute = navController.currentDestination?.route
                    if (returnState.contentFocusHandoff) return@TopNavBar
                    // The pill leaves with the keypress; the page swap follows a frame later.
                    // Selecting the tab the route is already on cancels a still-pending move.
                    pendingTab = if (activeRoute == tab.route) null else tab
                    // Instant switch: the page is already composed in the shell's tab layer, so
                    // only the pill animates. No PixelCopy, no slide overlay, no rebuild.
                    selectedTab = tab
                    returnState.selectTab(tab)
                },
            )
        }

    }
    } // CompositionLocalProvider(LocalMotionArtworkEnabled)
    } // PageLayout
    }
    }
}

/**
 * A tab root that stays composed for the whole session, the way Bolt IPTV keeps its four pages
 * alive. Only the page belonging to the current tab keeps its focus nodes and its draw output;
 * the parked ones cost nothing but their retained state.
 */
@Composable
private fun TabPage(
    active: Boolean,
    drawn: Boolean,
    page: com.qqmusictv.ui.navigation.ReturnPageState,
    content: @Composable () -> Unit,
    foreign: () -> Unit = {},
) {
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    Box(
        Modifier
            .fillMaxSize()
            .onFocusChanged {
                // The pill's Down handoff lands the focus ON this full-screen Box (it is the
                // spatially closest focusable below the bar). Enter the page's group so the
                // first real control receives the cursor instead of this invisible wrapper.
                if (it.isFocused && active) focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Enter)
                // Rapid tab switches can drop the cursor into a parked page's subtree, where
                // nothing is drawn — the remote then reads as dead. Bounce it back to the
                // visible pill of whichever tab is on screen.
                else if (!active && it.hasFocus) foreign()
            }
            // A parked page is removed from focus search entirely, so the cursor can never land
            // on the tab the user is not looking at.
            .focusProperties { canFocus = active }
            .drawWithContent { if (drawn) drawContent() }
            // Enter redirection needs this Box to be a focus group.
            .focusGroup(),
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            com.qqmusictv.ui.navigation.LocalReturnPage provides page,
            com.qqmusictv.ui.navigation.LocalReturnPath provides emptyList(),
        ) { content() }
    }
}

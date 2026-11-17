package com.qqmusictv.ui.screens

import com.qqmusictv.i18n.tr
import com.qqmusictv.ui.components.menuInitialFocus
import com.qqmusictv.R

import com.qqmusictv.ui.components.focusedArtworkBorder
import com.qqmusictv.ui.components.pageHorizontalInset
import com.qqmusictv.ui.components.FadeThrough
import com.qqmusictv.ui.components.TouchSurface as Surface

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import com.qqmusictv.ui.navigation.ReturnLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import com.qqmusictv.ui.navigation.ReturnLazyVerticalGrid as LazyVerticalGrid
import com.qqmusictv.ui.navigation.items
import com.qqmusictv.ui.navigation.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import com.qqmusictv.ui.components.returnToTabOnUp
import com.qqmusictv.ui.components.rememberTabAwareListState
import com.qqmusictv.ui.components.rememberTabAwareGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.qqmusictv.data.model.Album
import com.qqmusictv.data.model.Artist
import com.qqmusictv.data.model.Song
import com.qqmusictv.data.network.PlaylistDto
import com.qqmusictv.ui.components.AlbumCard
import com.qqmusictv.ui.components.LocalUiMotion
import com.qqmusictv.ui.viewmodel.LibraryViewModel
import com.qqmusictv.ui.viewmodel.LibrarySection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qqmusictv.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch
import com.qqmusictv.ui.viewmodel.SortField

private fun com.qqmusictv.ui.viewmodel.LibraryState.hasContent(section: LibrarySection): Boolean = when (section) {
    LibrarySection.Songs -> songs.isNotEmpty()
    LibrarySection.Playlists -> playlists.isNotEmpty()
    LibrarySection.Albums -> albums.isNotEmpty()
    LibrarySection.Artists -> artists.isNotEmpty()
}

/** Sorted snapshots let a sort/section fade finish before the new order is shown. */
private data class LibraryItems(
    val playlists: List<PlaylistDto> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val songs: List<Song> = emptyList(),
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LibraryScreen(
    vm: LibraryViewModel,
    playerVm: PlayerViewModel,
    onAlbumClick: (String) -> Unit = {},
    onPlaylistClick: (id: String, name: String, artworkUrl: String?) -> Unit = { _, _, _ -> },
    onArtistClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    contentFocusRequester: FocusRequester? = null,
    onReturnToTab: (() -> Unit)? = null,
    tabBarFocused: Boolean = false,
) {
    // Survive navigation away/back (saved in the nav backstack), so returning to Library
    // lands on the same section instead of resetting to Playlists.
    var section by rememberSaveable { mutableStateOf(LibrarySection.Songs) }
    val sectionStates=androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    LaunchedEffect(section) { vm.selectSection(section) }
    DisposableEffect(vm) { onDispose { vm.onHidden() } }
    val state by vm.state.collectAsStateWithLifecycle()
    val localEntryFocus = remember { FocusRequester() }
    val listFocus = remember { FocusRequester() }
    val sidebarFocus = remember { FocusRequester() }
    val sortRoute = remember { LibrarySortFocusRoute() }
    val sortFocus = sortRoute.control
    val hasCurrentItems = state.hasContent(section)
    val motion = LocalUiMotion.current
    val returnNavigation = com.qqmusictv.ui.navigation.LocalNavigationReturn.current
    val listReady = state.hasMut && state.error == null && hasCurrentItems &&
        !(state.isLoading && !hasCurrentItems)

    val pinnedIds by vm.pinnedIds.collectAsState()
    val orderedItems = remember(section, state, pinnedIds) {
        when (section) {
            LibrarySection.Playlists -> LibraryItems(playlists = vm.sortedPlaylists())
            LibrarySection.Albums -> LibraryItems(albums = vm.sortedAlbums())
            LibrarySection.Artists -> LibraryItems(artists = vm.sortedArtists())
            LibrarySection.Songs -> LibraryItems(songs = vm.sortedSongs())
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val inset = pageHorizontalInset
    val sidebarWidth = 208.dp.coerceAtMost((maxWidth - inset * 2) * 0.32f)
    Row(Modifier.fillMaxSize().padding(horizontal = inset)) {
        // Modern left sidebar (Playlists / Albums / Artists / Songs).
        SectionSidebar(section, onReturnToTab, sidebarWidth, sortRoute::request, sidebarFocus) { section = it }

        // Always mount this entry group so Down is safe even during first loading.
        // Prefer the list itself over both sidebar and sort control when data exists.
        Box(modifier = Modifier.weight(1f).fillMaxHeight()
            .focusRequester(contentFocusRequester ?: localEntryFocus)
            .libraryEntryFocus(listFocus,listReady,returnNavigation?.restoring==true,sortRoute,sidebarFocus).focusGroup()) {
            FadeThrough(Triple(section, state, orderedItems), modifier = Modifier.fillMaxSize(),
                contentKey = { (section, state, _) -> Triple(section, state.sort, when {
                    !state.hasMut -> "auth"
                    state.isLoading && !state.hasContent(section) -> "loading"
                    state.error != null -> "error"
                    section == LibrarySection.Songs && state.songsError != null && state.songs.isEmpty() -> "songsError"
                    else -> "content"
                }) }) { (section, state, orderedItems) ->
                when {
                    !state.hasMut -> NoMutPlaceholder()
                    // Only show the full-screen spinner on a truly empty first load.
                    // If cached/previous data exists, keep showing it while refreshing
                    // so focus stays in the content and doesn't jump to the top bar.
                    state.isLoading && !state.hasContent(section) -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF31C27C))
                    }
                    state.error != null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text(tr(R.string.msg_096, com.qqmusictv.i18n.AppLanguage.message(state.error)), color = Color(0xFFFF3B30), fontSize = 14.sp)
                    }
                    else -> Column(Modifier.fillMaxSize().padding(top = 80.dp)) {
                        // Hard-routing Left/Down into an empty list would strand focus here;
                        // only override when the list can actually receive it.
                        SortBar(section, state.sort.field, state.sort.dir, onReturnToTab, sortFocus, if (listReady) listFocus else null) { f, d -> vm.setSort(f, d) }
                        if (section == LibrarySection.Songs) {
                            if (state.songsError != null && state.songs.isNotEmpty()) {
                                SongLoadError(state.songsError!!, state.isLoading, vm::refresh)
                            }
                            if (state.songs.isNotEmpty() && state.sort.field == SortField.DATE && !state.songsNewestFirst && !vm.hasSongAdditionDates()) {
                                Text(tr(R.string.msg_114), fontSize = 12.sp,
                                    color = com.qqmusictv.ui.theme.AppPalette.adaptive(Color(0xFF6E6E73), Color(0xFFB1B1B8)), modifier = Modifier.padding(start = 32.dp, top = 8.dp, bottom = 8.dp))
                            }
                        }
                        val dateOrderUnavailable = when (section) {
                            LibrarySection.Albums -> state.albums.isNotEmpty() && state.albums.any { it.additionOrder == null } && state.albums.any { it.dateAdded.isNullOrBlank() }
                            LibrarySection.Playlists -> state.playlists.isNotEmpty() && state.playlists.any { it.additionOrder == null } && state.playlists.any { it.dateAdded.isNullOrBlank() }
                            else -> false
                        }
                        if ((state.sort.field == SortField.DATE || state.sort.field == SortField.DEFAULT) && dateOrderUnavailable) {
                            Text(tr(R.string.msg_114), fontSize = 12.sp, color = com.qqmusictv.ui.theme.AppPalette.adaptive(Color(0xFF6E6E73), Color(0xFFB1B1B8)), modifier = Modifier.padding(start = 32.dp, top = 8.dp, bottom = 8.dp))
                        }
                        Box(Modifier.weight(1f).focusRequester(listFocus).focusGroup()) {
                            CompositionLocalProvider(LocalLibrarySortFocus provides sortRoute) {
                            // 分类切换：只保留一棵内容树，用一次性的 alpha + 轻微上移做进场。
                            // 不用 AnimatedContent——它会同时挂载新旧两个列表，在这台设备上代价太大。
                            val sectionEnter = remember { Animatable(1f) }
                            LaunchedEffect(section) {
                                if (!motion.enabled) { sectionEnter.snapTo(1f); return@LaunchedEffect }
                                sectionEnter.snapTo(0f)
                                sectionEnter.animateTo(1f, tween(200, easing = FastOutSlowInEasing))
                            }
                            Box(Modifier.fillMaxSize()
                                .graphicsLayer {
                                    alpha = 0.35f + 0.65f * sectionEnter.value
                                    translationY = (1f - sectionEnter.value) * 12.dp.toPx()
                                }
                                .librarySortOnRight(section==LibrarySection.Songs || section==LibrarySection.Artists)) {
                            sectionStates.SaveableStateProvider(section.name) {
                            when (section) {
                                LibrarySection.Playlists -> {
                                    PlaylistGrid(
                                        tabBarFocused = tabBarFocused,
                                        onReturnToTab = onReturnToTab,
                                        playlists  = orderedItems.playlists,
                                        pinnedIds  = pinnedIds,
                                        onOpen     = { onPlaylistClick(it.id, it.name, it.artworkUrl(500)) },
                                        onPlay     = { vm.playPlaylist(it.id, playerVm) },
                                        onTogglePin = { vm.togglePin(it.id) },
                                    )
                                }
                                LibrarySection.Albums  -> AlbumGrid(orderedItems.albums, onAlbumClick, onReturnToTab, tabBarFocused)
                                LibrarySection.Artists -> ArtistList(orderedItems.artists, onArtistClick, onReturnToTab, tabBarFocused)
                                LibrarySection.Songs -> if (state.songsError != null && state.songs.isEmpty()) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        SongLoadError(state.songsError!!, state.isLoading, vm::refresh)
                                    }
                                } else SongList(orderedItems.songs, playerVm, onArtistClick, onAlbumClick, onReturnToTab, tabBarFocused)
                            }
                            }
                            }
                            }
                        }
                    }
                }
            }

        }
    }
}

} // page bounds

/** Modern left sidebar — vertical section nav. Selected row = red-tinted pill with a
 *  bright accent bar; unselected are quiet. Bigger, cleaner than the old sidebar. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SectionSidebar(selected: LibrarySection, onReturnToTab: (() -> Unit)?, sidebarWidth: androidx.compose.ui.unit.Dp, onRequestSort: () -> Boolean, sidebarFocus: FocusRequester? = null, onSelect: (LibrarySection) -> Unit) {
    val motion = LocalUiMotion.current
    val density = LocalDensity.current
    // 每个分类行在 root 坐标系里的位置与高度。存绝对值、读取时才减去侧栏起点：
    // Surface 内部会再包一层布局，boundsInParent() 会丢掉 Column 的 padding 偏移；
    // 而首帧时侧栏起点可能还没测量到，用绝对坐标可避免写入过期的相对值。
    val rowRootTop = remember { mutableStateMapOf<LibrarySection, Float>() }
    val rowHeights = remember { mutableStateMapOf<LibrarySection, Float>() }
    var sidebarTopInRoot by remember { mutableStateOf(0f) }
    val indicatorTop = remember { Animatable(0f) }
    val indicatorHeight = remember { Animatable(0f) }
    var indicatorPlaced by remember { mutableStateOf(false) }
    val targetTop = rowRootTop[selected]?.minus(sidebarTopInRoot)
    val targetHeight = rowHeights[selected]
    LaunchedEffect(selected, targetTop, targetHeight) {
        val b = targetTop ?: return@LaunchedEffect
        val h = targetHeight ?: return@LaunchedEffect
        if (!indicatorPlaced) {
            indicatorTop.snapTo(b); indicatorHeight.snapTo(h); indicatorPlaced = true
        } else if (motion.enabled) {
            // 位置先动、高度后到：读起来像滑块"滑过去"，而不是整块被拉伸。
            kotlinx.coroutines.coroutineScope {
                launch { indicatorTop.animateTo(b, tween(220, easing = FastOutSlowInEasing)) }
                launch { indicatorHeight.animateTo(h, tween(180, easing = FastOutSlowInEasing)) }
            }
        } else {
            indicatorTop.snapTo(b); indicatorHeight.snapTo(h)
        }
    }
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(sidebarWidth)
            .onGloballyPositioned { sidebarTopInRoot = it.boundsInRoot().top }
            .background(com.qqmusictv.ui.theme.AppPalette.adaptive(Color(0xFFFFFFFF), Color(0xFF101012))),
    ) {
        // 选中指示器画在所有行下面：一整个滑块在两个分类之间移动，而不是各行各自切换底色。
        if (indicatorPlaced) {
            Box(
                Modifier
                    .offset(y = with(density) { indicatorTop.value.toDp() })
                    .padding(end = 16.dp)
                    .fillMaxWidth()
                    .height(with(density) { indicatorHeight.value.toDp() })
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x3331C27C)),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .padding(end = 16.dp, top = 96.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                tr(R.string.msg_089),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = com.qqmusictv.ui.theme.AppPalette.primary,
                modifier = Modifier.padding(bottom = 14.dp),
            )
            LibrarySection.entries.forEach { s ->
                val isSel = s == selected
                Surface(
                    onClick = { onSelect(s) },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        // 选中底色由外层滑动指示器绘制，行本身保持透明才能透出它。
                        containerColor = Color.Transparent,
                        focusedContainerColor = if (isSel) Color(0x4D31C27C) else com.qqmusictv.ui.theme.AppPalette.adaptive(Color(0xFFE8E8ED), Color(0xFF38383D)),
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
                    // The first content row goes straight Up to tabs; keep sort reachable from the sidebar.
                    modifier = Modifier.fillMaxWidth()
                        .onGloballyPositioned { coords ->
                            val r = coords.boundsInRoot()
                            rowRootTop[s] = r.top
                            rowHeights[s] = r.height
                        }
                        .onFocusChanged { if (it.isFocused) android.util.Log.i("LIBFOCUS", "sidebar focused: ${s.label}") }
                        .then(if (isSel && sidebarFocus != null) Modifier.focusRequester(sidebarFocus) else Modifier)
                        .onPreviewKeyEvent {event ->
                            if(event.type==KeyEventType.KeyDown && event.key==Key.DirectionRight) onRequestSort() else false
                        }
                        .returnToTabOnUp(if (s == LibrarySection.Songs) onReturnToTab else null),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            Modifier.width(3.dp).height(18.dp).clip(RoundedCornerShape(2.dp))
                                .background(if (isSel) Color(0xFF31C27C) else Color.Transparent)
                        )
                        Text(
                            text = s.label,
                            fontSize = 16.sp,
                            fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (isSel) com.qqmusictv.ui.theme.AppPalette.primary else com.qqmusictv.ui.theme.AppPalette.adaptive(Color(0xFF6E6E73), Color(0xFFB1B1B8)),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun SortBar(
    section: LibrarySection,
    currentField: SortField,
    currentDir: com.qqmusictv.ui.viewmodel.SortDir,
    onReturnToTab: (() -> Unit)?,
    sortFocus: FocusRequester,
    listFocus: FocusRequester? = null,
    onSort: (SortField, com.qqmusictv.ui.viewmodel.SortDir) -> Unit,
) {
    val fields = when (section) {
        LibrarySection.Playlists -> listOf(SortField.DATE to tr(R.string.msg_117), SortField.NAME to tr(R.string.msg_116))
        LibrarySection.Albums    -> listOf(SortField.DATE to tr(R.string.msg_117), SortField.NAME to tr(R.string.msg_116), SortField.ARTIST to tr(R.string.msg_071))
        LibrarySection.Artists   -> listOf(SortField.DATE to tr(R.string.msg_117), SortField.NAME to tr(R.string.msg_116))
        LibrarySection.Songs     -> listOf(SortField.DATE to tr(R.string.msg_117), SortField.NAME to tr(R.string.msg_118), SortField.ARTIST to tr(R.string.msg_071))
    }
    val curLabel = fields.firstOrNull { it.first == currentField }?.second ?: tr(R.string.msg_119)
    val dirArrow = if (currentDir == com.qqmusictv.ui.viewmodel.SortDir.ASC) "↑" else "↓"
    var showDialog by remember { mutableStateOf(false) }

    // Round ⇅ sort button, right-aligned — matches the playlist detail's sort control.
    Row(
        modifier = Modifier.fillMaxWidth().returnToTabOnUp(onReturnToTab).background(com.qqmusictv.ui.theme.AppPalette.adaptive(Color(0xFFFFFFFF), Color(0xFF101012))).padding(start = 32.dp, top = 10.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$curLabel · ${if (currentDir == com.qqmusictv.ui.viewmodel.SortDir.DESC) tr(R.string.msg_120) else tr(R.string.msg_121)}",
            color = com.qqmusictv.ui.theme.AppPalette.adaptive(Color(0xFF6E6E73), Color(0xFFB1B1B8)), fontSize = 13.sp,
            modifier = Modifier.padding(end = 12.dp))
        Surface(
            onClick = { showDialog = true },
            modifier = Modifier.focusRequester(sortFocus).semantics {contentDescription=tr(R.string.msg_122)}
                .focusProperties {if(listFocus!=null) {left=listFocus;down=listFocus}},
            shape  = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
            colors = com.qqmusictv.ui.components.contentActionColors(com.qqmusictv.ui.theme.AppPalette.adaptive(Color(0xFFE8E8ED), Color(0xFF38383D))),
            border = com.qqmusictv.ui.components.contentActionBorder(RoundedCornerShape(50)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        ) {
            Box(Modifier.requiredSize(44.dp), contentAlignment = Alignment.Center) {
                com.qqmusictv.ui.components.Icon(com.qqmusictv.ui.components.Glyph.SORT, size = 22.dp, color = com.qqmusictv.ui.theme.AppPalette.primary)
            }
        }
    }

    FadeThrough(showDialog, contentKey = { if (it) true else null }) { visible ->
            if (visible) {
            SortDialog(
                fields = fields,
                currentField = currentField,
                currentDir = currentDir,
                onPick = { f, d -> onSort(f, d) },
                onDismiss = { showDialog = false },
            )
        }
    }
}

/** Sort chooser popup: pick a field, then a direction. Real Dialog so D-pad focus
 *  stays inside it (a plain overlay Box lets focus walk out to the grid behind). */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SortDialog(
    fields: List<Pair<SortField, String>>,
    currentField: SortField,
    currentDir: com.qqmusictv.ui.viewmodel.SortDir,
    onPick: (SortField, com.qqmusictv.ui.viewmodel.SortDir) -> Unit,
    onDismiss: () -> Unit,
) {
    val Asc = com.qqmusictv.ui.viewmodel.SortDir.ASC
    val Desc = com.qqmusictv.ui.viewmodel.SortDir.DESC
    com.qqmusictv.ui.components.MotionDialog(onDismissRequest = onDismiss) {
        val firstFocus = remember { FocusRequester() }
        Surface(
            shape = RoundedCornerShape(16.dp),
            colors = androidx.tv.material3.SurfaceDefaults.colors(containerColor = com.qqmusictv.ui.theme.AppPalette.adaptive(Color(0xFFF5F5F7), Color(0xFF222225))),
            modifier = Modifier.width(340.dp),
        ) {
            Column(Modifier.menuInitialFocus(firstFocus).padding(vertical = 18.dp)) {
                Text(tr(R.string.msg_123), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = com.qqmusictv.ui.theme.AppPalette.primary,
                    modifier = Modifier.padding(start = 22.dp, bottom = 10.dp))
                fields.forEachIndexed { i, (field, label) ->
                    SortRow(
                        label = label,
                        selected = field == currentField,
                        modifier = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier,
                    ) { onPick(field, currentDir) }
                }
                Box(Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 22.dp)
                    .height(1.dp).background(com.qqmusictv.ui.theme.AppPalette.adaptive(Color(0xFFDCDCE1), Color(0xFF414146))))
                SortRow(tr(R.string.msg_121), currentDir == Asc) { onPick(currentField, Asc) }
                SortRow(tr(R.string.msg_120), currentDir == Desc) { onPick(currentField, Desc) }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SortRow(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent, focusedContainerColor = com.qqmusictv.ui.theme.AppPalette.adaptive(Color(0xFFE8E8ED), Color(0xFF38383D)),
        ),
        modifier = modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, fontSize = 14.sp, color = com.qqmusictv.ui.theme.AppPalette.primary,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.weight(1f))
            if (selected) Text("✓", fontSize = 15.sp, color = Color(0xFF31C27C))
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlaylistGrid(
    playlists: List<PlaylistDto>,
    pinnedIds: Set<String>,
    onOpen: (PlaylistDto) -> Unit,
    onPlay: (PlaylistDto) -> Unit,
    onTogglePin: (PlaylistDto) -> Unit,
    onReturnToTab: (() -> Unit)? = null,
    tabBarFocused: Boolean = false,
) {
    val gridState = rememberTabAwareGridState(tabBarFocused)
    if (playlists.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { Text(tr(R.string.msg_124), color = com.qqmusictv.ui.theme.AppPalette.adaptive(Color(0xFF8E8E93), Color(0xFFA3A3AA))) }
        return
    }
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(4),
        contentPadding = PaddingValues(start = 32.dp, top = 20.dp, bottom = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        val playlistKeys = com.qqmusictv.ui.navigation.occurrenceKeys(playlists.map { it.id })
        gridItemsIndexed(playlists, key = { index, _ -> playlistKeys[index] }) { index, playlist ->
            Box(Modifier.animateItem().librarySortOnRight(index%4==3 || index==playlists.lastIndex).returnToTabOnUp(if (index < 4) onReturnToTab else null)) {
                PlaylistCard(
                    playlist    = playlist,
                    pinned      = playlist.id in pinnedIds,
                    onOpen      = { onOpen(playlist) },
                    onPlay      = { onPlay(playlist) },
                    onTogglePin = { onTogglePin(playlist) },
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlaylistCard(
    playlist: PlaylistDto,
    pinned: Boolean,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onTogglePin: () -> Unit,
) {
    Surface(
        onClick      = onOpen,
        onLongClick  = onTogglePin,
        shape  = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = com.qqmusictv.ui.theme.AppPalette.adaptive(Color(0xFFF5F5F7), Color(0xFF222225)), focusedContainerColor = com.qqmusictv.ui.theme.AppPalette.adaptive(Color(0xFFEDEDF0), Color(0xFF414146))),
        scale  = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Column(modifier = Modifier.padding(bottom = 8.dp)) {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                    .focusedArtworkBorder(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)).clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                    .background(com.qqmusictv.ui.theme.AppPalette.adaptive(Color(0xFFE8E8ED), Color(0xFF38383D))),
            ) {
                if (playlist.artworkUrl != null) {
                    AsyncImage(
                        model = playlist.artworkUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        com.qqmusictv.ui.components.Icon(com.qqmusictv.ui.components.Glyph.MUSIC_NOTE, size = 32.dp, color = com.qqmusictv.ui.theme.AppPalette.adaptive(Color(0xFF8E8E93), Color(0xFFA3A3AA)))
                    }
                }
                if (pinned) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xCC000000))
                            .padding(horizontal = 5.dp, vertical = 3.dp),
                    ) {
                        Text("📌", fontSize = 10.sp)
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(playlist.name, fontSize = 12.sp, color = com.qqmusictv.ui.theme.AppPalette.primary, maxLines = 1, fontWeight = FontWeight.Medium)
                    if (playlist.curatorName.isNotEmpty())
                        Text(playlist.curatorName, fontSize = 10.sp, color = com.qqmusictv.ui.theme.AppPalette.adaptive(Color(0xFF6E6E73), Color(0xFFB1B1B8)), maxLines = 1)
                }
                Surface(
                    onClick = onPlay,
                    shape  = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                    colors = com.qqmusictv.ui.components.contentActionColors(com.qqmusictv.ui.theme.AppPalette.adaptive(Color(0xFFE8E8ED), Color(0xFF38383D))),
                    border = com.qqmusictv.ui.components.contentActionBorder(RoundedCornerShape(50)),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
                    modifier = Modifier.size(28.dp),
                ) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        com.qqmusictv.ui.components.Icon(com.qqmusictv.ui.components.Glyph.PLAY, size = 11.dp, color = com.qqmusictv.ui.theme.AppPalette.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumGrid(albums: List<Album>, onAlbumClick: (String) -> Unit, onReturnToTab: (() -> Unit)? = null, tabBarFocused: Boolean = false) {
    val gridState = rememberTabAwareGridState(tabBarFocused)
    if (albums.isEmpty()) { Box(Modifier.fillMaxSize(), Alignment.Center) { Text(tr(R.string.msg_125), color = com.qqmusictv.ui.theme.AppPalette.adaptive(Color(0xFF8E8E93), Color(0xFFA3A3AA))) }; return }
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(4),
        contentPadding = PaddingValues(start = 32.dp, top = 20.dp, bottom = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        val albumKeys = com.qqmusictv.ui.navigation.occurrenceKeys(albums.map { it.id })
        gridItemsIndexed(albums, key = { index, _ -> albumKeys[index] }) { index, album ->
            AlbumCard(album = album, size = 150, onClick = { onAlbumClick(album.id) },
                modifier = Modifier.animateItem().librarySortOnRight(index%4==3 || index==albums.lastIndex).returnToTabOnUp(if (index < 4) onReturnToTab else null))
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ArtistList(artists: List<Artist>, onArtistClick: (String) -> Unit, onReturnToTab: (() -> Unit)? = null, tabBarFocused: Boolean = false) {
    val listState = rememberTabAwareListState(tabBarFocused)
    if (artists.isEmpty()) { Box(Modifier.fillMaxSize(), Alignment.Center) { Text(tr(R.string.msg_126), color = com.qqmusictv.ui.theme.AppPalette.adaptive(Color(0xFF8E8E93), Color(0xFFA3A3AA))) }; return }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 32.dp, top = 24.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val artistKeys = com.qqmusictv.ui.navigation.occurrenceKeys(artists.map { it.id })
        itemsIndexed(artists, key = { index, _ -> artistKeys[index] }) { index, artist ->
            Surface(onClick = { onArtistClick(artist.id) }, modifier = Modifier.fillMaxWidth().animateItem().returnToTabOnUp(if (index == 0) onReturnToTab else null), shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = com.qqmusictv.ui.theme.AppPalette.adaptive(Color(0xFFF5F5F7), Color(0xFF222225))), scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(50)).background(com.qqmusictv.ui.theme.AppPalette.adaptive(Color(0xFFE8E8ED), Color(0xFF38383D)))) {
                        if (artist.artworkUrl != null) AsyncImage(model = artist.artworkUrl(88), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                    Text(artist.name, fontSize = 14.sp, color = com.qqmusictv.ui.theme.AppPalette.primary, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SongList(
    songs: List<Song>,
    playerVm: PlayerViewModel,
    onArtistClick: (String) -> Unit = {},
    onAlbumClick: (String) -> Unit = {},
    onReturnToTab: (() -> Unit)? = null,
    tabBarFocused: Boolean = false,
) {
    val listState = rememberTabAwareListState(tabBarFocused)
    if (songs.isEmpty()) { Box(Modifier.fillMaxSize(), Alignment.Center) { Text(tr(R.string.msg_127), color = com.qqmusictv.ui.theme.AppPalette.adaptive(Color(0xFF8E8E93), Color(0xFFA3A3AA))) }; return }
    var menuSong by remember { mutableStateOf<Song?>(null) }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 32.dp, top = 24.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val returnKeys=com.qqmusictv.ui.navigation.occurrenceKeys(songs.map { it.id })
    items(songs.size, key = { returnKeys[it] }) { idx ->
            val song = songs[idx]
            Surface(
                onClick = { playerVm.playAlbum(songs, idx) },
                onLongClick = { menuSong = song },
                onContextMenu = { menuSong = song },
                modifier = Modifier.fillMaxWidth().animateItem().returnToTabOnUp(if (idx == 0) onReturnToTab else null),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = com.qqmusictv.ui.theme.AppPalette.adaptive(Color(0xFFF5F5F7), Color(0xFF222225))), scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f)) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(5.dp)).background(com.qqmusictv.ui.theme.AppPalette.adaptive(Color(0xFFE8E8ED), Color(0xFF38383D)))) {
                        if (song.artworkUrl != null) AsyncImage(model = song.artworkUrl(80), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                    Column(Modifier.weight(1f)) {
                        Text(song.title, fontSize = 13.sp, color = com.qqmusictv.ui.theme.AppPalette.primary, maxLines = 1, fontWeight = FontWeight.Medium)
                        Text(song.artistName, fontSize = 11.sp, color = com.qqmusictv.ui.theme.AppPalette.adaptive(Color(0xFF6E6E73), Color(0xFFB1B1B8)), maxLines = 1)
                    }
                    Text(song.durationFormatted, fontSize = 11.sp, color = com.qqmusictv.ui.theme.AppPalette.adaptive(Color(0xFF8E8E93), Color(0xFFA3A3AA)))
                }
            }
        }
    }
    var pickerSong by remember { mutableStateOf<Song?>(null) }
    FadeThrough(menuSong, contentKey = { it?.id }) { retained ->
            retained?.let { s ->
            SongContextMenu(
                song = s,
                onDismiss = { menuSong = null },
                onPlayNext = { playerVm.playNext(s); menuSong = null },
                onAddToQueue = { playerVm.addToQueue(s); menuSong = null },
                onAddToLibrary = { playerVm.addToLibrary(s); menuSong = null },
                onAddToPlaylist = { pickerSong = s; menuSong = null },
                onGoToArtist = s.artistId?.let { id -> { onArtistClick(id); menuSong = null } },
                onGoToAlbum  = s.albumId?.let  { id -> { onAlbumClick(id);  menuSong = null } },
            )
        }
    }
    FadeThrough(pickerSong, contentKey = { it?.id }) { retained ->
            retained?.let { s ->
            PlaylistPickerDialog(playerVm = playerVm, song = s, onDismiss = { pickerSong = null })
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun SongContextMenu(
    song: Song,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToLibrary: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    onGoToArtist: (() -> Unit)? = null,
    onGoToAlbum:  (() -> Unit)? = null,
) {
    com.qqmusictv.ui.components.MotionDialog(onDismissRequest = onDismiss) {
        val firstFocus = remember { androidx.compose.ui.focus.FocusRequester() }
        var awaitingOpeningRelease by remember { mutableStateOf(true) }
        Column(
            Modifier.menuInitialFocus(firstFocus).onPreviewKeyEvent { event ->
                if (awaitingOpeningRelease && event.key in setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)) {
                    if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                        awaitingOpeningRelease = false
                        false
                    } else {
                        if (event.type == KeyEventType.KeyUp) awaitingOpeningRelease = false
                        true
                    }
                } else false
            }.width(320.dp).clip(RoundedCornerShape(16.dp)).background(com.qqmusictv.ui.theme.AppPalette.adaptive(Color(0xFFF5F5F7), Color(0xFF222225))).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // Header with artwork + song info
            Row(Modifier.padding(horizontal = 8.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)).background(com.qqmusictv.ui.theme.AppPalette.adaptive(Color(0xFFE8E8ED), Color(0xFF38383D)))) {
                    if (song.artworkUrl != null) AsyncImage(model = song.artworkUrl(88), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                Column {
                    Text(song.title, fontSize = 13.sp, color = com.qqmusictv.ui.theme.AppPalette.primary, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(song.artistName, fontSize = 11.sp, color = com.qqmusictv.ui.theme.AppPalette.adaptive(Color(0xFF6E6E73), Color(0xFFB1B1B8)), maxLines = 1)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(com.qqmusictv.ui.theme.AppPalette.adaptive(Color(0xFFE8E8ED), Color(0xFF38383D))))
            Spacer(Modifier.height(2.dp))
            ContextMenuItem(tr(R.string.msg_128), onPlayNext, Modifier.focusRequester(firstFocus))
            ContextMenuItem(tr(R.string.msg_129), onAddToQueue)
            if (onAddToLibrary  != null) ContextMenuItem(tr(R.string.msg_130),  onAddToLibrary)
            if (onAddToPlaylist != null) ContextMenuItem(tr(R.string.msg_131), onAddToPlaylist)
            if (onGoToArtist != null) ContextMenuItem(tr(R.string.msg_132), onGoToArtist)
            if (onGoToAlbum  != null) ContextMenuItem(tr(R.string.msg_133),  onGoToAlbum)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ContextMenuItem(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.padding(horizontal = 6.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = com.qqmusictv.ui.theme.AppPalette.adaptive(Color(0xFFE8E8ED), Color(0xFF38383D))),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Text(label, fontSize = 14.sp, color = com.qqmusictv.ui.theme.AppPalette.primary, modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp))
    }
}

@Composable
private fun NoMutPlaceholder() {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(tr(R.string.msg_134), color = com.qqmusictv.ui.theme.AppPalette.primary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(tr(R.string.msg_135), color = com.qqmusictv.ui.theme.AppPalette.adaptive(Color(0xFF6E6E73), Color(0xFFB1B1B8)), fontSize = 13.sp)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SidebarItem(label: String, isSelected: Boolean, onClick: () -> Unit) {
    val bg     by animateColorAsState(if (isSelected) com.qqmusictv.ui.theme.AppPalette.adaptive(Color(0xFFF5F5F7), Color(0xFF222225)) else Color.Transparent, tween(225))
    val accent by animateColorAsState(if (isSelected) Color(0xFF31C27C) else Color.Transparent, tween(225))
    val text   by animateColorAsState(if (isSelected) com.qqmusictv.ui.theme.AppPalette.primary else com.qqmusictv.ui.theme.AppPalette.adaptive(Color(0xFF6E6E73), Color(0xFFB1B1B8)), tween(225))
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = bg, focusedContainerColor = com.qqmusictv.ui.theme.AppPalette.adaptive(Color(0xFFEDEDF0), Color(0xFF414146)))) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.width(2.dp).height(20.dp).background(accent, RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp)))
            Text(label, fontSize = 13.sp, color = text, fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal, modifier = Modifier.padding(start = 14.dp))
        }
    }
}

/** "添加到播放列表" picker — lists the user's editable library playlists; tap one to append the song. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlaylistPickerDialog(
    playerVm: com.qqmusictv.ui.viewmodel.PlayerViewModel,
    song: Song,
    onDismiss: () -> Unit,
) {
    var playlists by remember { mutableStateOf<List<com.qqmusictv.data.network.PlaylistDto>?>(null) }
    LaunchedEffect(Unit) { playlists = playerVm.editablePlaylists() }
    com.qqmusictv.ui.components.MotionDialog(onDismissRequest = onDismiss) {
        val firstFocus = remember { androidx.compose.ui.focus.FocusRequester() }
        Column(
            Modifier.menuInitialFocus(firstFocus, targetAvailable = !playlists.isNullOrEmpty()).width(360.dp).heightIn(max = 460.dp)
                .clip(RoundedCornerShape(16.dp)).background(com.qqmusictv.ui.theme.AppPalette.adaptive(Color(0xFFF5F5F7), Color(0xFF222225))).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(tr(R.string.msg_131), fontSize = 15.sp, color = com.qqmusictv.ui.theme.AppPalette.primary, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(com.qqmusictv.ui.theme.AppPalette.adaptive(Color(0xFFE8E8ED), Color(0xFF38383D))))
            Spacer(Modifier.height(4.dp))
            val pls = playlists
            when {
                pls == null -> Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator(color = Color(0xFF31C27C))
                }
                pls.isEmpty() -> Text(tr(R.string.msg_136), color = com.qqmusictv.ui.theme.AppPalette.adaptive(Color(0xFF6E6E73), Color(0xFFB1B1B8)), fontSize = 13.sp,
                    modifier = Modifier.padding(16.dp))
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(pls.size) { i ->
                        val pl = pls[i]
                        ContextMenuItem(pl.name,
                            { playerVm.addToPlaylist(pl.id, pl.name, song); onDismiss() },
                            if (i == 0) Modifier.focusRequester(firstFocus) else Modifier)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SongLoadError(message: String, isLoading: Boolean, onRetry: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 32.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(message, fontSize = 14.sp, color = com.qqmusictv.ui.theme.AppPalette.adaptive(Color(0xFF6E6E73), Color(0xFFB1B1B8)), modifier = Modifier.weight(1f))
        Surface(onClick = { if (!isLoading) onRetry() },
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
            colors = com.qqmusictv.ui.components.contentActionColors(com.qqmusictv.ui.theme.AppPalette.adaptive(Color(0xFFF5F5F7), Color(0xFF222225))),
            border = com.qqmusictv.ui.components.contentActionBorder(RoundedCornerShape(50))) {
            Text(if (isLoading) tr(R.string.msg_137) else tr(R.string.msg_138), color = Color(0xFF31C27C), fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
        }
    }
}

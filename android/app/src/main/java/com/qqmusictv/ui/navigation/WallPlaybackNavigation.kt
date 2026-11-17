package com.qqmusictv.ui.navigation

/** Lineage belongs to detail entries, never to the global playback navigation event. */
private const val PLAYER_ORIGIN = "player.detail.origin"
private val playerDetailRoutes = setOf(Screen.AlbumDetail.route, Screen.PlaylistDetail.route, Screen.ArtistDetail.route)

/** Audio-player menus establish a return source; related details inherit it. */
internal fun NavigationReturnState.openDetail(route: String, fromPlayer: Boolean = false) {
    val source = nav.currentBackStackEntry
    val origin = when {
        fromPlayer && source?.destination?.route == Screen.NowPlaying.route -> source?.id
        source?.destination?.route in playerDetailRoutes -> source?.savedStateHandle?.get<String>(PLAYER_ORIGIN)
        else -> null
    }
    nav.navigate(route)
    val detail = nav.currentBackStackEntry
    if (origin != null && detail?.destination?.route in playerDetailRoutes) {
        detail?.savedStateHandle?.set(PLAYER_ORIGIN, origin)
    }
}

/** Explicit playback ends this detail branch and reuses the exact player that opened it. */
internal fun NavigationReturnState.returnToPlayerAfterPlayback(): Boolean {
    val detail = nav.currentBackStackEntry ?: return false
    if (detail.destination.route !in playerDetailRoutes) return false
    val originId = detail.savedStateHandle.get<String>(PLAYER_ORIGIN) ?: return false
    val origin = runCatching { nav.getBackStackEntry(Screen.NowPlaying.route) }.getOrNull() ?: return false
    if (origin.id != originId) return false
    if (!nav.popBackStack(Screen.NowPlaying.route, inclusive = false)) return false
    cancel()
    request(origin.id)
    return true
}

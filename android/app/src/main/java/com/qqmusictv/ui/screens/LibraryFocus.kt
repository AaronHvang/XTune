package com.qqmusictv.ui.screens

import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.input.key.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged

/** Explicit sort requests must bypass the page's usual automatic list entry. */
internal class LibrarySortFocusRoute(val control: FocusRequester = FocusRequester()) {
    var requestingSort = false
        private set
    fun request(): Boolean {
        requestingSort = true
        return try { runCatching {control.requestFocus()}.getOrDefault(false) } finally { requestingSort = false }
    }
}

internal fun Modifier.libraryEntryFocus(list: FocusRequester, ready: Boolean, restoring: Boolean,
    sort: LibrarySortFocusRoute, sidebar: FocusRequester? = null): Modifier = onFocusChanged { state ->
    // The entry group is targeted by the nav bar's Down handoff via a DIRECT FocusRequester
    // request, which bypasses focusProperties{onEnter} interception. Detect self-focus
    // (the group node itself holds focus because no child took it — empty/loading/auth
    // regions have no focusable children) and redirect to the list, or to the sidebar so
    // the cursor never rests on an invisible dead end.
    if (!state.isFocused) return@onFocusChanged
    if (restoring || sort.requestingSort) return@onFocusChanged
    if (ready) list.requestFocus() else sidebar?.requestFocus()
}

internal val LocalLibrarySortFocus = compositionLocalOf<LibrarySortFocusRoute?> {null}

/** Linear lists use Right; grid items opt in only at their right edge. */
@Composable
internal fun Modifier.librarySortOnRight(enabled: Boolean=true): Modifier {
    val route=LocalLibrarySortFocus.current
    return onPreviewKeyEvent {event ->
        enabled && route!=null && event.type==KeyEventType.KeyDown && event.key==Key.DirectionRight && route.request()
    }
}

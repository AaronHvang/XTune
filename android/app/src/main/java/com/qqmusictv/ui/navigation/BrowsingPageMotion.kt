package com.qqmusictv.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import com.qqmusictv.ui.components.UiMotion

/** Root-to-root motion: player transitions slide here; browsing-root pushes ride AppShell's snapshot overlay. */
object BrowsingPageMotion {
    private val roots = listOf(Screen.NowPlaying.route, Screen.Browse.route, Screen.Library.route, Screen.DevMenu.route, Screen.Search.route)

    /** Positive = move towards the right-hand tab; incoming page starts on the right. */
    fun direction(from: String?, to: String?): Int {
        val before = roots.indexOf(from)
        val after = roots.indexOf(to)
        if (before < 0 || after < 0) return 0
        return (after - before).compareTo(0)
    }

    internal const val tabSlideMs = 300
    private fun playerInvolved(from: String?, to: String?) =
        from == Screen.NowPlaying.route || to == Screen.NowPlaying.route
    // Symmetric, gradual acceleration/deceleration for the full-width content pane.
    // Both pages use the same curve so their shared edge remains contiguous.
    internal val pageEasing = TabMotion.easing

    fun enter(from: String?, to: String?, motion: UiMotion, snapshotActive: Boolean = false): EnterTransition {
        if (!motion.enabled) return fadeIn(tween(0))

        val direction = direction(from, to)
        return if (direction != 0 && playerInvolved(from, to) && !snapshotActive) {
            // Full-width push: the outgoing page slides out towards the
            // old tab while the incoming page slides in from the new tab's side. Skipped when
            // AppShell's snapshot is already pushing the same pair (it owns the slide then).
            slideInHorizontally(tween(tabSlideMs, easing = pageEasing)) { direction * it }
        } else {
            // Tab pushes covered by AppShell's snapshot overlay contribute only a quick fade:
            // the incoming page's first composition can block long enough to swallow any
            // NavHost slide.
            fadeIn(tween(motion.enterMs))
        }
    }

    fun exit(from: String?, to: String?, motion: UiMotion, snapshotActive: Boolean = false): ExitTransition {
        if (!motion.enabled) return fadeOut(tween(0))

        val direction = direction(from, to)
        return if (direction != 0 && playerInvolved(from, to) && !snapshotActive) {
            slideOutHorizontally(tween(tabSlideMs, easing = pageEasing)) { -direction * it }
        } else fadeOut(tween(motion.exitMs))
    }
}

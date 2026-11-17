package com.qqmusictv.ui.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import com.qqmusictv.ui.components.UiMotion

/** Shared timing for a full-page player/content crossfade. */
object PlayerEntryMotion {
    const val crossfadeMs = 500
    /** The top capsule leaves quickly when the player opens, and comes back a beat later. */
    const val navExitMs = 160
    const val navEnterMs = 220
    val easing = FastOutSlowInEasing

    fun duration(motion: UiMotion): Int = if (motion.enabled) crossfadeMs else 0
}

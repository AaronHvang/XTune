package com.qqmusictv.ui.navigation

import androidx.compose.animation.core.CubicBezierEasing

/** Shared clock for browsing/settings crossfade, navigation capsule and label colors. */
internal object TabMotion {
    const val durationMillis = 300
    fun selectionDuration(from: TopNavTab, to: TopNavTab): Int =
        if (from == TopNavTab.NowPlaying || to == TopNavTab.NowPlaying) 340 else durationMillis

    val easing = CubicBezierEasing(.37f, 0f, .63f, 1f)

    /** Ease-out for the white pill: quick departure, long settle, so the travel stays readable. */
    val pillEasing = CubicBezierEasing(.22f, .61f, .36f, 1f)
}

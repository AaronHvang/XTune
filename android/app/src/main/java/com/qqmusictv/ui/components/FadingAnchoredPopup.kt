package com.qqmusictv.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/**
 * No Layout node before Popup: its anchor remains the caller's metadata Box.
 * Only the separate popup window contains an animation layout. Keep the window
 * until the exit finishes, without changing the anchor rectangle or provider.
 */
@Composable
fun FadingAnchoredPopup(
    visibleState: MutableTransitionState<Boolean>,
    popupPositionProvider: PopupPositionProvider,
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (visibleState.isIdle && !visibleState.currentState) return
    val openingMode = androidx.compose.ui.platform.LocalInputModeManager.current.inputMode
    val motion = LocalUiMotion.current
    val interactive = LocalMotionInteractive.current && visibleState.targetState
    com.qqmusictv.ui.navigation.ReturnFocusLayer {
    Popup(popupPositionProvider = popupPositionProvider, onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true)) {
        // 气泡菜单从下方按钮往上长出，而不是整块淡入：以底部中心为原点缩放，
        // 内容在面板展开过半后（70ms 延迟）才淡入，形成"面板先到、文字随后"的层级感。
        // reduced-motion 下两个动画都为 0ms，退化为原来的瞬时切换。
        val origin = TransformOrigin(0.5f, 1f)
        val growMs = if (motion.enabled) 220 else 0
        val labelMs = if (motion.enabled) motion.exitMs else 0
        AnimatedVisibility(visibleState = visibleState, modifier = Modifier.motionInput(interactive),
            enter = scaleIn(tween(growMs, easing = FastOutSlowInEasing), initialScale = 0.88f, transformOrigin = origin) +
                fadeIn(tween(if (motion.enabled) 140 else 0, delayMillis = if (motion.enabled) 70 else 0)),
            exit = scaleOut(tween(labelMs), targetScale = 0.92f, transformOrigin = origin) +
                fadeOut(tween(labelMs))) {
            CompositionLocalProvider(LocalMotionInteractive provides interactive, LocalMenuOpeningInputMode provides openingMode) { content() }
        }
    }
    }
}

package com.qqmusictv.ui.screens

import com.qqmusictv.i18n.tr
import com.qqmusictv.R

import com.qqmusictv.ui.components.TouchSurface as Surface
import com.qqmusictv.ui.components.ContentActionShape
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import com.qqmusictv.ui.components.pageHorizontalInset
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.qqmusictv.ui.components.LocalUiMotion
import com.qqmusictv.ui.components.QQWebLoginPanel
import com.qqmusictv.ui.viewmodel.OnboardingState
import com.qqmusictv.ui.viewmodel.OnboardingViewModel
import kotlin.math.hypot

/** QQ brand green: the primary button's color, and what the entry reveal expands into. */
private val QQGreen = Color(0xFF31C27C)

/** First-run login: scan QQ/WeChat right on this screen, or skip into preview mode. Either
 *  way the app is entered through a green circle that grows from the tapped button until it
 *  covers the screen, so the main page appears to emerge from the button itself. */
@Composable
fun OnboardingScreen(vm: OnboardingViewModel, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsState()
    val keyboard = LocalSoftwareKeyboardController.current
    val motion = LocalUiMotion.current
    DisposableEffect(vm) {
        vm.startMutPolling()
        onDispose { vm.stopMutPolling() }
    }
    var revealCenter by remember { mutableStateOf<Offset?>(null) }
    var finished by remember { mutableStateOf(false) }
    fun enter(center: Offset) {
        if (finished || revealCenter != null) return
        if (motion.enabled) {
            revealCenter = center
        } else {
            finished = true
            keyboard?.hide()
            vm.finish()
            onDone()
        }
    }
    Box(modifier.fillMaxSize()) {
        OnboardingContent(
            state,
            onStart = { center -> enter(center) },
            onLoggedIn = { center -> enter(center) },
        )
        val center = revealCenter
        if (center != null) {
            GreenReveal(center) {
                if (!finished) {
                    finished = true
                    keyboard?.hide()
                    vm.finish()
                    onDone()
                }
            }
        }
    }
}

/** Brand-green circle growing from [center] until every pixel of the screen is covered. */
@Composable
private fun GreenReveal(center: Offset, onEnd: () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(520, easing = CubicBezierEasing(.32f, .72f, .35f, 1f)))
        onEnd()
    }
    Canvas(Modifier.fillMaxSize()) {
        // The diagonal is the worst-case distance from any center to the far corner.
        drawCircle(QQGreen, radius = hypot(size.width, size.height) * progress.value, center = center)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun OnboardingContent(state: OnboardingState, onStart: (Offset) -> Unit, onLoggedIn: (Offset) -> Unit, modifier: Modifier = Modifier) {
    val primaryFocus = remember { FocusRequester() }
    var buttonCenter by remember { mutableStateOf(Offset.Zero) }
    var panelCenter by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(120)
        runCatching { primaryFocus.requestFocus() }
    }
    BoxWithConstraints(modifier.fillMaxSize().background(com.qqmusictv.ui.theme.AppPalette.background)) {
        val compact = maxHeight < 480.dp
        val inset = pageHorizontalInset
        Row(
            Modifier.fillMaxSize().padding(horizontal = inset, vertical = if (compact) 24.dp else 40.dp),
            horizontalArrangement = Arrangement.spacedBy(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("QQ音乐", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = QQGreen)
                Text(tr(R.string.msg_362), fontSize = 36.sp, fontWeight = FontWeight.Bold, color = com.qqmusictv.ui.theme.AppPalette.adaptive(Color(0xFF1D1D1F), Color(0xFFF5F5F7)))
                Text(tr(R.string.msg_169),
                    fontSize = 16.sp, lineHeight = 24.sp, color = com.qqmusictv.ui.theme.AppPalette.adaptive(Color(0xFF6E6E73), Color(0xFFB1B1B8)))
                Spacer(Modifier.height(if (compact) 0.dp else 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(7.dp).background(if (state.hasMut) Color(0xFF248A3D) else com.qqmusictv.ui.theme.AppPalette.adaptive(Color(0xFFB6B6BC), Color(0xFFAAAAAF)), RoundedCornerShape(50)))
                    Text(when { state.checking -> tr(R.string.msg_173); state.tokenError != null -> com.qqmusictv.i18n.AppLanguage.message(state.tokenError).orEmpty(); state.hasMut -> tr(R.string.msg_174); else -> tr(R.string.msg_175) },
                        fontSize = 13.sp, lineHeight = 20.sp,
                        color = if (state.hasMut) Color(0xFF248A3D) else com.qqmusictv.ui.theme.AppPalette.adaptive(Color(0xFF6E6E73), Color(0xFFB1B1B8)))
                }
                Spacer(Modifier.height(4.dp))
                Surface(
                    onClick = { onStart(buttonCenter) },
                    modifier = Modifier.width(208.dp).height(52.dp)
                        .focusRequester(primaryFocus)
                        .onGloballyPositioned { buttonCenter = it.boundsInRoot().center },
                    shape = ClickableSurfaceDefaults.shape(ContentActionShape),
                    colors = com.qqmusictv.ui.components.contentActionColors(QQGreen),
                    border = com.qqmusictv.ui.components.contentActionBorder(),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 1f),
                    glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow.None),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(if (state.hasMut) tr(R.string.msg_176) else if (state.tokenStored) tr(R.string.msg_177) else tr(R.string.msg_178), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color.White)
                    }
                }
                Text(tr(R.string.msg_179), fontSize = 12.sp, color = com.qqmusictv.ui.theme.AppPalette.adaptive(Color(0xFF8E8E93), Color(0xFFA3A3AA)))
            }
            // Inline QR sign-in: WeChat/QQ tabs above the code, in the panel's original spot.
            // The panel screenshots the hidden page and shows only the enlarged code. A
            // confirmed scan enters the app through the same green reveal from the panel center.
            Column(Modifier.weight(1.15f).fillMaxHeight().onGloballyPositioned { panelCenter = it.boundsInRoot().center }) {
                QQWebLoginPanel(
                    onLoggedIn = { onLoggedIn(panelCenter) },
                    modifier = Modifier.fillMaxSize(),
                    initialFocus = false,
                )
            }
        }
    }
}

package com.qqmusictv.ui.components

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.focusGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.animation.core.MutableTransitionState
import kotlin.math.roundToInt
import com.qqmusictv.ui.navigation.TopNavTab
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Uses the actual navigation bar and the same nesting as AppShell/player. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w960dp-h540dp")
class MotionNavigationTest {
    @Before fun selectChineseForLegacyLabels() { com.qqmusictv.i18n.AppLanguage.select(com.qqmusictv.i18n.LanguageChoice.Chinese) }
    @get:Rule val compose = createComposeRule()

    @Test fun playerMotionWrappersDoNotConsumeBackAsFocusExit() {
        val play = FocusRequester()
        lateinit var manager: FocusManager
        compose.setContent {
            manager = LocalFocusManager.current
            FadeThrough(false, Modifier.fillMaxSize()) { // setup / app
                FadeThrough(true, Modifier.fillMaxSize()) { // empty / playing
                    FadeThrough(false, Modifier.fillMaxSize()) { // split / fullscreen
                        TouchSurface({}, Modifier.size(100.dp).focusRequester(play).testTag("play")) { Text("Play") }
                    }
                }
            }
        }
        compose.runOnIdle { assertTrue(play.requestFocus()) }
        compose.onNodeWithTag("play").assertIsFocused()
        compose.runOnIdle {
            // Compose handles a remote Back as focus Exit before the activity handler.
            // Opacity wrappers must add zero Exit stops ahead of the page BackHandler.
            assertFalse("Motion layers must not consume the first Back", manager.moveFocus(FocusDirection.Exit))
        }
    }

    @Test fun actualTabBarMovesLeftAndRightThroughAllDestinations() {
        val tabs = TopNavTab.entries.associateWith { FocusRequester() }
        var focused: TopNavTab? = null
        var selected by mutableStateOf(TopNavTab.Browse)
        compose.setContent {
            FadeThrough(false, Modifier.fillMaxSize()) {
                FadeVisibility(true, Modifier.fillMaxWidth().motionInput(true)) {
                    Box(Modifier.focusProperties { onEnter = { tabs.getValue(TopNavTab.Browse).requestFocus() } }.focusGroup()) {
                        TopNavBar(selected = selected, onSelect = { selected = it },
                            tabFocusRequesters = tabs,
                            onTabFocusChanged = { tab, active -> if (active) focused = tab else if (focused == tab) focused = null })
                    }
                }
            }
        }
        compose.runOnIdle { tabs.getValue(TopNavTab.Browse).requestFocus() }
        for (tab in listOf(TopNavTab.Library, TopNavTab.Dev, TopNavTab.Search)) {
            compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
            compose.runOnIdle { assertEquals(tab, focused); assertEquals(tab, selected) }
        }
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        compose.runOnIdle { assertEquals(TopNavTab.Search, focused) }
        compose.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        compose.runOnIdle { assertEquals(TopNavTab.Dev, focused); assertEquals(TopNavTab.Dev, selected) }
        for (tab in listOf(TopNavTab.Library, TopNavTab.Browse, TopNavTab.NowPlaying)) {
            compose.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
            compose.runOnIdle { assertEquals(tab, focused); assertEquals(tab, selected) }
        }
        compose.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        compose.runOnIdle { assertEquals(TopNavTab.NowPlaying, focused) }
    }

    @Test fun headerCentersCapsuleWithoutMovingContentStart() {
        var density = 1f
        var headerHeight = 0f
        var contentTop = 0f
        compose.setContent {
            density = LocalDensity.current.density
            Column(Modifier.width(960.dp)) {
                TopNavBar(TopNavTab.Browse, onSelect = {},
                    modifier = Modifier.onGloballyPositioned { headerHeight = it.size.height.toFloat() })
                Box(Modifier.height(100.dp).fillMaxWidth().onGloballyPositioned { contentTop = it.positionInWindow().y })
            }
        }
        val capsule = compose.onNodeWithTag("topNavCapsule").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            assertEquals(480f * density, capsule.center.x, 1f)
            assertEquals(18f * density, capsule.top, 1f)
            assertEquals(70f * density, headerHeight, 1f)
            assertEquals(70f * density, contentTop, 1f)
        }
    }

    @Test fun popupAnchorIsMetadataBoxAcrossOpenMoveCloseAndReopen() {
        var visible by mutableStateOf(false)
        var offset by mutableStateOf(150)
        var metadataBounds = IntRect.Zero
        val observed = mutableListOf<IntRect>()
        val provider = object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
                layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                observed.add(anchorBounds)
                return IntOffset(anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2,
                    anchorBounds.top - popupContentSize.height)
            }
        }
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.offset(offset.dp, 220.dp).size(300.dp, 60.dp).onGloballyPositioned {
                    val at = it.positionInWindow()
                    metadataBounds = IntRect(at.x.roundToInt(), at.y.roundToInt(),
                        at.x.roundToInt() + it.size.width, at.y.roundToInt() + it.size.height)
                }) {
                    Text("Song / Artist")
                    val state = remember { MutableTransitionState(false) }
                    state.targetState = visible
                    FadingAnchoredPopup(state, provider, onDismissRequest = { visible = false }) {
                        Box(Modifier.size(230.dp, 100.dp).testTag("menu")) { Text("Go to Artist") }
                    }
                }
            }
        }
        compose.runOnIdle { visible = true }
        compose.onNodeWithTag("menu").assertExists()
        compose.runOnIdle { assertEquals(metadataBounds, observed.last()); offset = 210 }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(metadataBounds, observed.last()); visible = false }
        compose.onNodeWithTag("menu").assertDoesNotExist()
        compose.runOnIdle { visible = true }
        compose.onNodeWithTag("menu").assertExists()
        compose.runOnIdle { assertEquals(metadataBounds, observed.last()) }
    }
}

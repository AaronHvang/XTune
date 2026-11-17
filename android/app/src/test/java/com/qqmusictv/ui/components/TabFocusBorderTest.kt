package com.qqmusictv.ui.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.qqmusictv.ui.navigation.TopNavTab
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class,qualifiers="w960dp-h600dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
class TabFocusBorderTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val tab=FocusRequester();private val content=FocusRequester()
    private lateinit var input:InputModeManager
    private var entry by mutableIntStateOf(0)
    private fun setup(reduceMotion:Boolean=false) {
        compose.setContent {
            input=LocalInputModeManager.current
            val manager=LocalFocusManager.current
            var request by remember { mutableIntStateOf(0) }
            var navFocused by remember { mutableStateOf(false) }
            // Same asynchronous clear -> frame -> focus handshake as AppShell.
            LaunchedEffect(request) {
                if(request>0) {manager.clearFocus(force=true);withFrameNanos {};tab.requestFocus()}
            }
            CompositionLocalProvider(LocalUiMotion provides UiMotion(enabled=!reduceMotion)) {
                Column(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF999999))) {
                    Box(Modifier.onFocusChanged {
                        if(it.hasFocus && !navFocused && input.inputMode==InputMode.Keyboard) entry++
                        navFocused=it.hasFocus
                    }.focusGroup()) {
                        TopNavBar(TopNavTab.Browse,{},tabFocusRequesters=mapOf(TopNavTab.Browse to tab),
                            contentFocusRequester=content,focusEntryId=entry,navigationHasFocus=navFocused)
                    }
                    Box(Modifier.returnToTabOnUp {request++}.size(150.dp).focusRequester(content)
                        .testTag("content").focusable())
                }
            }
        }
        compose.runOnIdle { input.requestInputMode(InputMode.Keyboard);content.requestFocus() }
        compose.waitForIdle();compose.mainClock.autoAdvance=false
        // Switching to keyboard mode lets the header claim the initial focus; the test only
        // counts entries caused by explicitly returning from the content below.
        compose.runOnIdle { entry=0 }
        compose.mainClock.advanceTimeBy(32)
    }
    private fun tick(ms:Long) {
        compose.runOnIdle {Snapshot.sendApplyNotifications()}
        compose.mainClock.advanceTimeBy(ms)
    }
    private fun returnUp() {
        compose.onNodeWithTag("content").performKeyInput {pressKey(Key.DirectionUp)}
        tick(48)
        compose.onNodeWithContentDescription(TopNavTab.Browse.label).assertIsFocused()
        tick(32)
    }
    private fun snapshot(name:String):Bitmap {
        lateinit var bitmap:Bitmap
        compose.runOnIdle {
            val view=compose.activity.window.decorView
            bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888);view.draw(Canvas(bitmap))
            val root=com.qqmusictv.testing.TestArtifacts.root
            val out=File(root,"artifacts/v3.0-v16-release/preview").apply {mkdirs()}
            File(out,"$name.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        }
        return bitmap
    }
    private fun borderDarkness(before:Bitmap, image:Bitmap, bounds:androidx.compose.ui.geometry.Rect):Long {
        var total=0L
        for(y in bounds.top.toInt().coerceAtLeast(0) until bounds.bottom.toInt().coerceAtMost(before.height))
            for(x in bounds.left.toInt().coerceAtLeast(0) until bounds.right.toInt().coerceAtMost(before.width))
                total+=(android.graphics.Color.red(before.getPixel(x,y))-android.graphics.Color.red(image.getPixel(x,y))).coerceAtLeast(0)
        return total
    }
    @Test fun actualFocusEntryFadesIn200Holds300AndFadesOut200WithoutMovingContent() {
        setup();val before=snapshot("border-before-focus")
        val bounds=compose.onNodeWithContentDescription(TopNavTab.Browse.label).fetchSemanticsNode().boundsInRoot
        returnUp();assertEquals(1,entry)
        val entering=snapshot("border-during-entry")
        tick(224)
        val active=snapshot("border-active")
        val enteringDark=borderDarkness(before,entering,bounds)
        val full=borderDarkness(before,active,bounds)
        assertTrue("Focus border must darken the pill on entry",enteringDark>0)
        assertTrue("Focus border must keep fading in",enteringDark<full)
        assertTrue("Focus border must be clearly visible",full>20000)
        assertEquals(bounds,compose.onNodeWithContentDescription(TopNavTab.Browse.label).fetchSemanticsNode().boundsInRoot)
        // All changed pixels must remain inside the existing pill; no scale or layout movement.
        for(y in 0 until before.height) for(x in 0 until before.width) {
            if(x<bounds.left.toInt()-1 || x>bounds.right.toInt()+1 || y<bounds.top.toInt()-1 || y>bounds.bottom.toInt()+1)
                assertEquals("Outside pill at $x,$y",before.getPixel(x,y),active.getPixel(x,y))
        }
        tick(180)
        assertEquals(full,borderDarkness(before,snapshot("border-at-hold"),bounds))
        tick(160)
        val halfway=borderDarkness(before,snapshot("border-during-fade"),bounds)
        assertTrue("Border must fade continuously: full=$full, intermediate=$halfway", halfway > full * 0.1 && halfway < full * 0.9)
        tick(220);val settled=snapshot("border-expired")
        assertEquals(0L,borderDarkness(before,settled,bounds))
    }
    @Test fun leavingDismissesBorderAndReentryRestartsHoldWithoutHeldUpRestartingIt() {
        setup();val bounds=compose.onNodeWithContentDescription(TopNavTab.Browse.label).fetchSemanticsNode().boundsInRoot
        val clean=snapshot("border-clean")
        returnUp();assertEquals(1,entry)
        assertTrue(borderDarkness(clean,snapshot("border-entered"),bounds)>0)
        compose.onNodeWithContentDescription(TopNavTab.Browse.label).performKeyInput {pressKey(Key.DirectionDown)}
        tick(32);compose.onNodeWithTag("content").assertIsFocused()
        assertEquals(0L,borderDarkness(clean,snapshot("border-dismissed-on-down"),bounds))
        returnUp();assertEquals(2,entry)
        compose.onNodeWithContentDescription(TopNavTab.Browse.label).performKeyInput {pressKey(Key.DirectionUp)}
        tick(32);assertEquals(2,entry)
        tick(800);assertEquals(0L,borderDarkness(clean,snapshot("border-not-restarted-by-up"),bounds))
    }
    @Test fun reduceMotionStillShowsTheFocusHint() {
        setup(reduceMotion=true)
        val bounds=compose.onNodeWithContentDescription(TopNavTab.Browse.label).fetchSemanticsNode().boundsInRoot
        val clean=snapshot("border-reduce-motion-before")
        returnUp()
        tick(160)
        assertTrue("Reduce motion must still show the hint",borderDarkness(clean,snapshot("border-with-reduce-motion"),bounds)>20000)
        tick(800);assertEquals(0L,borderDarkness(clean,snapshot("border-reduce-motion-expired"),bounds))
    }
}

package com.qqmusictv.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.*
import com.qqmusictv.R
import com.qqmusictv.data.qq.QQCookieStore
import com.qqmusictv.i18n.tr
import com.qqmusictv.ui.theme.AppPalette
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay

private const val LOGIN_URL = "https://y.qq.com/"

/** Desktop Chrome UA: QQ Music restricts the Android TV user agent. */
private const val DESKTOP_UA =
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

/** Retries because the sign-in panel mounts asynchronously after the page settles. */
private const val AUTO_CLICK_LOGIN_JS = """
(function () {
  var tries = 0;
  var timer = setInterval(function () {
    tries++;
    if (tries > 60) { clearInterval(timer); return; }
    try {
      var nodes = document.querySelectorAll('a,button,[role="button"]');
      for (var i = 0; i < nodes.length; i++) {
        var el = nodes[i];
        var text = (el.textContent || '').replace(/\s+/g, '');
        if (!text || text.length > 12 || text.indexOf('登录') === -1) continue;
        if (el.offsetParent === null) continue;
        el.click();
        clearInterval(timer);
        return;
      }
    } catch (e) {}
  }, 300);
  return true;
})();
"""

/**
 * Clicks the QQ / WeChat tab inside the sign-in panel. The D-pad cannot move focus between
 * DOM nodes, so the TV drives the tab from native buttons instead.
 */
private fun switchTabJs(label: String) = """
(function () {
  var label = '$label';
  var tries = 0;
  var timer = setInterval(function () {
    tries++;
    if (tries > 60) { clearInterval(timer); return; }
    try {
      var nodes = document.querySelectorAll('a,button,div,span,li,p');
      for (var i = 0; i < nodes.length; i++) {
        var el = nodes[i];
        var text = (el.textContent || '').replace(/\s+/g, '');
        if (text !== label) continue;
        if (el.offsetParent === null) continue;
        el.click();
        clearInterval(timer);
        return;
      }
    } catch (e) {}
  }, 250);
  return true;
})();
"""

@EntryPoint
@InstallIn(SingletonComponent::class)
interface QQWebLoginEntryPoint {
    fun cookieStore(): QQCookieStore
}

internal fun qqCookieStore(context: Context): QQCookieStore = EntryPointAccessors
    .fromApplication(context.applicationContext, QQWebLoginEntryPoint::class.java)
    .cookieStore()

/**
 * Full-screen QR sign-in dialog (Settings re-login). The body is [QQWebLoginPanel] so the
 * onboarding screen can embed the same flow inline.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun QQWebLoginDialog(onClose: () -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier.fillMaxSize().background(AppPalette.background)
                .padding(horizontal = 40.dp, vertical = 28.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(tr(R.string.msg_362), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = AppPalette.primary)
                    Text(tr(R.string.msg_363), fontSize = 13.sp, color = AppPalette.secondary)
                }
                Surface(
                    onClick = onClose,
                    modifier = Modifier.width(104.dp).height(44.dp),
                    shape = ClickableSurfaceDefaults.shape(ContentActionShape),
                    colors = contentActionColors(AppPalette.adaptive(Color(0xFFE8E8ED), Color(0xFF38383D))),
                    border = contentActionBorder(),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 1f),
                    glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow.None),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (com.qqmusictv.i18n.AppLanguage.code == "en") "Close" else "关闭",
                            fontSize = 15.sp, fontWeight = FontWeight.Medium,
                            color = AppPalette.adaptive(Color(0xFF1D1D1F), Color(0xFFF5F5F7)),
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            QQWebLoginPanel(onLoggedIn = onClose, modifier = Modifier.fillMaxWidth().weight(1f))
        }
    }
}

/**
 * Embeddable QR sign-in for QQ Music: WeChat/QQ tab buttons above the WebView that renders
 * y.qq.com's code. The resulting cookie is handed to [QQCookieStore], which the rest of the
 * app already polls; [onLoggedIn] fires as soon as the phone confirms the scan.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun QQWebLoginPanel(
    onLoggedIn: () -> Unit,
    modifier: Modifier = Modifier,
    initialFocus: Boolean = true,
) {
    val context = LocalContext.current
    val loggedIn = rememberUpdatedState(onLoggedIn)
    val cookieStore = remember(context) { qqCookieStore(context) }
    val webViewAvailable = remember(context) { runCatching { WebView(context).destroy() }.isSuccess }
    val holder = remember { WebViewHolder() }
    val wechatFocus = remember { FocusRequester() }
    var timedOut by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf(QrTab.QQ) }

    // Focus starts on the WeChat tab: one OK press reaches the usual sign-in method.
    if (initialFocus) {
        LaunchedEffect(Unit) {
            delay(120)
            runCatching { wechatFocus.requestFocus() }
        }
    }

    // QQ writes the cookie for y.qq.com once the QR scan is confirmed on the phone.
    LaunchedEffect(Unit) {
        val timeoutMs = 5 * 60 * 1000L
        var waited = 0L
        while (waited <= timeoutMs) {
            val cookie = runCatching {
                CookieManager.getInstance().getCookie(LOGIN_URL)
            }.getOrNull().orEmpty()
            if (cookie.contains("qqmusic_key=") || cookie.contains("qm_keyst=")) {
                runCatching { cookieStore.setCookie(cookie) }
                loggedIn.value()
                return@LaunchedEffect
            }
            delay(1_000)
            waited += 1_000
        }
        timedOut = true
    }

    DisposableEffect(Unit) {
        onDispose {
            holder.webView?.let { view ->
                runCatching { view.stopLoading() }
                runCatching { view.destroy() }
            }
            holder.webView = null
        }
    }

    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            listOf(QrTab.WECHAT, QrTab.QQ).forEach { tab ->
                LoginTabButton(
                    label = tab.title,
                    active = activeTab == tab,
                    focusRequester = if (tab == QrTab.WECHAT) wechatFocus else null,
                ) {
                    activeTab = tab
                    holder.webView?.evaluateJavascript(switchTabJs(tab.jsLabel), null)
                }
                Spacer(Modifier.width(10.dp))
            }
            if (timedOut) {
                Text(tr(R.string.msg_365), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFFB22222))
            }
        }
        Spacer(Modifier.height(16.dp))
        // Display only: the WebView must never take focus, or the page eats every D-pad key
        // and the user can no longer reach the tab buttons or the rest of the screen.
        Box(
            Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(18.dp))
                .background(if (webViewAvailable) Color.White else AppPalette.surface),
        ) {
            if (webViewAvailable) {
                AndroidView(
                    factory = { ctx -> createWebView(ctx, holder) },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Column(
                    Modifier.align(Alignment.Center).padding(32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(tr(R.string.msg_364), fontSize = 15.sp, lineHeight = 24.sp, color = AppPalette.primary)
                    Text("http://<电视IP>:8082/qq", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = AppPalette.accent)
                }
            }
        }
    }
}

private class WebViewHolder {
    var webView: WebView? = null
}

/** Sign-in methods the TV can switch between without a mouse. */
internal enum class QrTab(val title: String, val jsLabel: String) {
    WECHAT("微信扫码", "微信登录"),
    QQ("QQ 扫码", "QQ登录"),
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LoginTabButton(
    label: String,
    active: Boolean,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .width(122.dp)
            .height(44.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        shape = ClickableSurfaceDefaults.shape(ContentActionShape),
        colors = contentActionColors(
            if (active) AppPalette.adaptive(Color(0xFF31C27C), Color(0xFF31C27C))
            else AppPalette.adaptive(Color(0xFFE8E8ED), Color(0xFF38383D)),
        ),
        border = contentActionBorder(),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 1f),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow.None),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                label,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = if (active) Color.White
                else AppPalette.adaptive(Color(0xFF1D1D1F), Color(0xFFF5F5F7)),
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createWebView(context: Context, holder: WebViewHolder): WebView {
    val webView = WebView(context)
    holder.webView = webView
    // Pure display: page loads settle on their own (auto-login-click JS), the phone does the
    // scanning. If the WebView could take focus it would swallow all D-pad keys.
    webView.isFocusable = false
    webView.isFocusableInTouchMode = false
    runCatching {
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
            userAgentString = DESKTOP_UA
            // The QQ sign-in chain still serves some http assets.
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                runCatching { view.evaluateJavascript(AUTO_CLICK_LOGIN_JS, null) }
            }
        }
        webView.loadUrl(LOGIN_URL)
    }
    return webView
}

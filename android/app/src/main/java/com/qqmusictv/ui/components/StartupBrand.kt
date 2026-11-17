package com.qqmusictv.ui.components

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.os.Build
import android.widget.ImageView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.qqmusictv.R
import kotlinx.coroutines.delay

const val StartupLogoWidthDp = 245.76f
const val StartupLogoAspect = 74.117f / 14.076f

/** The brand clip is 4.0 s long; the app takes over the moment it ends. */
const val StartupBrandMs = 4_000L

val StartupGreen = Color(0xFF31C27C)

@Composable
fun StartupBrand(modifier: Modifier = Modifier) {
    // Animated WebP keeps the brand clip off the media stack: it is a still drawable that
    // happens to move, so no player, no surface, no decoder handshake before the first frame.
    if (Build.VERSION.SDK_INT >= 28) AnimatedBrand(modifier) else PaintedBrand(modifier)
}

@androidx.annotation.RequiresApi(28)
@Composable
private fun AnimatedBrand(modifier: Modifier) {
    var broken by remember { mutableStateOf(false) }
    if (broken) {
        PaintedBrand(modifier)
        return
    }
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(android.graphics.Color.BLACK)
                try {
                    val source = ImageDecoder.createSource(ctx.resources, R.drawable.xtune_boot)
                    val drawable = ImageDecoder.decodeDrawable(source) { _, _, _ -> }
                    // Play once and hold the settled logo instead of looping the whole clip.
                    if (drawable is AnimatedImageDrawable) drawable.repeatCount = 0
                    setImageDrawable(drawable)
                } catch (e: Throwable) {
                    broken = true
                }
            }
        },
        update = { view -> (view.drawable as? AnimatedImageDrawable)?.start() },
    )
}

/** The old green wordmark, kept for API 23-27 and as the fallback if the clip will not decode. */
@Composable
private fun PaintedBrand(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "startupPulse")
    val scale by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(1600, easing = androidx.compose.animation.core.FastOutSlowInEasing)),
        label = "startupScale",
    )
    Box(modifier.fillMaxSize().background(StartupGreen), contentAlignment = Alignment.Center) {
        Text(
            text = "QQ音乐",
            color = Color.White,
            fontSize = 64.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.scale(scale),
        )
    }
}

/**
 * The content is already drawn beneath the overlay while data loads. The brand clip owns the
 * screen for its whole 4 s, then fades out as soon as the app behind it is ready.
 */
@Composable
fun StartupHost(content: @Composable (() -> Unit) -> Unit) {
    var ready by remember { mutableStateOf(false) }
    var brandDone by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        android.util.Log.i("BrandStartup", "overlay composed; clip starts")
        delay(StartupBrandMs)
        brandDone = true
        android.util.Log.i("BrandStartup", "clip done")
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        content { ready = true }
        AnimatedVisibility(visible = !(brandDone && ready), enter = EnterTransition.None, exit = fadeOut(tween(220))) {
            StartupBrand()
        }
    }
}

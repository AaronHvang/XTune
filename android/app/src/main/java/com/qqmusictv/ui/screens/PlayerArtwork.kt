package com.qqmusictv.ui.screens



import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import androidx.tv.material3.*
import coil.ImageLoader
import coil.request.ImageRequest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Looping, muted motion album-art video layered over the static cover. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun MotionCover(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var ready by remember(url) { mutableStateOf(false) }

    val exo = remember(url) {
        // The motion-art master offers variants up to ~6 Mbps 1080p HEVC. That's absurd for a
        // small square loop and it choked this MediaTek Fire TV (sustained 4-6 Mbps HEVC → visibly
        // choppy). Cap the ladder to a small, low-bitrate rung — the cover is only ~300dp.
        val selector = androidx.media3.exoplayer.trackselection.DefaultTrackSelector(context).apply {
            parameters = buildUponParameters()
                .setMaxVideoSize(640, 640)
                .setMaxVideoBitrate(1_500_000)
                // Muted cover loop: no audio track means no audio playback configuration, so
                // PlayerViewModel's "another app started playing" detector can't see it.
                .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_AUDIO, true)
                .build()
        }
        androidx.media3.exoplayer.ExoPlayer.Builder(context).setTrackSelector(selector).build().apply {
            setMediaItem(androidx.media3.common.MediaItem.fromUri(url))
            repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL
            volume = 0f
            playWhenReady = true
            addListener(object : androidx.media3.common.Player.Listener {
                // ready tracks an ACTUAL rendered frame, not buffer state. A buffered-but-not-yet-
                // drawn surface is exactly when the green YUV frame shows, so gating the fade on
                // onRenderedFirstFrame (not STATE_READY) means alpha never reveals a green surface.
                override fun onRenderedFirstFrame() { ready = true }
            })
            prepare()
        }
    }

    // On resume from background the surface reattaches and briefly shows a green
    // YUV frame. Reset ready=false on pause so the shutter hides it until the
    // first decoded frame arrives.
    DisposableEffect(lifecycleOwner, exo) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> { ready = false; exo.pause() }
                Lifecycle.Event.ON_RESUME -> {
                    // Keep the shutter up (ready=false) until the decoder redraws a real frame —
                    // onRenderedFirstFrame flips it. Never optimistically reveal the surface.
                    ready = false
                    exo.play()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exo.release()
        }
    }

    val alpha by animateFloatAsState(
        if (ready) 1f else 0f,
        tween(825, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "motionFade",
    )

    // PlayerView → SurfaceView: the video gets its OWN hardware overlay plane, so the GPU
    // barely touches it. A TextureView composites the 1080p HEVC frame through the shared
    // GPU layer every frame, fighting the DynamicBackground Screen-blend passes — that made
    // the cover choppy on Fire TV. RESIZE_MODE_ZOOM fills the box; the black shutter + alpha
    // fade hide the green-frame handoff instead.
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { ctx ->
            androidx.media3.ui.PlayerView(ctx).apply {
                useController = false
                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                player = exo
            }
        },
        update = { view -> view.player = exo },
        modifier = modifier.graphicsLayer { this.alpha = alpha },
    )
}

private fun Color.hsvHue(): Float {
    val r = red; val g = green; val b = blue
    val max = maxOf(r, g, b); val min = minOf(r, g, b)
    val delta = max - min
    if (delta < 0.001f) return 0f
    val h = when (max) {
        r -> 60f * (((g - b) / delta).mod(6f))
        g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }
    return if (h < 0f) h + 360f else h
}

private fun hueDist(a: Float, b: Float): Float {
    val d = kotlin.math.abs(a - b); return minOf(d, 360f - d)
}

/** Make a lighter/darker (and slightly de/re-saturated) shade of [c] — SAME hue. Used to fill orb
 *  slots from an album that only has one colour family, so we never invent a hue (blue/green) the
 *  artwork doesn't contain. step 0 = lighter, 1 = darker, 2 = lighter+, … */
private fun varyShade(c: Color, step: Int): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(android.graphics.Color.rgb((c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt()), hsv)
    val dir = if (step % 2 == 0) 1f else -1f
    val mag = 0.18f + 0.12f * (step / 2)
    hsv[2] = (hsv[2] + dir * mag).coerceIn(0.30f, 0.98f)
    hsv[1] = (hsv[1] * (1f - dir * 0.12f)).coerceIn(0.35f, 1f)   // lighter reads a touch less saturated
    return Color(android.graphics.Color.HSVToColor(hsv))
}

// Pick up to n colors distinct in hue (>= minAngle apart). If the album doesn't HAVE that many
// separated accents, fill the rest with lighter/darker SHADES of the accents we found — so every orb
// colour still comes from the artwork (an orange cover gives orange shades, never a fake blue/green).
private fun spreadByHue(colors: List<Color>, n: Int, minAngle: Float = 20f): List<Color> {
    val result = mutableListOf<Color>()
    val chosen = mutableListOf<Pair<Float, Float>>()   // (hue, value)
    // Keep a swatch if it adds EITHER hue variety OR tonal variety. An album that is one
    // colour family (most are) still yields several real orbs — vibrant / muted / dark /
    // light variants of that family — instead of collapsing to one hue and then padding
    // with computed shades. This is what gives the "oil painting" spread. We only
    // drop a swatch that is near-identical in BOTH hue and brightness to one already kept.
    for (c in colors) {
        val h = c.hsvHue()
        val v = maxOf(c.red, c.green, c.blue)
        if (chosen.all { (ch, cv) -> hueDist(h, ch) >= minAngle || kotlin.math.abs(v - cv) >= 0.16f }) {
            result.add(c); chosen.add(h to v)
            if (result.size == n) break
        }
    }
    if (result.size < n) {
        val src = result.toList().ifEmpty { colors.take(1) }.ifEmpty { listOf(Color(0xFF888888)) }
        var k = 0
        while (result.size < n) {
            result.add(varyShade(src[k % src.size], k / src.size)); k++
        }
    }
    return result
}

// Monochrome equivalent of spreadByHue: separate greys by BRIGHTNESS (hue is meaningless when
// every swatch is grey, so spreadByHue would collapse them all onto the first one).
private fun spreadByValue(colors: List<Color>, n: Int, minGap: Float = 0.13f): List<Color> {
    val result = mutableListOf<Color>()
    val chosen = mutableListOf<Float>()
    for (c in colors) {
        val v = maxOf(c.red, c.green, c.blue)
        if (chosen.all { kotlin.math.abs(v - it) >= minGap }) {
            result.add(c); chosen.add(v)
            if (result.size == n) break
        }
    }
    if (result.size < n) {
        for (c in colors) {
            if (c !in result) { result.add(c); if (result.size == n) break }
        }
    }
    return result
}

// Backdrop vibrancy. Raising SAT_* makes colors read as colors rather than tints;
// VALUE_CEILING is the safety rail that keeps them from turning pale and competing
// with the white lyrics on the right half of the screen. Don't push it past ~0.85.
private const val SAT_BOOST = 1.45f
private const val SAT_FLOOR = 0.55f
private const val VALUE_CEILING = 0.80f

/** Extracts a dark base color + a vibrant accent color from the artwork. */
@Composable
internal fun rememberArtworkPalette(artworkUrl: String?, seed: String = ""): List<Color> {
    val context = LocalContext.current
    val fallback = listOf(Color(0xFF0A0A0A), Color(0xFF0D0D0D), Color(0xFF0A0A0A), Color(0xFF111111), Color(0xFF080808), Color(0xFF0D0D0D))
    // A song with NO usable artwork (obscure "internet" library tracks) shouldn't sit on a
    // flat grey backdrop. Derive a stable, muted 6-colour set from a hash of the song id so
    // each one gets its own quiet colour instead — real B&W covers still go grey via the
    // monochrome branch below (they DO produce a bitmap).
    val seeded = remember(seed) {
        if (seed.isEmpty()) fallback else {
            val h = (seed.hashCode() and 0x7FFFFFFF)
            val baseHue = (h % 360).toFloat()
            val hsv = FloatArray(3)
            List(6) { i ->
                hsv[0] = (baseHue + i * 47f) % 360f
                hsv[1] = 0.55f
                hsv[2] = 0.34f + (i % 3) * 0.06f   // deep, below the white-lyric competition line
                Color(android.graphics.Color.HSVToColor(hsv))
            }
        }
    }
    var colors by remember(artworkUrl) { mutableStateOf(seeded) }
    LaunchedEffect(artworkUrl) {
        if (artworkUrl == null) { colors = seeded; return@LaunchedEffect }
        try {
            // Decode a SMALL bitmap for the palette — Palette downsamples internally anyway, so a
            // 1200² ARGB bitmap (~5.7 MB, kept in RAM with allowHardware off) was pure waste on a
            // memory-starved Fire TV. 256² is ~256 KB and gives an identical palette. Reuse Coil's
            // shared loader instead of spinning up a new ImageLoader per song.
            val loader = context.applicationContext.let { coil.Coil.imageLoader(it) }
            // Software (RGB_565) bitmap so Palette can always read the pixels — a hardware bitmap
            // (or a null drawable from the previously-disabled cache path) made execute() return
            // nothing for some covers (DIGIDI DIGIDI: real blue/purple art, grey backdrop). Let it
            // reuse Coil's cache too, and retry once, so a single transient miss doesn't fall to grey.
            fun paletteRequest() = ImageRequest.Builder(context).data(artworkUrl).size(256)
                .allowHardware(false).bitmapConfig(android.graphics.Bitmap.Config.RGB_565).build()
            var bitmap = (loader.execute(paletteRequest()).drawable as? BitmapDrawable)?.bitmap
            if (bitmap == null) {
                kotlinx.coroutines.delay(250)
                bitmap = (loader.execute(paletteRequest()).drawable as? BitmapDrawable)?.bitmap
            }
            if (bitmap == null) { colors = seeded; return@LaunchedEffect }   // still nothing → seeded, not grey
            // Finer quantization (32 vs 16) surfaces smaller accent regions — a teal logo, a red
            // jacket — that the 6 named roles miss entirely.
            val p = Palette.from(bitmap).maximumColorCount(32).generate()
            val hsvTmp = FloatArray(3)
            // Use EVERY quantized swatch, not just the 6 named roles, so real accent colours are on
            // the table. Rank by a blend of saturation and prominence so a vivid accent outranks a
            // large dull background — that's what "grab more accent colours" needs.
            val named = listOfNotNull(
                p.vibrantSwatch, p.lightVibrantSwatch, p.darkVibrantSwatch,
                p.mutedSwatch, p.lightMutedSwatch, p.darkMutedSwatch, p.dominantSwatch,
            )
            val maxPop = (p.swatches.maxOfOrNull { it.population } ?: 1).coerceAtLeast(1)
            val swatches = (named + p.swatches).distinctBy { it.rgb }
                .sortedByDescending { sw ->
                    android.graphics.Color.colorToHSV(sw.rgb, hsvTmp)
                    hsvTmp[1] * 0.62f + (sw.population.toFloat() / maxPop) * 0.38f
                }
            // Detect a grey / black-and-white cover from the ORIGINAL saturations, BEFORE the boost:
            // once every swatch is floored to SAT_FLOOR they all look colourful and the test can't
            // tell. A monochrome sleeve keeps its greys (and can go near-white), separated by
            // brightness instead of hue, so the orbs read as three shades of grey — not invented colour.
            val hsv = FloatArray(3)
            val maxSat = swatches.maxOfOrNull { android.graphics.Color.colorToHSV(it.rgb, hsv); hsv[1] } ?: 0f
            val monochrome = maxSat < 0.18f
            val picked = swatches.map { swatch ->
                android.graphics.Color.colorToHSV(swatch.rgb, hsv)
                if (monochrome) {
                    hsv[1] = 0f                                              // true grey, no invented hue
                    // Keep greys GREY — a near-white orb competes with the white lyrics. Cap well below
                    // white so monochrome art reads as smoky grey pools, not glowing white blobs.
                    hsv[2] = (0.30f + hsv[2] * 0.42f).coerceIn(0.30f, 0.68f)
                } else {
                    // Deep saturated colours read as colour; pastels read as light grey. Floor the
                    // saturation and cap the value so a pale swatch doesn't wash out the lyrics.
                    hsv[1] = (hsv[1] * SAT_BOOST).coerceIn(SAT_FLOOR, 1f)
                    hsv[2] = hsv[2].coerceAtMost(VALUE_CEILING)
                }
                Color(android.graphics.Color.HSVToColor(hsv))
            }.distinct()
            val dom = Color(p.getDominantColor(0xFF050505.toInt()))
            val domLum = 0.2126f * dom.red + 0.7152f * dom.green + 0.0722f * dom.blue

            colors = when {
                domLum < 0.06f && !monochrome -> fallback   // truly black colour art — nothing to extract
                picked.size >= 2 -> if (monochrome) spreadByValue(picked, 6) else spreadByHue(picked, 6)
                else -> {
                    val dark  = Color(dom.red * 0.4f, dom.green * 0.4f, dom.blue * 0.4f)
                    val light = Color((dom.red + 0.3f).coerceAtMost(1f), (dom.green + 0.3f).coerceAtMost(1f), (dom.blue + 0.3f).coerceAtMost(1f))
                    listOf(dom, light, dark, dom, light, dark)
                }
            }
        } catch (_: Exception) {}
    }
    return colors
}

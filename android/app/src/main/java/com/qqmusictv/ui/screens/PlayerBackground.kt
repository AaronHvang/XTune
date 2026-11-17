package com.qqmusictv.ui.screens

import com.qqmusictv.i18n.tr
import com.qqmusictv.R

import com.qqmusictv.ui.components.LocalUiMotion

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode as AnimRepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.tv.material3.*
import com.qqmusictv.ui.viewmodel.NowPlayingBackground

@Composable
internal fun referenceTextHeight(text: String, style: TextStyle): Dp {
    val measurer = rememberTextMeasurer()
    val resolvedStyle = LocalTextStyle.current.merge(style)
    val height = measurer.measure(text, resolvedStyle, maxLines = 1, softWrap = false).size.height
    return with(LocalDensity.current) { height.toDp() }
}

/**
 * Small "下一首…" toast, bottom-right, that fades in ~15s before the track switches.
 * Reads the per-frame clock via derivedStateOf so only the boolean flip recomposes it.
 */
@Composable
internal fun BoxScope.NextUpToast(progressState: androidx.compose.runtime.State<Long>, durationMs: Long, nextTitle: String?, songKey: String, visible: Boolean = true,
) {
    val rawShow by remember(durationMs, nextTitle, visible) {
        androidx.compose.runtime.derivedStateOf {
            visible && nextTitle != null && durationMs > 0L && (durationMs - progressState.value) in 1_000L..15_000L
        }
    }
    // Capture the "next" title ONCE, when the toast arms, and keep showing that. At a track boundary
    // the queue advances a few frames before the current song does, so the live next-title briefly
    // points one song too far ahead (the "next next"). Freezing the captured title ignores that
    // transient; a real track change flips songKey and resets us.
    var shownTitle by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(songKey) { shownTitle = null }
    LaunchedEffect(rawShow, songKey) {
        if (!rawShow) { shownTitle = null; return@LaunchedEffect }
        kotlinx.coroutines.delay(400)
        if (rawShow) shownTitle = nextTitle
    }
    var lastTitle by remember { mutableStateOf<String?>(null) }
    SideEffect { if (shownTitle != null) lastTitle = shownTitle }
    val motion = LocalUiMotion.current
    val alpha by animateFloatAsState(if (shownTitle != null) 1f else 0f,
        tween(if (motion.enabled) motion.enterMs else 0), label = "nextToast")
    val title = shownTitle ?: lastTitle
    if (alpha <= 0.01f || title == null) return
    Column(
        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 30.dp, bottom = 52.dp)
            .graphicsLayer { this.alpha = alpha }
            .background(Color(0xE6161616), RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(tr(R.string.msg_283), style = TextStyle(fontFamily = LocalTextStyle.current.fontFamily, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF31C27C), letterSpacing = 1.5.sp))
        Text(title, maxLines = 1, style = TextStyle(fontFamily = LocalTextStyle.current.fontFamily, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White))
    }
}

/**
 * Full-screen backdrop: blurred album artwork (loaded tiny → upscaled) with
 * 4 radial color blobs drifting in quadrants, Screen-blended on top.
 * Beat energy pulses blob radius and alpha.
 */
@Composable
private fun DynamicBackground(artworkUrlTemplate: String?, songKey: String, beatAnalyzer: com.qqmusictv.media.BeatAnalyzer, beatMultiplier: Float = 1f, mode: NowPlayingBackground = NowPlayingBackground.DYNAMIC, playing: Boolean = true, orbSpeed: Float = 1f, reduceMotion: Boolean = false, lowPower: Boolean = false) {
    // BLACK: plain black, no blobs, no beat. Nothing else to compute.
    if (mode == NowPlayingBackground.BLACK) {
        Box(Modifier.fillMaxSize().background(Color.Black))
        return
    }
    val projector = mode == NowPlayingBackground.PROJECTOR

    // Intensity (Calm 0.55 … Crazy 3.5) is applied as a render AMPLITUDE below, NOT by multiplying the
    // level and clipping to 1 — that just pinned everything at max, so Strong and Crazy looked the same
    // and the vocal orb sat maxed. Levels stay 0..1; the multiplier drives how big/bright they swell.
    val amp = beatMultiplier
    // PERF: everything animated below is kept as State and read INSIDE drawBehind, never with `by` at
    // composable scope. Reading them here made DynamicBackground recompose ~60×/sec — rebuilding colour
    // lists and brushes every frame, which was the app's main GC-churn source. Read in the draw lambda,
    // only the draw phase re-runs each frame; the composable recomposes only on song/palette change.
    val rawEnergy by beatAnalyzer.energy.collectAsState()
    // Critically damped (dampingRatio 1.0), not the old 0.5: an underdamped spring overshoots and
    // rings after every hit, which read as the punch "bouncing all over the place". 1.0 gives a snappy
    // attack that settles cleanly with no wobble, so each beat lands once and decays.
    val energyState = animateFloatAsState(rawEnergy.coerceIn(0f, 1f), androidx.compose.animation.core.spring(dampingRatio = 1f, stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow), label = "beat")

    val rawBands by beatAnalyzer.bands.collectAsState()
    val bandSpring = androidx.compose.animation.core.spring<Float>(dampingRatio = 0.6f, stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow)
    val band0State = animateFloatAsState(rawBands.getOrElse(0) { 0f }.coerceIn(0f, 1f), bandSpring, label = "bass")
    val band1State = animateFloatAsState(rawBands.getOrElse(1) { 0f }.coerceIn(0f, 1f), bandSpring, label = "vocal")
    val band2State = animateFloatAsState(rawBands.getOrElse(2) { 0f }.coerceIn(0f, 1f), bandSpring, label = "treble")

    val paletteUrl = artworkUrlTemplate?.replace("{w}", "300")?.replace("{h}", "300")?.replace("{f}", "jpg")
    val palette = rememberArtworkPalette(paletteUrl, seed = songKey)
    val animatedStates = palette.mapIndexed { i, c ->
        animateColorAsState(c, tween(1500), label = "blob$i")
    }

    // Orb speed scales the drift periods (faster speed → shorter tween).
    val sp = orbSpeed.coerceIn(0.4f, 2.0f)
    val infinite = rememberInfiniteTransition(label = "pool")
    val t1s = infinite.animateFloat(0f, 1f, infiniteRepeatable(tween((20_000 / sp).toInt(), easing = LinearEasing), AnimRepeatMode.Reverse), label = "t1")
    val t2s = infinite.animateFloat(0f, 1f, infiniteRepeatable(tween((27_000 / sp).toInt(), easing = LinearEasing), AnimRepeatMode.Reverse), label = "t2")
    val t3s = infinite.animateFloat(0f, 1f, infiniteRepeatable(tween((34_000 / sp).toInt(), easing = LinearEasing), AnimRepeatMode.Reverse), label = "t3")
    val t4s = infinite.animateFloat(0f, 1f, infiniteRepeatable(tween((15_000 / sp).toInt(), easing = LinearEasing), AnimRepeatMode.Reverse), label = "t4")

    // PERF + accessibility: freeze the perpetual drift while paused OR when Reduce Motion is on. We
    // snapshot the drift phase and read that in the draw instead of the live State, so the draw phase
    // isn't invalidated 60×/sec. Beat energy/bands settle to 0 on their own (and Reduce Motion zeroes
    // them below, so the orbs hold completely still).
    val moving = playing && !reduceMotion
    val frozen = remember { floatArrayOf(0f, 0f, 0f, 0f) }
    LaunchedEffect(moving) {
        if (!moving) { frozen[0] = t1s.value; frozen[1] = t2s.value; frozen[2] = t3s.value; frozen[3] = t4s.value }
    }

    // PROJECTOR uses TRUE black; a projector throws light on a wall, so the near-black #050505 lift
    // that stops a panel crushing shadows becomes a visible grey rectangle instead.
    Box(Modifier.fillMaxSize().background(if (projector) Color.Black else Color(0xFF050505))) {
        Box(Modifier.fillMaxSize().drawBehind {
            val w = size.width; val h = size.height
            // Deferred reads — this is the draw phase, so these re-run per frame without recomposing.
            // While paused we read the frozen snapshot (not the live State) so the draw stops updating.
            val t1 = if (moving) t1s.value else frozen[0]
            val t2 = if (moving) t2s.value else frozen[1]
            val t3 = if (moving) t3s.value else frozen[2]
            val t4 = if (moving) t4s.value else frozen[3]
            val n = animatedStates.size
            val motionAmp = if (reduceMotion) 0f else 1f   // Reduce Motion → orbs hold at base, no pulse

            if (projector) {
                // THREE ORBS, ONE PER BAND — bass (slow, low), vocal (centre-panned, mid), treble
                // (fast, high). Each rides an ellipse on its own animator+phase so the composition
                // never repeats, swells on its band, and glows from a palette colour.
                // Small drift toward the next accent so colour evolves but each orb keeps its identity.
                val orbColors = List(3) { i -> lerp(animatedStates[i % n].value, animatedStates[(i + 1) % n].value, 0.05f + 0.10f * floatArrayOf(t1, t2, t3)[i]) }
                val orbLevels = floatArrayOf(band0State.value * motionAmp, band1State.value * motionAmp, band2State.value * motionAmp)
                // Pushed right of the album art (which sits in the left column) so no orb hides behind it.
                val anchorX = floatArrayOf(0.52f, 0.62f, 0.72f)
                val anchorY = floatArrayOf(0.44f, 0.56f, 0.46f)
                val phase   = floatArrayOf(0f, 2.1f, 4.2f)
                val drift   = floatArrayOf(t1, t2, t3)
                val twoPi = (2.0 * Math.PI).toFloat()
                // Smaller orbs than the full-screen blobs — a projector glow is a light source, not a
                // wash. Wider drift so they still roam the frame at the smaller size.
                // Per-band character so the three read as three: bass is the big slow one, treble the
                // small snappy one, vocal in between. Each is DIM at rest and swells hard on its band,
                // so the beat is the difference you see — not a constant wash.
                val bandBaseR = floatArrayOf(minOf(w, h) * 0.26f, minOf(w, h) * 0.21f, minOf(w, h) * 0.16f)
                val sizeRide  = floatArrayOf(0.75f, 0.85f, 1.05f)   // treble punches biggest relative to size
                for (i in 0 until 3) {
                    val lvl = orbLevels[i]
                    // Gentler drift so the orbs roam slowly instead of swimming around the frame.
                    val cx = anchorX[i] * w + cos(drift[i] * twoPi + phase[i]) * 0.08f * w
                    val cy = anchorY[i] * h + sin(drift[i] * twoPi + phase[i]) * 0.045f * h
                    val c = Offset(cx, cy)
                    val col = orbColors[i]
                    // Halo alpha now RIDES the beat: dim at rest (won't wash the lyrics), bright on the
                    // hit. On true black under Screen blend that's exactly the expressive pulse we want.
                    // Intensity (amp) scales the SWELL, not the base: Calm barely moves, Crazy swings big
                    // and bright. Generous caps so higher tiers stay visibly punchier instead of clipping.
                    val r = bandBaseR[i] * (1f + (lvl * sizeRide[i] * amp).coerceAtMost(2.2f))
                    val haloA = (0.24f + lvl * 0.42f * amp).coerceAtMost(0.9f)
                    drawCircle(
                        brush = Brush.radialGradient(listOf(col.copy(alpha = haloA), col.copy(alpha = 0f)), center = c, radius = r),
                        radius = r, center = c, blendMode = BlendMode.Screen,
                    )
                    // Core: small, whitened (a real glow's hottest point desaturates), punches hard on
                    // the band — a tiny fraction of the area, so it can flare without lifting the black.
                    // Low Power skips the core (a second gradient per orb per frame) — the halo carries it.
                    if (!lowPower) {
                        val cr = r * 0.34f
                        val core = lerp(col, Color.White, 0.6f)
                        drawCircle(
                            brush = Brush.radialGradient(listOf(core.copy(alpha = (0.12f + lvl * 0.62f * amp).coerceAtMost(0.92f)), core.copy(alpha = 0f)), center = c, radius = cr),
                            radius = cr, center = c, blendMode = BlendMode.Screen,
                        )
                    }
                }
                // A LIGHT edge fade only — just enough to guarantee no lit rectangle at the frame edge.
                // The orbs are small and pulled inward, so they never reach the edge anyway; a heavy
                // vignette here was painting black back over the glow and killing it.
                val ev = h * 0.10f; val eh = w * 0.10f
                drawRect(Brush.verticalGradient(listOf(Color.Black, Color(0x00000000)), startY = 0f, endY = ev))
                drawRect(Brush.verticalGradient(listOf(Color(0x00000000), Color.Black), startY = h - ev, endY = h))
                drawRect(Brush.horizontalGradient(listOf(Color.Black, Color(0x00000000)), startX = 0f, endX = eh))
                drawRect(Brush.horizontalGradient(listOf(Color(0x00000000), Color.Black), startX = w - eh, endX = w))
                // Right-side darkening for lyrics readability (a gradient, not an edge). Stronger now —
                // a bright orb drifting under the lyric column was washing out the dim inactive lines.
                drawRect(Brush.horizontalGradient(listOf(Color(0x00000000), Color(0x9E000000)), startX = w * 0.30f, endX = w))
                return@drawBehind
            }

            // DYNAMIC — four drifting album-colour blobs. Intensity (amp) scales the beat swing;
            // Reduce Motion (motionAmp=0) holds them still.
            val energy = energyState.value * motionAmp
            // 3 blobs, not 4: each BlendMode.Screen blob forces a full-screen offscreen compositing
            // pass recorded every frame, and on Fire TV that draw-command recording (behind the
            // per-word lyric text) was the measured jank on this screen. 3 colour pools still read as
            // the oil-painting spread; the 4th blob was mostly overlap.
            val blobCount = if (lowPower) 2 else 3
            // Each blob is PINNED to its own distinct palette colour — no pair-crossfade. The old
            // lerp blended two colours per blob and, with big overlapping blobs under Screen, merged
            // the whole thing into one moving gradient wash. One colour per blob + smaller radius
            // (below) lets the separate colours read as distinct pools, like the oil-painting
            // patches, instead of averaging into a single tint.
            val colors4 = List(4) { i -> animatedStates[i % n].value }
            val eAmp = (energy * amp).coerceIn(0f, 2.2f)
            // Per-corner band ride (projector-mode flavour): each corner breathes on its own
            // frequency band on top of the shared beat, so bass thumps two corners and treble
            // shimmers the other two — the palette pools now read the spectrum, not one pulse.
            val bandRide = floatArrayOf(
                band0State.value,   // top-left  → bass
                band2State.value,   // top-right → treble
                band1State.value,   // bot-left  → vocal/mid
                band0State.value,   // bot-right → bass
            )
            val beatScale = 1f + eAmp * 0.25f
            val beatAlpha = (0.60f + eAmp * 0.20f).coerceAtMost(0.95f)
            // Smaller than the old 0.62 so blobs overlap less and stay recognisably separate colours.
            val r = maxOf(w, h) * 0.42f * beatScale
            val nudge = eAmp * maxOf(w, h) * 0.02f
            val nudgeOffsets = listOf(
                Offset( nudge,  nudge * 0.5f),
                Offset(-nudge, -nudge * 0.7f),
                Offset( nudge * 0.6f, -nudge),
                Offset(-nudge * 0.4f,  nudge * 0.8f),
            )
            val centers = listOf(
                Offset(lerp(0.02f, 0.28f, t1) * w, lerp(0.05f, 0.32f, t2) * h),
                Offset(lerp(0.72f, 0.98f, t2) * w, lerp(0.05f, 0.35f, t3) * h),
                Offset(lerp(0.05f, 0.30f, t3) * w, lerp(0.68f, 0.95f, t1) * h),
                Offset(lerp(0.70f, 0.98f, t1) * w, lerp(0.65f, 0.95f, t3) * h),
            ).mapIndexed { i, c -> c + nudgeOffsets[i] }
            colors4.take(blobCount).forEachIndexed { i, color ->
                val ride = (bandRide[i] * amp).coerceIn(0f, 1.6f)
                val rr = r * (1f + ride * 0.30f)
                val aa = (beatAlpha + ride * 0.18f).coerceAtMost(0.98f)
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(color.copy(alpha = aa), color.copy(alpha = 0f)),
                        center = centers[i], radius = rr,
                    ),
                    radius = rr, center = centers[i],
                    blendMode = BlendMode.Screen,
                )
            }
            // Center darkening
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0x77000000), Color(0x00000000)),
                    center = Offset(w * 0.5f, h * 0.5f), radius = maxOf(w, h) * 0.55f,
                ),
                radius = maxOf(w, h) * 0.55f, center = Offset(w * 0.5f, h * 0.5f),
            )
            // Right-side darkening for lyrics readability
            drawRect(
                brush = Brush.horizontalGradient(
                    listOf(Color(0x00000000), Color(0x7A000000)),
                    startX = w * 0.35f, endX = w,
                ),
            )
            // Kept low: the flat veil mutes every hue equally, so readability comes
            // from the right-side gradient above instead.
            drawRect(Color(0x22000000))
        })
    }
}

@Composable
internal fun MusicalDots(
    progressState: State<Long>, offsetMs: Long, gapStartMs: Long, gapEndMs: Long,
    modifier: Modifier = Modifier,
) {
    // Only this leaf observes the per-frame clock; the lyric list remains coarse-grained.
    val progressMs = progressState.value + offsetMs
    val dotState = lyricDotsFrame(progressMs, gapStartMs, gapEndMs)
    Row(
        modifier = modifier.graphicsLayer { alpha = dotState.groupAlpha },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (alpha in dotState.dotAlphas) {
            Box(
                Modifier.size(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = alpha)),
            )
        }
    }
}

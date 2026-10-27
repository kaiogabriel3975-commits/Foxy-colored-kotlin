@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.kitsune.color

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/** Toque com vibração leve (se a preferência estiver ligada). */
val LocalTick = staticCompositionLocalOf<() -> Unit> { {} }

/** Blur do wallpaper para os cartões (null = sem blur), se o blur da interface está ligado e se está na horizontal. */
val LocalHaze = staticCompositionLocalOf<HazeState?> { null }
val LocalBlurOn = staticCompositionLocalOf { true }
val LocalLand = staticCompositionLocalOf { false }

/** Cartão sem cor, só com blur (e um contorno fino), para agrupar controles fora do menu. */
@Composable
fun BlurCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val haze = LocalHaze.current
    val shape = RoundedCornerShape(24.dp)
    var m = modifier.fillMaxWidth().clip(shape)
    if (haze != null && LocalBlurOn.current) m = m.hazeEffect(haze) { blurRadius = 20.dp; noiseFactor = 0f }
    Box(m.border(1.dp, Color.White.copy(alpha = 0.10f), shape)) {
        Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) { content() }
    }
}

/** Clique com a física "elástica" do Material Expressive: encolhe ao tocar e volta com mola. */
@Composable
fun Modifier.bounceClick(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val tick = LocalTick.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.9f else 1f, spring(dampingRatio = 0.42f, stiffness = 520f), label = "bounce")
    return this
        .graphicsLayer { scaleX = s; scaleY = s }
        .clickable(interactionSource = src, indication = null, enabled = enabled) { tick(); onClick() }
}

// ---------- ícones desenhados (sem biblioteca de ícones, para o APK ficar leve) ----------
typealias IconPaint = DrawScope.(Color) -> Unit

private fun DrawScope.st() = Stroke(size.minDimension * 0.09f, cap = StrokeCap.Round)

val IcColors: IconPaint = { c ->
    val m = size.minDimension
    drawCircle(c, m * .375f, style = st())
    drawCircle(c, m * .125f, style = st())
}
val IcModes: IconPaint = { c ->
    val w = size.width
    val h = size.height
    val p = Path().apply {
        moveTo(.1f * w, .5f * h)
        cubicTo(.2f * w, .15f * h, .32f * w, .15f * h, .5f * w, .5f * h)
        cubicTo(.68f * w, .85f * h, .8f * w, .85f * h, .9f * w, .5f * h)
    }
    drawPath(p, c, style = st())
}
val IcMic: IconPaint = { c ->
    val w = size.width
    val h = size.height
    drawRoundRect(c, Offset(.375f * w, .08f * h), Size(.25f * w, .46f * h), CornerRadius(.125f * w), style = st())
    drawArc(c, 0f, 180f, false, Offset(.2f * w, .3f * h), Size(.6f * w, .45f * h), style = st())
    drawLine(c, Offset(.5f * w, .75f * h), Offset(.5f * w, .92f * h), size.minDimension * .09f, StrokeCap.Round)
}
val IcMusic: IconPaint = { c ->
    val w = size.width
    val h = size.height
    val sw = size.minDimension * .09f
    drawLine(c, Offset(.4f * w, .75f * h), Offset(.4f * w, .2f * h), sw, StrokeCap.Round)
    drawLine(c, Offset(.4f * w, .2f * h), Offset(.82f * w, .12f * h), sw, StrokeCap.Round)
    drawLine(c, Offset(.82f * w, .12f * h), Offset(.82f * w, .66f * h), sw, StrokeCap.Round)
    drawCircle(c, .11f * w, Offset(.3f * w, .75f * h), style = st())
    drawCircle(c, .11f * w, Offset(.72f * w, .66f * h), style = st())
}
val IcSched: IconPaint = { c ->
    val w = size.width
    val h = size.height
    val sw = size.minDimension * .09f
    drawCircle(c, .38f * w, Offset(.5f * w, .56f * h), style = st())
    drawLine(c, Offset(.5f * w, .56f * h), Offset(.5f * w, .36f * h), sw, StrokeCap.Round)
    drawLine(c, Offset(.5f * w, .56f * h), Offset(.64f * w, .66f * h), sw, StrokeCap.Round)
    drawLine(c, Offset(.38f * w, .06f * h), Offset(.62f * w, .06f * h), sw, StrokeCap.Round)
}
val IcGear: IconPaint = { c ->
    val m = size.minDimension
    drawCircle(c, m * .17f, style = st())
    for (i in 0 until 8) {
        val a = Math.toRadians(i * 45.0)
        val s = Offset(center.x + (cos(a) * m * .3f).toFloat(), center.y + (sin(a) * m * .3f).toFloat())
        val e = Offset(center.x + (cos(a) * m * .43f).toFloat(), center.y + (sin(a) * m * .43f).toFloat())
        drawLine(c, s, e, m * .1f, StrokeCap.Round)
    }
    drawCircle(c, m * .32f, style = st())
}
val IcPower: IconPaint = { c ->
    val w = size.width
    val h = size.height
    drawArc(c, -50f, 280f, false, Offset(.2f * w, .24f * h), Size(.6f * w, .6f * w), style = st())
    drawLine(c, Offset(.5f * w, .08f * h), Offset(.5f * w, .5f * h), size.minDimension * .09f, StrokeCap.Round)
}
val IcPlay: IconPaint = { c ->
    val w = size.width
    val h = size.height
    drawPath(Path().apply { moveTo(.3f * w, .2f * h); lineTo(.8f * w, .5f * h); lineTo(.3f * w, .8f * h); close() }, c)
}
val IcPause: IconPaint = { c ->
    val w = size.width
    val h = size.height
    drawRoundRect(c, Offset(.25f * w, .2f * h), Size(.18f * w, .6f * h), CornerRadius(.04f * w))
    drawRoundRect(c, Offset(.57f * w, .2f * h), Size(.18f * w, .6f * h), CornerRadius(.04f * w))
}
val IcNext: IconPaint = { c ->
    val w = size.width
    val h = size.height
    drawPath(Path().apply { moveTo(.2f * w, .22f * h); lineTo(.66f * w, .5f * h); lineTo(.2f * w, .78f * h); close() }, c)
    drawRoundRect(c, Offset(.72f * w, .22f * h), Size(.1f * w, .56f * h), CornerRadius(.03f * w))
}
val IcPrev: IconPaint = { c ->
    val w = size.width
    val h = size.height
    drawPath(Path().apply { moveTo(.8f * w, .22f * h); lineTo(.34f * w, .5f * h); lineTo(.8f * w, .78f * h); close() }, c)
    drawRoundRect(c, Offset(.18f * w, .22f * h), Size(.1f * w, .56f * h), CornerRadius(.03f * w))
}
// ícones pequenos para as trilhas dos sliders
val IcSunLow: IconPaint = { c -> drawCircle(c, size.minDimension * .2f, style = st()) }
val IcSunHigh: IconPaint = { c ->
    val m = size.minDimension
    drawCircle(c, m * .18f, style = st())
    for (i in 0 until 8) {
        val a = Math.toRadians(i * 45.0)
        drawLine(
            c, Offset(center.x + (cos(a) * m * .3f).toFloat(), center.y + (sin(a) * m * .3f).toFloat()),
            Offset(center.x + (cos(a) * m * .45f).toFloat(), center.y + (sin(a) * m * .45f).toFloat()), m * .08f, StrokeCap.Round,
        )
    }
}
val IcSatLow: IconPaint = { c -> drawCircle(c, size.minDimension * .2f, style = st()) }
val IcSatHigh: IconPaint = { c -> drawCircle(c, size.minDimension * .24f) }
val IcSlow: IconPaint = { c ->
    val w = size.width
    val h = size.height
    drawPath(Path().apply { moveTo(.38f * w, .22f * h); lineTo(.66f * w, .5f * h); lineTo(.38f * w, .78f * h) }, c, style = st())
}
val IcFast: IconPaint = { c ->
    val w = size.width
    val h = size.height
    drawPath(Path().apply { moveTo(.18f * w, .22f * h); lineTo(.46f * w, .5f * h); lineTo(.18f * w, .78f * h) }, c, style = st())
    drawPath(Path().apply { moveTo(.5f * w, .22f * h); lineTo(.78f * w, .5f * h); lineTo(.5f * w, .78f * h) }, c, style = st())
}
val IcBarsLow: IconPaint = { c ->
    val w = size.width
    val h = size.height
    drawLine(c, Offset(.5f * w, .4f * h), Offset(.5f * w, .6f * h), size.minDimension * .12f, StrokeCap.Round)
}
val IcBarsHigh: IconPaint = { c ->
    val w = size.width
    val h = size.height
    val sw = size.minDimension * .1f
    drawLine(c, Offset(.25f * w, .4f * h), Offset(.25f * w, .6f * h), sw, StrokeCap.Round)
    drawLine(c, Offset(.5f * w, .15f * h), Offset(.5f * w, .85f * h), sw, StrokeCap.Round)
    drawLine(c, Offset(.75f * w, .3f * h), Offset(.75f * w, .7f * h), sw, StrokeCap.Round)
}
val IcDotsFew: IconPaint = { c ->
    drawCircle(c, size.minDimension * .09f, Offset(size.width * .35f, size.height * .5f))
    drawCircle(c, size.minDimension * .09f, Offset(size.width * .65f, size.height * .5f))
}
val IcDotsMany: IconPaint = { c ->
    for (i in 0 until 3) for (j in 0 until 2) {
        drawCircle(c, size.minDimension * .08f, Offset(size.width * (.2f + .3f * i), size.height * (.35f + .3f * j)))
    }
}

@Composable
fun Ic(paint: IconPaint, color: Color, size: androidx.compose.ui.unit.Dp = 24.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) { this.paint(color) }
}

class NavItem(val label: String, val icon: IconPaint)

// ---------- slider expressivo (SliderWithTrackIcons) com deslize suave ----------
@Composable
fun ExpressiveSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    startIcon: IconPaint? = null,
    endIcon: IconPaint? = null,
    onFinished: (() -> Unit)? = null,
) {
    val anim = remember { Animatable(value) }
    // o valor mostrado "persegue" o dedo com uma mola: o deslize fica fluido, sem colar no toque
    LaunchedEffect(value) { anim.animateTo(value, spring(dampingRatio = 0.85f, stiffness = 720f)) }
    Slider(
        value = anim.value.coerceIn(range.start, range.endInclusive),
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        onValueChangeFinished = onFinished,
        valueRange = range,
        track = { st ->
            val activeColor = SliderDefaults.colors().activeTickColor
            val inactiveColor = SliderDefaults.colors().inactiveTickColor
            SliderDefaults.Track(
                sliderState = st,
                modifier = Modifier.height(36.dp).drawWithContent {
                    drawContent()
                    val isz = 20.dp.toPx()
                    val pad = 10.dp.toPx()
                    val gap = 6.dp.toPx()
                    val y = size.height / 2 - isz / 2
                    val activeEnd = size.width * st.coercedValueAsFraction - gap
                    fun put(p: IconPaint, x: Float) {
                        val col = if (x + isz / 2 < activeEnd) activeColor else inactiveColor
                        inset(x, y, size.width - x - isz, size.height - y - isz) { p(this, col) }
                    }
                    startIcon?.let { put(it, pad) }
                    endIcon?.let { put(it, size.width - pad - isz) }
                },
                drawStopIndicator = null,
                thumbTrackGapSize = 6.dp,
            )
        },
    )
}

// ---------- roda de cores (a bolinha desliza com mola) ----------
@Composable
fun ColorWheel(
    hue: Int,
    sat: Int,
    enabled: Boolean,
    onChange: (Int, Int) -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var box by remember { mutableStateOf(IntSize.Zero) }
    val colors = remember { (0..6).map { Color.hsv((it * 60f).coerceAtMost(360f), 1f, 1f) } }
    val ball = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var near by remember { mutableStateOf(false) }
    val grow by animateFloatAsState(if (near) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 300f), label = "grow")
    fun finish() { near = false; onEnd() }
    LaunchedEffect(hue, sat, box) {
        if (box.width > 0) {
            val r = box.width / 2f
            val a = Math.toRadians(hue.toDouble())
            val d = sat / 1000f * r
            val t = Offset(r + (cos(a) * d).toFloat(), r + (sin(a) * d).toFloat())
            if (ball.value == Offset.Zero) ball.snapTo(t) else ball.animateTo(t, spring(dampingRatio = 0.7f, stiffness = 170f))
        }
    }
    fun pick(p: Offset) {
        val cx = box.width / 2f
        val cy = box.height / 2f
        val r = box.width / 2f
        if (r <= 0f) return
        val dx = p.x - cx
        val dy = p.y - cy
        var a = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
        if (a < 0) a += 360f
        val d = hypot(dx, dy) / r
        near = d > 0.78f
        onChange(a.toInt(), (min(d, 1f) * 1000).toInt())
    }
    Canvas(
        modifier
            .aspectRatio(1f)
            .graphicsLayer { val sc = 1f + 0.07f * grow; scaleX = sc; scaleY = sc }
            .onSizeChanged { box = it }
            .pointerInput(enabled) {
                if (enabled) {
                    detectDragGestures(
                        onDragStart = { pick(it) },
                        onDragEnd = { finish() },
                        onDragCancel = { finish() },
                    ) { change, _ ->
                        change.consume()
                        pick(change.position)
                    }
                }
            }
            .pointerInput(enabled) {
                if (enabled) detectTapGestures { pick(it); finish() }
            },
    ) {
        val r = size.minDimension / 2f
        drawCircle(brush = Brush.sweepGradient(colors, center), radius = r)
        drawCircle(brush = Brush.radialGradient(listOf(Color.White, Color.Transparent), center = center, radius = r), radius = r)
        val p = ball.value
        val br = 16.dp.toPx() * (1f + 0.35f * grow)
        drawCircle(Color.hsv(hue.toFloat().coerceIn(0f, 360f), (sat / 1000f).coerceIn(0f, 1f), 1f), br, p)
        drawCircle(Color.White, br, p, style = Stroke(3.dp.toPx()))
    }
}

// ---------- seletor de horário em roda (sem diálogos do sistema) ----------
@Composable
fun WheelPicker(count: Int, value: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val item = 44.dp
    val state = rememberLazyListState(initialFirstVisibleItemIndex = value.coerceIn(0, count - 1))
    val fling = rememberSnapFlingBehavior(state)
    val current by rememberUpdatedState(value)
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) {
                val i = centerIndex(state)
                if (i != current) onChange(i)
            }
        }
    }
    LaunchedEffect(value) {
        if (!state.isScrollInProgress && centerIndex(state) != value) state.scrollToItem(value.coerceIn(0, count - 1))
    }
    Box(modifier.height(item * 3).width(76.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxWidth().height(item).clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        )
        LazyColumn(
            state = state,
            flingBehavior = fling,
            contentPadding = PaddingValues(vertical = item),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(count) { i ->
                Box(Modifier.height(item), contentAlignment = Alignment.Center) {
                    Text("%02d".format(i), style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}

private fun centerIndex(s: LazyListState): Int {
    val li = s.layoutInfo
    val c = (li.viewportStartOffset + li.viewportEndOffset) / 2
    return li.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - c) }?.index ?: 0
}

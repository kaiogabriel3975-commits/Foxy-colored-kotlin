package com.kitsune.color

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Toque com vibração leve (se a preferência estiver ligada). */
val LocalTick = staticCompositionLocalOf<() -> Unit> { {} }

@Composable
fun Modifier.ticking(onClick: () -> Unit): Modifier {
    val t = LocalTick.current
    return this.clickable { t(); onClick() }
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

@Composable
fun Ic(paint: IconPaint, color: Color, size: androidx.compose.ui.unit.Dp = 24.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) { this.paint(color) }
}

// ---------- roda de cores ----------
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
    fun pick(p: Offset) {
        val cx = box.width / 2f
        val cy = box.height / 2f
        val r = box.width / 2f
        if (r <= 0f) return
        val dx = p.x - cx
        val dy = p.y - cy
        var a = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
        if (a < 0) a += 360f
        val d = kotlin.math.hypot(dx, dy)
        onChange(a.toInt(), (min(d / r, 1f) * 1000).toInt())
    }
    Canvas(
        modifier
            .aspectRatio(1f)
            .onSizeChanged { box = it }
            .pointerInput(enabled) {
                if (enabled) {
                    detectDragGestures(
                        onDragStart = { pick(it) },
                        onDragEnd = { onEnd() },
                        onDragCancel = { onEnd() },
                    ) { change, _ ->
                        change.consume()
                        pick(change.position)
                    }
                }
            }
            .pointerInput(enabled) {
                if (enabled) detectTapGestures { pick(it); onEnd() }
            },
    ) {
        val r = size.minDimension / 2f
        drawCircle(brush = Brush.sweepGradient(colors, center), radius = r)
        drawCircle(brush = Brush.radialGradient(listOf(Color.White, Color.Transparent), center = center, radius = r), radius = r)
        val ang = Math.toRadians(hue.toDouble())
        val dist = sat / 1000f * r
        val p = Offset(center.x + (cos(ang) * dist).toFloat(), center.y + (sin(ang) * dist).toFloat())
        drawCircle(Color.hsv(hue.toFloat().coerceIn(0f, 360f), (sat / 1000f).coerceIn(0f, 1f), 1f), 16.dp.toPx(), p)
        drawCircle(Color.White, 16.dp.toPx(), p, style = Stroke(3.dp.toPx()))
    }
}

// ---------- seletor de horário em roda (sem diálogos do sistema) ----------
@OptIn(ExperimentalFoundationApi::class)
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

// ---------- barra flutuante em pílula ----------
class NavItem(val label: String, val icon: IconPaint)

@Composable
fun NavPill(items: List<NavItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val tick = LocalTick.current
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
        shadowElevation = 10.dp,
    ) {
        Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            items.forEachIndexed { i, it ->
                val sel = i == selected
                val bg by animateColorAsState(
                    if (sel) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent, label = "navbg",
                )
                val fg = if (sel) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                Column(
                    Modifier.clip(CircleShape).background(bg).clickable { tick(); onSelect(i) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Ic(it.icon, fg, 22.dp)
                    Text(it.label, style = MaterialTheme.typography.labelSmall, color = fg)
                }
            }
        }
    }
}

/** Degradê suave nas bordas (igual no topo e na base). */
@Composable
fun EdgeFade(top: Boolean, height: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val bg = MaterialTheme.colorScheme.background
    val stops = listOf(0.94f, 0.82f, 0.58f, 0.3f, 0.1f, 0f).map { bg.copy(alpha = it) }
    val brush = if (top) Brush.verticalGradient(stops) else Brush.verticalGradient(stops.reversed())
    Box(modifier.fillMaxWidth().height(height).background(brush))
}

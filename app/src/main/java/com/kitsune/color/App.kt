@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.kitsune.color

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp
import androidx.core.content.ContextCompat
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.roundToInt

/** Tudo o que vive enquanto o app está aberto. */
class Container(private val ctx: Context) {
    val prefs = Prefs(ctx)
    val led = LedController(ctx, prefs)
    val mic = MicController(led)
    val music = MusicPlayer(ctx, led, prefs)
    val recents = Recents(ctx)

    private val main = Handler(Looper.getMainLooper())
    var wall by mutableStateOf<ImageBitmap?>(null)
        private set
    /** luminosidade do wallpaper (0 = escuro, 1 = claro); -1 quando é o fundo cinza padrão */
    var wallLuma by mutableFloatStateOf(-1f)
        private set

    init { loadWall() }

    fun stopAudio() {
        music.stop()
        mic.stop()
    }

    fun loadWall() {
        val choice = prefs.wallChoice
        Thread {
            val bmp: Bitmap? = try {
                when (choice) {
                    1 -> decodeRes(R.drawable.wall_light)
                    2 -> decodeRes(R.drawable.wall_dark)
                    3 -> decodeFile(File(ctx.filesDir, "wall.img"))
                    else -> null
                }
            } catch (_: Exception) {
                null
            }
            val luma = bmp?.let { lumaOf(it) } ?: -1f
            main.post {
                wall = bmp?.asImageBitmap()
                wallLuma = luma
            }
        }.start()
    }

    private fun opts(w: Int, h: Int): BitmapFactory.Options {
        var s = 1
        while (w / s > 1600 || h / s > 1600) s *= 2
        return BitmapFactory.Options().apply { inSampleSize = s }
    }

    private fun decodeRes(id: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(ctx.resources, id, o)
        return BitmapFactory.decodeResource(ctx.resources, id, opts(o.outWidth, o.outHeight))
    }

    private fun decodeFile(f: File): Bitmap? {
        if (!f.exists()) return null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        return BitmapFactory.decodeFile(f.path, opts(o.outWidth, o.outHeight))
    }

    private fun lumaOf(b: Bitmap): Float {
        val s = Bitmap.createScaledBitmap(b, 24, 24, true)
        var sum = 0.0
        for (y in 0 until 24) for (x in 0 until 24) {
            val p = s.getPixel(x, y)
            val r = (p shr 16) and 255
            val g = (p shr 8) and 255
            val bl = p and 255
            sum += (0.299 * r + 0.587 * g + 0.114 * bl) / 255.0
        }
        return (sum / (24 * 24)).toFloat()
    }
}

@Composable
fun App(c: Container, dark: Boolean) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val prefs = c.prefs
    val led = c.led
    val tick: () -> Unit = remember { { if (prefs.haptic.value) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) } }

    var screen by remember { mutableIntStateOf(if (prefs.remember.value) prefs.lastScreen else 0) }
    var showList by remember { mutableStateOf(false) }
    var armed by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var gearCenter by remember { mutableStateOf(Offset.Zero) }
    val prog = remember { Animatable(0f) }
    val haze = rememberHazeState()
    var expanded by remember { mutableStateOf(true) }

    // a barra recolhe ao rolar para baixo e reaparece ao rolar para cima
    val scrollWatch = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -14f) expanded = false else if (available.y > 14f) expanded = true
                return Offset.Zero
            }
        }
    }

    // ----- permissões de Bluetooth -----
    val perms = remember { arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT) }
    fun granted() = perms.all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.all { it }) led.startScan() else led.say("Permita o acesso a dispositivos próximos para conectar.")
    }
    LaunchedEffect(Unit) {
        if (prefs.scanOnStart.value) {
            delay(700)
            if (granted()) led.startScan() else launcher.launch(perms)
        }
    }
    LaunchedEffect(led.link) {
        if (led.link == LedController.Link.ON || led.link == LedController.Link.CONNECTING) showList = false
    }
    LaunchedEffect(armed) {
        if (armed) {
            delay(3000)
            armed = false
        }
    }
    LaunchedEffect(menuOpen) {
        prog.animateTo(if (menuOpen) 1f else 0f, spring(dampingRatio = 0.8f, stiffness = 260f))
    }
    LaunchedEffect(prefs.wallChoice, prefs.wallVersion) { c.loadWall() }
    BackHandler(menuOpen) { menuOpen = false }

    // ----- telas visíveis na barra -----
    val all = listOf(
        Triple(0, NavItem("Cores", IcColors), true),
        Triple(1, NavItem("Modos", IcModes), prefs.tabModes.value),
        Triple(2, NavItem("Mic", IcMic), prefs.tabMic.value),
        Triple(3, NavItem("Música", IcMusic), prefs.tabMusic.value),
        Triple(4, NavItem("Agenda", IcSched), prefs.tabSched.value),
    )
    val visible = all.filter { it.third }
    LaunchedEffect(visible.size, screen) {
        if (visible.none { it.first == screen }) screen = 0
    }
    val half = (visible.size + 1) / 2
    val leading = visible.take(half)
    val trailing = visible.drop(half)

    fun go(n: Int) {
        if (n == screen) return
        if (prefs.stopOnLeave.value) c.stopAudio()
        screen = n
        expanded = true
        if (prefs.remember.value) prefs.changeLastScreen(n)
    }

    // ----- tela ligada com som -----
    val keepOn = prefs.wake.value && (c.music.playing || (c.mic.on && c.mic.source == 1))
    DisposableEffect(keepOn) {
        view.keepScreenOn = keepOn
        onDispose { view.keepScreenOn = false }
    }

    val wallLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            Thread {
                try {
                    ctx.contentResolver.openInputStream(uri)?.use { i -> File(ctx.filesDir, "wall.img").outputStream().use { o -> i.copyTo(o) } }
                    view.post { prefs.changeWallChoice(3); prefs.wallChanged() }
                } catch (_: Exception) {
                }
            }.start()
        }
    }

    val on = led.link == LedController.Link.ON
    val status = when (led.link) {
        LedController.Link.OFF -> "Desconectado"
        LedController.Link.SCANNING -> "Buscando aparelhos…"
        LedController.Link.CONNECTING -> "Conectando…"
        LedController.Link.ON -> "Conectado · ${led.deviceName}"
    }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val botInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val headerH = topInset + 76.dp
    val pad = PaddingValues(top = headerH + 4.dp, bottom = botInset + 116.dp)
    val scheme = MaterialTheme.colorScheme
    val blurOn = prefs.blur.value
    val grayBg = if (dark) Color(0xFF2B2B2F) else Color(0xFFD8D8DC)

    CompositionLocalProvider(LocalTick provides tick) {
        Box(Modifier.fillMaxSize().nestedScroll(scrollWatch)) {
            // ---- fonte do blur: wallpaper + telas ----
            Box(Modifier.fillMaxSize().hazeSource(haze)) {
                val blurDp = when (prefs.wallBlur) { 0 -> 0.dp; 1 -> 14.dp; else -> 28.dp }
                var wm = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.12f; scaleY = 1.12f }
                if (blurDp > 0.dp && c.wall != null) wm = wm.blur(blurDp)
                val w = c.wall
                if (w != null) {
                    Image(bitmap = w, contentDescription = null, contentScale = ContentScale.Crop, modifier = wm)
                    Box(Modifier.fillMaxSize().background((if (dark) Color.Black else Color.White).copy(alpha = 0.28f)))
                } else {
                    Box(Modifier.fillMaxSize().background(grayBg))
                }
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = {
                        if (prefs.reduce.value) EnterTransition.None togetherWith ExitTransition.None
                        else fadeIn(tween(220)) togetherWith fadeOut(tween(120))
                    },
                    label = "screen",
                ) { s ->
                    when (s) {
                        1 -> ModesScreen(c, pad)
                        2 -> MicScreen(c, pad)
                        3 -> MusicScreen(c, pad)
                        4 -> ScheduleScreen(c, pad)
                        else -> ColorsScreen(c, pad)
                    }
                }
            }

            // ---- blur em degradê só no cabeçalho (nada na barra de navegação) ----
            if (blurOn) {
                Box(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth().height(headerH + 28.dp).hazeEffect(haze) {
                        blurRadius = 22.dp
                        noiseFactor = 0f
                        progressive = HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 0f)
                    },
                )
            }

            // ---- cabeçalho ----
            Row(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(top = topInset).height(76.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(painterResource(R.drawable.logo), contentDescription = null, modifier = Modifier.size(40.dp))
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("Kitsune color", style = MaterialTheme.typography.titleLarge, color = scheme.onBackground)
                    Text(status, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1)
                }
                FilledTonalButton(
                    onClick = {
                        tick()
                        if (on) {
                            if (prefs.confirmOff.value && !armed) {
                                armed = true
                                led.say("Toque novamente para desconectar.")
                            } else {
                                armed = false
                                led.say("")
                                led.disconnect()
                            }
                        } else {
                            showList = true
                            if (granted()) led.startScan() else launcher.launch(perms)
                        }
                    },
                    shapes = ButtonDefaults.shapes(),
                ) { Text(if (on) "Desconectar" else "Conectar") }
                Box(
                    Modifier.padding(start = 8.dp).size(44.dp)
                        .onGloballyPositioned { g ->
                            val p = g.positionInRoot()
                            gearCenter = Offset(p.x + g.size.width / 2f, p.y + g.size.height / 2f)
                        }
                        .graphicsLayer { alpha = if (prog.value > 0.001f) 0f else 1f }
                        .bounceClick { menuOpen = true }
                        .clip(CircleShape).background(scheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) { Ic(IcGear, scheme.onSurface, 26.dp) }
            }

            if (led.message.isNotEmpty()) {
                Text(
                    led.message, color = scheme.error, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = topInset + 70.dp, start = 24.dp, end = 24.dp),
                )
            }

            // ---- barra flutuante expansível (botão central = ligar/desligar) ----
            val src = remember { MutableInteractionSource() }
            val pressed by src.collectIsPressedAsState()
            val pwrScale by androidx.compose.animation.core.animateFloatAsState(
                if (pressed) 0.86f else 1f, spring(dampingRatio = 0.4f, stiffness = 520f), label = "pwr",
            )
            HorizontalFloatingToolbar(
                expanded = expanded,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = botInset + 16.dp)
                    .then(
                        if (blurOn) Modifier.clip(CircleShape).hazeEffect(haze) { blurRadius = 24.dp; noiseFactor = 0f } else Modifier,
                    ),
                colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
                    toolbarContainerColor = scheme.surfaceContainerHigh.copy(alpha = if (blurOn) 0.55f else 0.96f),
                ),
                leadingContent = if (leading.isNotEmpty()) {
                    { leading.forEach { NavBtn(it.second, it.first == screen) { go(it.first) } } }
                } else null,
                trailingContent = if (trailing.isNotEmpty()) {
                    { trailing.forEach { NavBtn(it.second, it.first == screen) { go(it.first) } } }
                } else null,
                content = {
                    FilledIconButton(
                        onClick = { tick(); led.changePower(!led.power) },
                        enabled = on,
                        interactionSource = src,
                        modifier = Modifier.width(64.dp).graphicsLayer { scaleX = pwrScale; scaleY = pwrScale },
                        colors = if (led.power) IconButtonDefaults.filledIconButtonColors()
                        else IconButtonDefaults.filledIconButtonColors(
                            containerColor = scheme.surfaceVariant, contentColor = scheme.onSurfaceVariant,
                        ),
                    ) { Ic(IcPower, if (led.power) scheme.onPrimary else scheme.onSurfaceVariant, 24.dp) }
                },
            )

            // ---- menu: o círculo da engrenagem se expande até um quadrado no centro (e volta) ----
            if (prog.value > 0.001f || menuOpen) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val p = prog.value
                    val sw = constraints.maxWidth.toFloat()
                    val sh = constraints.maxHeight.toFloat()
                    val gearPx = with(density) { 44.dp.toPx() }
                    val sideDp = minOf(maxWidth - 32.dp, maxHeight - 150.dp, 420.dp)
                    val sidePx = with(density) { sideDp.toPx() }
                    val sizePx = lerp(gearPx, sidePx, p)
                    val cx = lerp(gearCenter.x, sw / 2f, p)
                    val cy = lerp(gearCenter.y, sh / 2f, p)
                    Box(
                        Modifier.fillMaxSize().background(Color.Black.copy(alpha = (0.5f * p).coerceIn(0f, 0.5f)))
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { menuOpen = false },
                    )
                    Box(
                        Modifier.offset { IntOffset((cx - sizePx / 2f).roundToInt(), (cy - sizePx / 2f).roundToInt()) }
                            .size(with(density) { sizePx.toDp() })
                            .clip(RoundedCornerShape(lerp(22.dp, 32.dp, p.coerceIn(0f, 1f))))
                            .background(scheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (p < 0.5f) {
                            Ic(IcGear, scheme.onSurface, 26.dp, Modifier.graphicsLayer { alpha = (1f - p * 2f).coerceIn(0f, 1f) })
                        }
                        Box(
                            Modifier.requiredSize(sideDp).graphicsLayer { alpha = ((p - 0.5f) / 0.5f).coerceIn(0f, 1f) },
                        ) {
                            SettingsContent(c, onClose = { menuOpen = false }, onPickWall = {
                                wallLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            })
                        }
                    }
                }
            }
        }

        if (showList) {
            ConnectDialog(
                led = led, onDismiss = { showList = false; led.stopScan() },
                onScan = { if (granted()) led.startScan() else launcher.launch(perms) },
            )
        }
    }
}

@Composable
private fun NavBtn(item: NavItem, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (selected) scheme.secondaryContainer else Color.Transparent, label = "nav")
    val fg = if (selected) scheme.onSecondaryContainer else scheme.onSurface
    Box(
        Modifier.bounceClick { onClick() }.size(48.dp).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center,
    ) { Ic(item.icon, fg, 24.dp) }
}

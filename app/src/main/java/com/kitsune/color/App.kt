@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.kitsune.color

import android.Manifest
import android.content.res.Configuration
import android.os.Build
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.VerticalFloatingToolbar
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import dev.chrisbanes.haze.HazeState
import kotlin.math.max
import kotlin.math.min
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
import androidx.compose.runtime.mutableLongStateOf
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
    val land = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val prefs = c.prefs
    val led = c.led
    val scheme = MaterialTheme.colorScheme
    val tick: () -> Unit = remember { { if (prefs.haptic.value) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) } }

    var screen by remember { mutableIntStateOf(if (prefs.remember.value) prefs.lastScreen else 0) }
    var armed by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var connOpen by remember { mutableStateOf(false) }
    var gearRect by remember { mutableStateOf(Rect.Zero) }
    var connRect by remember { mutableStateOf(Rect.Zero) }
    var barVisible by remember { mutableStateOf(true) }
    val menuProg = remember { Animatable(0f) }
    val connProg = remember { Animatable(0f) }
    val hazeAll = rememberHazeState()
    val hazeWall = rememberHazeState()
    val anyMenu by rememberUpdatedState(menuOpen || connOpen)

    // a barra some "para trás" ao rolar para baixo e volta ao rolar para cima (ignora a rolagem dos menus)
    val scrollWatch = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!anyMenu) {
                    if (available.y < -14f) barVisible = false else if (available.y > 14f) barVisible = true
                }
                return Offset.Zero
            }
        }
    }

    // ----- permissões -----
    val perms = remember { arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT) }
    fun granted() = perms.all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.all { it }) led.startScan() else led.say("Permita o acesso a dispositivos próximos para conectar.")
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (prefs.scanOnStart.value) {
            delay(700)
            if (granted()) led.startScan() else launcher.launch(perms)
        }
    }
    LaunchedEffect(prefs.notif.value) {
        if (prefs.notif.value && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    // notificação e widget só são atualizados quando algo muda, e com atraso (economiza bateria)
    val favKey = c.recents.list.joinToString { "${it.h}${it.pinned}" }
    LaunchedEffect(prefs.notif.value, led.link, led.power, led.bright, favKey) {
        delay(1500)
        Surfaces.refresh(ctx.applicationContext)
    }
    // Live Update / Now Bar: só existe enquanto há uma sessão ativa (música, microfone ou, se pedido, conexão)
    var liveSince by remember { mutableLongStateOf(0L) }
    val liveTrack = c.music.tracks.getOrNull(c.music.index)
    val liveKind = when {
        c.music.playing -> 1
        c.mic.on -> 2
        led.link == LedController.Link.ON && prefs.liveAlways.value -> 3
        else -> 0
    }
    LaunchedEffect(liveKind) { liveSince = if (liveKind == 0) 0L else System.currentTimeMillis() }
    LaunchedEffect(prefs.liveUpdate.value, liveKind, liveTrack?.title, led.power, led.bright, led.deviceName, c.mic.source) {
        delay(900)
        val kind = if (prefs.liveUpdate.value) liveKind else 0
        val title = when (kind) {
            1 -> liveTrack?.title ?: "Música"
            2 -> "Microfone sincronizado"
            else -> "Conectado · ${led.deviceName}"
        }
        val text = when (kind) {
            1 -> "Luzes sincronizadas com a música · ${led.deviceName}"
            2 -> if (c.mic.source == 0) "Microfone do aparelho" else "Microfone do celular"
            else -> "Luz ${if (led.power) "ligada" else "desligada"} · brilho ${led.bright / 10}%"
        }
        Surfaces.live(ctx.applicationContext, kind, title, text, if (liveSince == 0L) System.currentTimeMillis() else liveSince)
    }
    LaunchedEffect(led.link) {
        if (led.link == LedController.Link.ON || led.link == LedController.Link.CONNECTING) connOpen = false
    }
    LaunchedEffect(armed) {
        if (armed) {
            delay(3000)
            armed = false
        }
    }
    // abrir e fechar: molas lentas, que aceleram aos poucos
    LaunchedEffect(menuOpen) { menuProg.animateTo(if (menuOpen) 1f else 0f, spring(dampingRatio = 0.88f, stiffness = 130f)) }
    LaunchedEffect(connOpen) { connProg.animateTo(if (connOpen) 1f else 0f, spring(dampingRatio = 0.88f, stiffness = 130f)) }
    LaunchedEffect(prefs.wallChoice, prefs.wallVersion) { c.loadWall() }
    BackHandler(menuOpen || connOpen) {
        if (menuOpen) menuOpen = false else {
            connOpen = false
            led.stopScan()
        }
    }

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
    fun go(n: Int) {
        if (n == screen) return
        if (prefs.stopOnLeave.value) c.stopAudio()
        screen = n
        barVisible = true
        if (prefs.remember.value) prefs.changeLastScreen(n)
    }

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
    val headerH = topInset + (if (land) 60.dp else 76.dp)
    val pad = PaddingValues(top = headerH + 4.dp, bottom = botInset + (if (land) 24.dp else 116.dp))
    val blurOn = prefs.blur.value
    val grayBg = if (dark) Color(0xFF121212) else Color(0xFFD8D8DC)
    val frameMod = if (land) Modifier.fillMaxHeight().aspectRatio(0.75f) else Modifier.fillMaxSize()
    val connTarget = (160 + 66 * min(max(led.found.size, 1), 8)).dp
    val connH by animateDpAsState(connTarget, spring(dampingRatio = 0.85f, stiffness = 200f), label = "connH")

    CompositionLocalProvider(
        LocalTick provides tick, LocalHaze provides hazeWall, LocalBlurOn provides blurOn,
        LocalLand provides land, LocalContentColor provides scheme.onBackground,
    ) {
        Box(Modifier.fillMaxSize().nestedScroll(scrollWatch)) {
            // ---- fonte do blur: wallpaper (só ele, para os cartões) e wallpaper + telas (para cabeçalho/barra) ----
            Box(Modifier.fillMaxSize().hazeSource(hazeAll)) {
                Box(Modifier.fillMaxSize().hazeSource(hazeWall)) {
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
                }
                Box(Modifier.align(Alignment.Center).then(frameMod)) {
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
            }

            // ---- cabeçalho (blur em degradê; sem blur, degradê preto) ----
            Box(Modifier.align(Alignment.Center).then(frameMod)) {
                if (blurOn) {
                    Box(
                        Modifier.align(Alignment.TopCenter).fillMaxWidth().height(headerH + 28.dp).hazeEffect(hazeAll) {
                            blurRadius = 22.dp
                            noiseFactor = 0f
                            progressive = HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 0f)
                        },
                    )
                } else {
                    Box(
                        Modifier.align(Alignment.TopCenter).fillMaxWidth().height(headerH + 28.dp).background(
                            Brush.verticalGradient(listOf((if (dark) Color.Black else Color.White).copy(alpha = 0.6f), Color.Transparent)),
                        ),
                    )
                }
                Row(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(top = topInset).height(if (land) 60.dp else 76.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(painterResource(R.drawable.logo), contentDescription = null, modifier = Modifier.size(if (land) 32.dp else 40.dp))
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("Kitsune color", style = MaterialTheme.typography.titleLarge, color = scheme.onBackground)
                        Text(status, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1)
                    }
                    Box(
                        Modifier.onGloballyPositioned { connRect = it.boundsInRoot() }
                            .graphicsLayer { alpha = if (connProg.value > 0.001f) 0f else 1f },
                    ) {
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
                                    connOpen = true
                                    if (granted()) led.startScan() else launcher.launch(perms)
                                }
                            },
                            shapes = ButtonDefaults.shapes(),
                        ) { Text(if (on) "Desconectar" else "Conectar") }
                    }
                    Box(
                        Modifier.padding(start = 8.dp).size(44.dp)
                            .onGloballyPositioned { gearRect = it.boundsInRoot() }
                            .graphicsLayer { alpha = if (menuProg.value > 0.001f) 0f else 1f }
                            .bounceClick { menuOpen = true }
                            .clip(CircleShape).background(scheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center,
                    ) { Ic(IcGear, scheme.onSurface, 26.dp) }
                }
                if (led.message.isNotEmpty()) {
                    Text(
                        led.message, color = scheme.error, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = topInset + (if (land) 54.dp else 70.dp), start = 24.dp, end = 24.dp),
                    )
                }
            }

            // ---- barra flutuante (horizontal; lateral na horizontal do celular) ----
            NavToolbar(
                land = land, items = visible, screen = screen, powerBtn = prefs.powerBtn.value, led = led, on = on,
                haze = hazeAll, blurOn = blurOn, visible = barVisible, tick = tick, onGo = { go(it) },
                botInset = botInset,
            )

            // ---- menus: o botão/engrenagem se expande até o centro (e volta) ----
            MorphMenu(
                prog = menuProg, open = menuOpen, origin = gearRect, originCorner = 22.dp, blurOn = blurOn, haze = hazeAll,
                onDismiss = { menuOpen = false },
                finalSize = { mw, mh ->
                    if (!land) {
                        val w = minOf(mw - 32.dp, (mh - 120.dp) * 3f / 4f, 420.dp)
                        w to w * 4f / 3f
                    } else {
                        val h = minOf(mh - 32.dp, (mw - 120.dp) * 3f / 4f, 420.dp)
                        h * 4f / 3f to h
                    }
                },
            ) {
                SettingsContent(c, onClose = { menuOpen = false }, onPickWall = {
                    wallLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                })
            }
            MorphMenu(
                prog = connProg, open = connOpen, origin = connRect,
                originCorner = with(density) { (connRect.height / 2f).toDp() }, blurOn = blurOn, haze = hazeAll,
                onDismiss = { connOpen = false; led.stopScan() },
                finalSize = { mw, mh ->
                    val w = minOf(mw - 48.dp, 360.dp)
                    val h = if (land) minOf(connH, mh - 32.dp) else minOf(connH, mh - 100.dp)
                    w to h
                },
            ) {
                ConnectContent(
                    led = led,
                    onScan = { if (granted()) led.startScan() else launcher.launch(perms) },
                    onClose = { connOpen = false; led.stopScan() },
                )
            }
        }
    }
}

/** O botão/engrenagem vira um menu no centro da tela com uma mola lenta (a animação funciona ao contrário também). */
@Composable
private fun MorphMenu(
    prog: Animatable<Float, AnimationVector1D>,
    open: Boolean,
    origin: Rect,
    originCorner: Dp,
    blurOn: Boolean,
    haze: HazeState,
    onDismiss: () -> Unit,
    finalSize: (Dp, Dp) -> Pair<Dp, Dp>,
    content: @Composable () -> Unit,
) {
    val p = prog.value
    if (p <= 0.001f && !open) return
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val (fw, fh) = finalSize(maxWidth, maxHeight)
        val fwPx = with(density) { fw.toPx() }
        val fhPx = with(density) { fh.toPx() }
        val sw = constraints.maxWidth.toFloat()
        val sh = constraints.maxHeight.toFloat()
        val q = p.coerceIn(0f, 1f)
        val w = lerp(origin.width, fwPx, p)
        val h = lerp(origin.height, fhPx, p)
        val cx = lerp(origin.center.x, sw / 2f, p)
        val cy = lerp(origin.center.y, sh / 2f, p)
        // fundo desfocado: some a rolagem e o toque nas telas de trás
        Box(
            Modifier.fillMaxSize().graphicsLayer { alpha = q }
                .then(if (blurOn) Modifier.hazeEffect(haze) { blurRadius = 22.dp; noiseFactor = 0f } else Modifier)
                .background(Color.Black.copy(alpha = 0.32f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() },
        )
        Box(
            Modifier.offset { IntOffset((cx - w / 2f).roundToInt(), (cy - h / 2f).roundToInt()) }
                .size(with(density) { w.toDp() }, with(density) { h.toDp() })
                .clip(RoundedCornerShape(lerp(originCorner, 32.dp, q)))
                .background(scheme.surfaceContainerHigh.copy(alpha = lerp(1f, 0.5f, q))),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.requiredSize(fw, fh).graphicsLayer { alpha = ((p - 0.5f) / 0.5f).coerceIn(0f, 1f) }) { content() }
        }
    }
}

@Composable
private fun NavBtn(item: NavItem, selected: Boolean, big: Boolean = false, onPos: (Offset) -> Unit, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val fg = if (selected) scheme.onSecondaryContainer else scheme.onSurface
    Box(
        Modifier.bounceClick { onClick() }.size(if (big) 56.dp else 48.dp)
            .onGloballyPositioned { onPos(it.boundsInRoot().center) },
        contentAlignment = Alignment.Center,
    ) { Ic(item.icon, fg, 24.dp) }
}

@Composable
private fun PowerBtn(led: LedController, on: Boolean, tick: () -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.86f else 1f, spring(dampingRatio = 0.4f, stiffness = 520f), label = "pwr")
    FilledIconButton(
        onClick = { tick(); led.changePower(!led.power) },
        enabled = on,
        interactionSource = src,
        modifier = modifier.graphicsLayer { scaleX = s; scaleY = s },
        colors = if (led.power) IconButtonDefaults.filledIconButtonColors()
        else IconButtonDefaults.filledIconButtonColors(containerColor = scheme.surfaceVariant, contentColor = scheme.onSurfaceVariant),
    ) { Ic(IcPower, if (led.power) scheme.onPrimary else scheme.onSurfaceVariant, 24.dp) }
}

/** Barra flutuante com fundo próprio (blur) e uma bolinha que desliza com mola até a tela atual. */
@Composable
private fun NavToolbar(
    land: Boolean,
    items: List<Triple<Int, NavItem, Boolean>>,
    screen: Int,
    powerBtn: Boolean,
    led: LedController,
    on: Boolean,
    haze: HazeState,
    blurOn: Boolean,
    visible: Boolean,
    tick: () -> Unit,
    onGo: (Int) -> Unit,
    botInset: Dp,
) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val pos = remember { mutableStateMapOf<Int, Offset>() }
    // "some para trás": encolhe e desaparece com uma mola
    val vis by animateFloatAsState(if (visible) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 300f), label = "navvis")
    val visNow by rememberUpdatedState(vis)
    val ind = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val target = pos[screen]
    LaunchedEffect(target) {
        if (target != null) {
            if (ind.value == Offset.Zero) ind.snapTo(target) else ind.animateTo(target, spring(dampingRatio = 0.5f, stiffness = 380f))
        }
    }
    if (vis <= 0.01f) return

    val centerIdx = if (powerBtn) -1 else items.size / 2
    val half = (items.size + 1) / 2
    val leadItems = if (powerBtn) items.take(half) else items.take(centerIdx)
    val trailItems = if (powerBtn) items.drop(half) else items.drop(centerIdx + 1)
    val onPos: (Int, Offset) -> Unit = { id, p -> if (visNow > 0.99f) pos[id] = p }

    Box(
        Modifier.fillMaxSize().graphicsLayer {
            val sc = 0.55f + 0.45f * vis
            scaleX = sc
            scaleY = sc
            alpha = vis.coerceIn(0f, 1f)
            if (size.width > 0f && size.height > 0f && bounds != Rect.Zero) {
                transformOrigin = TransformOrigin(bounds.center.x / size.width, bounds.center.y / size.height)
            }
        },
    ) {
        if (bounds != Rect.Zero) {
            Box(
                Modifier.offset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
                    .size(with(density) { bounds.width.toDp() }, with(density) { bounds.height.toDp() })
                    .clip(CircleShape)
                    .then(if (blurOn) Modifier.hazeEffect(haze) { blurRadius = 24.dp; noiseFactor = 0f } else Modifier)
                    .background(scheme.surfaceContainerHigh.copy(alpha = if (blurOn) 0.55f else 0.96f)),
            )
        }
        if (ind.value != Offset.Zero) {
            val r = with(density) { 24.dp.toPx() }
            Box(
                Modifier.offset { IntOffset((ind.value.x - r).roundToInt(), (ind.value.y - r).roundToInt()) }
                    .size(48.dp).clip(CircleShape).background(scheme.secondaryContainer),
            )
        }
        val colors = FloatingToolbarDefaults.standardFloatingToolbarColors(toolbarContainerColor = Color.Transparent)
        if (!land) {
            val leadC: (@Composable RowScope.() -> Unit)? = if (leadItems.isNotEmpty()) {
                { leadItems.forEach { t -> NavBtn(t.second, t.first == screen, onPos = { p -> onPos(t.first, p) }) { onGo(t.first) } } }
            } else null
            val trailC: (@Composable RowScope.() -> Unit)? = if (trailItems.isNotEmpty()) {
                { trailItems.forEach { t -> NavBtn(t.second, t.first == screen, onPos = { p -> onPos(t.first, p) }) { onGo(t.first) } } }
            } else null
            HorizontalFloatingToolbar(
                expanded = true,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = botInset + 16.dp)
                    .onGloballyPositioned { if (visNow > 0.99f) bounds = it.boundsInRoot() },
                colors = colors,
                expandedShadowElevation = 0.dp,
                collapsedShadowElevation = 0.dp,
                leadingContent = leadC,
                trailingContent = trailC,
                content = {
                    if (powerBtn) PowerBtn(led, on, tick, Modifier.width(64.dp))
                    else items.getOrNull(centerIdx)?.let { t -> NavBtn(t.second, t.first == screen, onPos = { p -> onPos(t.first, p) }) { onGo(t.first) } }
                },
            )
        } else {
            val leadC: (@Composable ColumnScope.() -> Unit)? = if (leadItems.isNotEmpty()) {
                { leadItems.forEach { t -> NavBtn(t.second, t.first == screen, onPos = { p -> onPos(t.first, p) }) { onGo(t.first) } } }
            } else null
            val trailC: (@Composable ColumnScope.() -> Unit)? = if (trailItems.isNotEmpty()) {
                { trailItems.forEach { t -> NavBtn(t.second, t.first == screen, onPos = { p -> onPos(t.first, p) }) { onGo(t.first) } } }
            } else null
            VerticalFloatingToolbar(
                expanded = true,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp)
                    .onGloballyPositioned { if (visNow > 0.99f) bounds = it.boundsInRoot() },
                colors = colors,
                expandedShadowElevation = 0.dp,
                collapsedShadowElevation = 0.dp,
                leadingContent = leadC,
                trailingContent = trailC,
                content = {
                    if (powerBtn) PowerBtn(led, on, tick, Modifier.height(64.dp))
                    else items.getOrNull(centerIdx)?.let { t -> NavBtn(t.second, t.first == screen, onPos = { p -> onPos(t.first, p) }) { onGo(t.first) } }
                },
            )
        }
    }
}

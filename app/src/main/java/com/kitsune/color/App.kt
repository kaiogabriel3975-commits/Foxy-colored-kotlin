package com.kitsune.color

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/** Tudo o que vive enquanto o app está aberto. */
class Container(ctx: Context) {
    val prefs = Prefs(ctx)
    val led = LedController(ctx, prefs)
    val mic = MicController(led)
    val music = MusicPlayer(ctx, led, prefs)
    val recents = Recents(ctx)

    fun stopAudio() {
        music.stop()
        mic.stop()
    }
}

private fun loadWall(ctx: Context): ImageBitmap? {
    val f = File(ctx.filesDir, "wall.img")
    if (!f.exists()) return null
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(f.path, o)
    var s = 1
    while (o.outWidth / s > 1600 || o.outHeight / s > 1600) s *= 2
    val b = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s }) ?: return null
    return b.asImageBitmap()
}

@Composable
fun App(c: Container, dark: Boolean) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val prefs = c.prefs
    val led = c.led
    val tick: () -> Unit = remember { { if (prefs.haptic.value) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) } }

    var screen by remember { mutableIntStateOf(if (prefs.remember.value) prefs.lastScreen else 0) }
    var showList by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var armed by remember { mutableStateOf(false) }

    // ----- permissões de Bluetooth -----
    val perms = remember { arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT) }
    fun granted() = perms.all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.all { it }) led.startScan() else led.say("Permita o acesso a dispositivos próximos para conectar.")
    }
    LaunchedEffect(Unit) {
        if (prefs.scanOnStart.value) {
            delay(700) // a busca começa depois que a tela já apareceu
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
        if (prefs.remember.value) prefs.setLastScreen(n)
    }

    // ----- tela ligada com som -----
    val keepOn = prefs.wake.value && (c.music.playing || (c.mic.on && c.mic.source == 1))
    DisposableEffect(keepOn) {
        view.keepScreenOn = keepOn
        onDispose { view.keepScreenOn = false }
    }

    // ----- wallpaper -----
    val wall by produceState<ImageBitmap?>(null, prefs.wallVersion) { value = withContext(Dispatchers.IO) { loadWall(ctx) } }
    val wallLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            Thread {
                try {
                    ctx.contentResolver.openInputStream(uri)?.use { i -> File(ctx.filesDir, "wall.img").outputStream().use { o -> i.copyTo(o) } }
                    view.post { prefs.wallChanged() }
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
    val navVisible = visible.size > 1
    val pad = PaddingValues(top = headerH + 4.dp, bottom = botInset + (if (navVisible) 112.dp else 24.dp))
    val bg = MaterialTheme.colorScheme.background

    CompositionLocalProvider(LocalTick provides tick) {
        Box(Modifier.fillMaxSize().background(bg)) {
            // fundo
            val blurDp = when (prefs.wallBlur) { 0 -> 0.dp; 1 -> 14.dp; else -> 28.dp }
            var wm = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.12f; scaleY = 1.12f }
            if (blurDp > 0.dp) wm = wm.blur(blurDp)
            val w = wall
            if (w != null) {
                Image(bitmap = w, contentDescription = null, contentScale = ContentScale.Crop, modifier = wm)
            } else {
                Image(
                    painter = painterResource(if (dark) R.drawable.wall_dark else R.drawable.wall_light),
                    contentDescription = null, contentScale = ContentScale.Crop, modifier = wm,
                )
            }
            Box(Modifier.fillMaxSize().background(bg.copy(alpha = if (dark) 0.5f else 0.4f)))

            // conteúdo
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

            // bordas em degradê (iguais em cima e embaixo)
            EdgeFade(top = true, height = headerH + 28.dp, modifier = Modifier.align(Alignment.TopCenter))
            EdgeFade(top = false, height = botInset + 132.dp, modifier = Modifier.align(Alignment.BottomCenter))

            // cabeçalho
            Row(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(top = topInset).height(76.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(painterResource(R.drawable.logo), contentDescription = null, modifier = Modifier.size(40.dp))
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("Kitsune color", style = MaterialTheme.typography.titleLarge)
                    Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                FilledTonalButton(onClick = {
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
                }) { Text(if (on) "Desconectar" else "Conectar") }
                Box(
                    Modifier.padding(start = 6.dp).size(44.dp).clip(CircleShape).clickable { tick(); showSettings = true },
                    contentAlignment = Alignment.Center,
                ) { Ic(IcGear, MaterialTheme.colorScheme.onSurface, 26.dp) }
            }

            if (led.message.isNotEmpty()) {
                Text(
                    led.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = topInset + 70.dp, start = 24.dp, end = 24.dp),
                )
            }

            // barra flutuante
            if (navVisible) {
                NavPill(
                    items = visible.map { it.second },
                    selected = visible.indexOfFirst { it.first == screen }.coerceAtLeast(0),
                    onSelect = { go(visible[it].first) },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = botInset + 16.dp),
                )
            }
        }

        if (showList) {
            ConnectDialog(
                led = led, onDismiss = { showList = false; led.stopScan() },
                onScan = { if (granted()) led.startScan() else launcher.launch(perms) },
            )
        }
        if (showSettings) {
            SettingsDialog(
                c = c, onDismiss = { showSettings = false },
                onPickWall = { wallLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            )
        }
    }
}

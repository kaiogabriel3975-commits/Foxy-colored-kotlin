@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)

package com.kitsune.color

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay

private val DAYS = listOf("seg", "ter", "qua", "qui", "sex", "sáb", "dom")

@Composable
private fun Title(t: String) =
    Text(t, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))

@Composable
private fun SwitchRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled) { onChange(!checked) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = { onChange(it) }, enabled = enabled)
    }
}

@Composable
private fun isDark(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

private fun granted(ctx: android.content.Context, p: String) =
    ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

// ---------------------------------------------------------------- Cores
@Composable
fun ColorsScreen(c: Container, pad: PaddingValues) {
    val led = c.led
    val on = led.link == LedController.Link.ON
    val land = LocalLand.current
    val tick = LocalTick.current
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(pad).padding(horizontal = if (land) 14.dp else 20.dp),
        verticalArrangement = Arrangement.spacedBy(if (land) 8.dp else 14.dp),
    ) {
        BlurCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (led.power) "Ligado" else "Desligado", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(checked = led.power, onCheckedChange = { led.changePower(it) }, enabled = on)
            }
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            ColorWheel(
                hue = led.hue, sat = led.sat, enabled = on,
                onChange = { h, s -> led.setColor(h, s) },
                onEnd = { c.recents.remember(led.hue, led.sat) },
                modifier = Modifier.widthIn(max = if (land) 200.dp else 340.dp).fillMaxWidth(),
            )
        }
        BlurCard {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Brilho", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text("${led.bright / 10}%", style = MaterialTheme.typography.labelLarge)
                }
                ExpressiveSlider(
                    led.bright.toFloat(), { led.changeBright(it.toInt()) }, 0f..1000f,
                    enabled = on, startIcon = IcSunLow, endIcon = IcSunHigh,
                )
                if (c.prefs.showSat.value) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                        Text("Saturação", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text("${led.sat / 10}%", style = MaterialTheme.typography.labelLarge)
                    }
                    ExpressiveSlider(
                        led.sat.toFloat(), { led.setColor(led.hue, it.toInt()) }, 0f..1000f,
                        enabled = on, startIcon = IcSatLow, endIcon = IcSatHigh,
                        onFinished = { c.recents.remember(led.hue, led.sat) },
                    )
                }
            }
        }
        Title("Cores recentes (segure para fixar)")
        val rec = c.recents.list
        val sw = if (land) 28.dp else 34.dp
        for (row in 0 until 2) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                for (col in 0 until 8) {
                    val i = row * 8 + col
                    val item = rec.getOrNull(i)
                    val m = Modifier.size(sw).clip(CircleShape)
                    if (item == null) {
                        Box(m.background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)))
                    } else {
                        Box(
                            m.background(Color.hsv(item.h.toFloat().coerceIn(0f, 360f), (item.s / 1000f).coerceIn(0f, 1f), 1f))
                                .combinedClickable(
                                    onClick = { tick(); if (on) led.setColor(item.h, item.s) },
                                    onLongClick = { tick(); c.recents.togglePin(i) },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (item.pinned) Box(Modifier.size(10.dp).clip(CircleShape).background(Color.White))
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Modos
@Composable
fun ModesScreen(c: Container, pad: PaddingValues) {
    val led = c.led
    val on = led.link == LedController.Link.ON
    val groups = if (c.prefs.experimental.value) MODE_GROUPS + MODE_EXTRA else MODE_GROUPS
    var cat by remember { mutableIntStateOf(0) }
    var mid by remember { mutableIntStateOf(0) }
    var speed by remember { mutableFloatStateOf(50f) }
    var dir by remember { mutableStateOf(false) }
    val cur = groups[cat.coerceIn(0, groups.size - 1)]
    val tick = LocalTick.current
    val land = LocalLand.current
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(pad).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BlurCard {
            Column {
                Text("Brilho · ${led.bright / 10}%", style = MaterialTheme.typography.titleSmall)
                ExpressiveSlider(led.bright.toFloat(), { led.changeBright(it.toInt()) }, 0f..1000f, enabled = on, startIcon = IcSunLow, endIcon = IcSunHigh)
            }
        }
        Title("Categorias")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            groups.forEachIndexed { i, g ->
                FilterChip(selected = i == cat, onClick = { tick(); cat = i }, label = { Text(g.name) })
            }
        }
        Title("Modos")
        cur.modes.chunked(3).forEachIndexed { r, rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEachIndexed { k, (id, name) ->
                    val sel = id == mid
                    Column(
                        Modifier.weight(1f).bounceClick(enabled = on) { c.stopAudio(); mid = id; led.mode(id) }
                            .height(if (land) 66.dp else 84.dp).clip(RoundedCornerShape(18.dp))
                            .background(if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        val fg = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        Text("${r * 3 + k + 1}", style = MaterialTheme.typography.titleMedium, color = fg.copy(alpha = .6f))
                        Text(name, style = MaterialTheme.typography.bodySmall, color = fg, maxLines = 2)
                    }
                }
                repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        BlurCard {
            Column {
                Text("Velocidade · ${speed.toInt()}", style = MaterialTheme.typography.titleSmall)
                ExpressiveSlider(speed, { speed = it; led.speed(it.toInt()) }, 1f..100f, enabled = on, startIcon = IcSlow, endIcon = IcFast)
            }
        }
        BlurCard { SwitchRow("Inverter sentido", dir, on) { dir = it; led.direction(it) } }

        if (c.prefs.adv.value) LabCard(c)
    }
}

@Composable
private fun LabCard(c: Container) {
    var text by remember { mutableStateOf("") }
    Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Laboratório de comandos", style = MaterialTheme.typography.titleMedium)
            Text("Bytes em hexadecimal, ex.: BC 13 02 00 64 55", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(value = text, onValueChange = { text = it.uppercase() }, singleLine = true, modifier = Modifier.fillMaxWidth())
            FilledTonalButton(onClick = {
                val a = text.trim().split(Regex("[\\s,]+")).filter { it.isNotEmpty() }.map { it.toIntOrNull(16) }
                if (a.isEmpty() || a.any { it == null || it < 0 || it > 255 } || a.size > 20) {
                    c.led.say("Bytes inválidos. Use hexadecimal de 00 a FF (máximo 20).")
                } else {
                    c.led.raw(ByteArray(a.size) { a[it]!!.toByte() })
                }
            }) { Text("Enviar") }
            c.led.rxLog.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

// ---------------------------------------------------------------- Microfone
@Composable
fun MicScreen(c: Container, pad: PaddingValues) {
    val led = c.led
    val mic = c.mic
    val ctx = LocalContext.current
    val on = led.link == LedController.Link.ON
    val tick = LocalTick.current
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) {
            mic.on = true
            if (!mic.apply()) led.say("Não foi possível abrir o microfone.")
        } else {
            led.say("Permita o microfone para usar o som do celular.")
        }
    }
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(pad).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Title("Fonte do som")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = mic.source == 0, onClick = { tick(); mic.source = 0; if (mic.on) mic.apply() },
                label = { Text("Microfone do aparelho") },
            )
            FilterChip(
                selected = mic.source == 1, onClick = { tick(); mic.source = 1; if (mic.on) mic.apply() },
                label = { Text("Microfone do celular") },
            )
        }
        val dev = mic.source == 0
        BlurCard {
            Column {
                Text(
                    if (dev) "Sensibilidade do aparelho · ${mic.sensDev}%" else "Sensibilidade do celular · ${mic.sensPhone}%",
                    style = MaterialTheme.typography.titleSmall,
                )
                ExpressiveSlider(
                    (if (dev) mic.sensDev else mic.sensPhone).toFloat(),
                    {
                        if (dev) { mic.sensDev = it.toInt(); if (mic.on) led.micSens(mic.sensDev) } else { mic.sensPhone = it.toInt(); mic.phoneSens(mic.sensPhone) }
                    },
                    1f..100f, startIcon = IcBarsLow, endIcon = IcBarsHigh,
                )
            }
        }
        if (dev) {
            Title("Efeito do aparelho")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in 1..4) {
                    FilterChip(
                        selected = mic.fx == i, onClick = { tick(); mic.fx = i; if (mic.on) led.micEffect(i) },
                        label = { Text("Efeito $i") },
                    )
                }
            }
        }
        Button(
            onClick = {
                tick()
                if (mic.on) {
                    mic.stop()
                } else if (mic.source == 1 && !granted(ctx, Manifest.permission.RECORD_AUDIO)) {
                    perm.launch(Manifest.permission.RECORD_AUDIO)
                } else {
                    mic.on = true
                    if (!mic.apply()) led.say("Não foi possível abrir o microfone.")
                }
            },
            shapes = ButtonDefaults.shapes(),
            enabled = on,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(52.dp),
            colors = if (mic.on) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors(),
        ) { Text(if (mic.on) "Desativar microfone" else "Ativar microfone") }
    }
}

// ---------------------------------------------------------------- Música
private fun fmt(ms: Int): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun MusicScreen(c: Container, pad: PaddingValues) {
    val m = c.music
    val ctx = LocalContext.current
    val tick = LocalTick.current
    val audioPerm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    var canRead by remember { mutableStateOf(granted(ctx, audioPerm)) }
    val readLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        canRead = ok
        if (ok) m.load()
    }
    var pending by remember { mutableIntStateOf(-1) }
    val recLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
        val i = pending
        pending = -1
        if (i >= 0) m.play(i) else m.toggle()
    }
    LaunchedEffect(canRead) { if (canRead) m.load() }
    LaunchedEffect(m.playing) {
        while (m.playing) {
            m.refresh()
            delay(300)
        }
    }
    var drag by remember { mutableStateOf<Float?>(null) }
    val t = m.tracks.getOrNull(m.index)

    LazyColumn(
        contentPadding = PaddingValues(
            top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding(), start = 20.dp, end = 20.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BlurCard {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(t?.title ?: "Nenhuma música", style = MaterialTheme.typography.titleLarge, maxLines = 1)
                        Text(
                            t?.artist?.ifBlank { "Música do aparelho" } ?: "Escolha uma música abaixo",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                        )
                        val frac = drag ?: (if (m.dur > 0) m.pos.toFloat() / m.dur else 0f)
                        ExpressiveSlider(
                            frac.coerceIn(0f, 1f), { drag = it }, 0f..1f,
                            modifier = Modifier.padding(top = 8.dp),
                            onFinished = { drag?.let { f -> m.seek((f * m.dur).toInt()) }; drag = null },
                        )
                        Row(Modifier.fillMaxWidth()) {
                            Text(fmt(drag?.let { (it * m.dur).toInt() } ?: m.pos), style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.weight(1f))
                            Text(fmt(m.dur), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                ) {
                    val ink = MaterialTheme.colorScheme.onBackground
                    Box(Modifier.bounceClick { m.prev() }.size(52.dp).clip(CircleShape), contentAlignment = Alignment.Center) { Ic(IcPrev, ink, 28.dp) }
                    Box(
                        Modifier.bounceClick {
                            if (m.playing || granted(ctx, Manifest.permission.RECORD_AUDIO)) m.toggle() else { pending = -1; recLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                        }.size(68.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) { Ic(if (m.playing) IcPause else IcPlay, MaterialTheme.colorScheme.onPrimary, 34.dp) }
                    Box(Modifier.bounceClick { m.next() }.size(52.dp).clip(CircleShape), contentAlignment = Alignment.Center) { Ic(IcNext, ink, 28.dp) }
                }
                BlurCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Variar brilho com o volume", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = m.vary, onCheckedChange = { m.vary = it })
                    }
                }
                if (m.vizMissing) {
                    Text(
                        "As luzes não reagem sem a permissão do microfone.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        item { Title("Músicas") }
        if (!canRead) {
            item {
                Button(onClick = { readLauncher.launch(audioPerm) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) { Text("Permitir acesso às músicas") }
            }
        } else if (m.tracks.isEmpty()) {
            item { Text("Nenhuma música encontrada no aparelho.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        itemsIndexed(m.tracks, key = { _, x -> x.id }) { i, x ->
            val sel = i == m.index
            Row(
                Modifier.fillMaxWidth().bounceClick {
                    if (granted(ctx, Manifest.permission.RECORD_AUDIO)) m.play(i) else { pending = i; recLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                }.clip(RoundedCornerShape(18.dp))
                    .background(if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(x.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                    if (x.artist.isNotBlank()) Text(x.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                Text(fmt(x.durMs.toInt()), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

// ---------------------------------------------------------------- Agenda
@Composable
fun ScheduleScreen(c: Container, pad: PaddingValues) {
    val led = c.led
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(pad).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Title("Agendamento no aparelho")
        SchCard("Ligar automaticamente", led.schOn) { led.schChanged(true) }
        SchCard("Desligar automaticamente", led.schOff) { led.schChanged(false) }
    }
}

@Composable
private fun SchCard(title: String, s: Sch, changed: () -> Unit) {
    val tick = LocalTick.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    BlurCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(s.summary(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = s.en, onCheckedChange = { s.en = it; changed() })
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DAYS.forEachIndexed { i, d ->
                    val sel = s.days[i]
                    Box(
                        Modifier.bounceClick { s.days[i] = !s.days[i]; changed() }.size(40.dp).clip(CircleShape)
                            .background(if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            d.take(1).uppercase(), style = MaterialTheme.typography.labelLarge,
                            color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                WheelPicker(24, s.h, { s.h = it; changed() })
                Text(":", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(horizontal = 6.dp))
                WheelPicker(60, s.m, { s.m = it; changed() })
            }
        }
    }
    }
}

// ---------------------------------------------------------------- Ajustes (engrenagem)
private fun openLiveSettings(ctx: android.content.Context) {
    val pkg = "android.provider.extra.APP_PACKAGE"
    try {
        ctx.startActivity(Intent("android.settings.MANAGE_APP_PROMOTED_NOTIFICATIONS").putExtra(pkg, ctx.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        try {
            ctx.startActivity(Intent("android.settings.APP_NOTIFICATION_SETTINGS").putExtra(pkg, ctx.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
        }
    }
}

@Composable
private fun MenuSwitch(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val card = if (isDark()) Color(0xFF444444).copy(alpha = 0.5f) else Color.White.copy(alpha = 0.5f)
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(card).padding(horizontal = 14.dp, vertical = 2.dp)) {
        SwitchRow(label, checked, enabled, onChange)
    }
}

@Composable
fun SettingsContent(c: Container, onClose: () -> Unit, onPickWall: () -> Unit) {
    val p = c.prefs
    val ctx = LocalContext.current
    val tick = LocalTick.current
    val hz = rememberHazeState()
    val blurOn = p.blur.value
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().hazeSource(hz).verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp).padding(top = 64.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Title("Aparência")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Automático", "Claro", "Escuro").forEachIndexed { i, n ->
                    FilterChip(selected = p.themeMode == i, onClick = { tick(); p.setTheme(i) }, label = { Text(n) })
                }
            }
            Text("Wallpaper", style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Cinza", "Claro", "Escuro", "Galeria").forEachIndexed { i, n ->
                    FilterChip(
                        selected = p.wallChoice == i,
                        onClick = { tick(); if (i == 3) onPickWall() else p.changeWallChoice(i) },
                        label = { Text(n) },
                    )
                }
            }
            Text("Desfoque do wallpaper", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Sem", "Leve", "Forte").forEachIndexed { i, n ->
                    FilterChip(selected = p.wallBlur == i, onClick = { tick(); p.changeWallBlur(i) }, label = { Text(n) })
                }
            }
            MenuSwitch("Blur da interface", p.blur.value) { p.blur.set(it) }
            MenuSwitch("Reduzir movimento", p.reduce.value) { p.reduce.set(it) }
            MenuSwitch("Vibração ao tocar", p.haptic.value) { p.haptic.set(it) }
            MenuSwitch("Barra de saturação", p.showSat.value) { p.showSat.set(it) }
            MenuSwitch("Botão de ligar na barra", p.powerBtn.value) { p.powerBtn.set(it) }

            Title("Notificações e Now Bar")
            MenuSwitch("Notificação de controle", p.notif.value) { p.notif.set(it) }
            MenuSwitch("Live Update na Now Bar (Samsung One UI 8 / Android 16)", p.liveUpdate.value) { p.liveUpdate.set(it) }
            MenuSwitch("Now Bar: mostrar sempre que conectado", p.liveAlways.value, p.liveUpdate.value) { p.liveAlways.set(it) }
            if (Build.VERSION.SDK_INT >= 36) {
                FilledTonalButton(
                    onClick = { tick(); openLiveSettings(ctx) }, shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Ajustes de Live Updates do sistema") }
            }

            Title("Fita LED")
            Text("Quantidade de LEDs · ${p.ledCount}", style = MaterialTheme.typography.bodyMedium)
            ExpressiveSlider(
                p.ledCount.toFloat(), { p.changeLedCount(it.toInt()) }, 8f..300f,
                startIcon = IcDotsFew, endIcon = IcDotsMany, onFinished = { c.led.ledCount(p.ledCount) },
            )
            Text("Ordem das cores", style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in 1..6) {
                    FilterChip(
                        selected = p.ledOrder == i, onClick = { tick(); p.changeLedOrder(i); c.led.colorOrder(i) },
                        label = { Text("Ordem $i") },
                    )
                }
            }

            Title("Conexão")
            MenuSwitch("Buscar aparelhos ao abrir o app", p.scanOnStart.value) { p.scanOnStart.set(it) }
            MenuSwitch("Conectar sozinho ao GATT-DEMO", p.autoDemo.value) { p.autoDemo.set(it) }
            MenuSwitch("Animação dos LEDs ao conectar", p.welcome.value) { p.welcome.set(it) }
            MenuSwitch("Reconectar se o sinal cair", p.reconnect.value) { p.reconnect.set(it) }
            MenuSwitch("Acertar o relógio do aparelho ao conectar", p.clock.value) { p.clock.set(it) }
            MenuSwitch("Confirmar antes de desconectar", p.confirmOff.value) { p.confirmOff.set(it) }

            Title("Telas na barra")
            MenuSwitch("Modos", p.tabModes.value) { p.tabModes.set(it) }
            MenuSwitch("Microfone", p.tabMic.value) { p.tabMic.set(it) }
            MenuSwitch("Música", p.tabMusic.value) { p.tabMusic.set(it) }
            MenuSwitch("Agenda", p.tabSched.value) { p.tabSched.set(it) }
            MenuSwitch("Modos experimentais", p.experimental.value) { p.experimental.set(it) }
            MenuSwitch("Lembrar a última tela", p.remember.value) { p.remember.set(it) }

            Title("Áudio")
            MenuSwitch("Manter a tela ligada com som", p.wake.value) { p.wake.set(it) }
            MenuSwitch("Parar o som ao trocar de tela", p.stopOnLeave.value) { p.stopOnLeave.set(it) }
            MenuSwitch("Tocar a próxima música automaticamente", p.autoNext.value) { p.autoNext.set(it) }

            Title("Avançado")
            MenuSwitch("Modo avançado (comandos brutos)", p.adv.value) { p.adv.set(it) }
        }
        // cabeçalho fixo: continua visível ao rolar, com blur em degradê
        if (blurOn) {
            Box(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().height(84.dp).hazeEffect(hz) {
                    blurRadius = 18.dp
                    noiseFactor = 0f
                    progressive = HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 0f)
                },
            )
        } else {
            Box(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().height(84.dp)
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent))),
            )
        }
        Row(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 18.dp).height(60.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Ajustes", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("Fechar") }
        }
    }
}

// ---------------------------------------------------------------- Lista de dispositivos
@Composable
fun ConnectContent(led: LedController, onScan: () -> Unit, onClose: () -> Unit) {
    val tick = LocalTick.current
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Dispositivos", style = MaterialTheme.typography.headlineSmall)
        Text(
            if (led.link == LedController.Link.SCANNING) "Buscando…" else "${led.found.size} encontrado(s)",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val list = led.found.sortedWith(compareByDescending<Found> { LedController.isDemo(it.name) }.thenByDescending { it.rssi }).take(30)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
            if (list.isEmpty()) {
                Text(
                    if (led.link == LedController.Link.SCANNING) "Procurando dispositivos próximos…" else "Nenhum dispositivo encontrado.",
                    Modifier.padding(vertical = 16.dp),
                )
            }
            list.forEach { f ->
                key(f.address) {
                    // cada aparelho novo se abre com uma mola suave
                    AnimatedVisibility(
                        visibleState = remember { MutableTransitionState(false).apply { targetState = true } },
                        enter = fadeIn(spring(stiffness = 200f)) + expandVertically(spring(dampingRatio = 0.8f, stiffness = 260f)),
                    ) {
                        val demo = LedController.isDemo(f.name)
                        Row(
                            Modifier.fillMaxWidth().bounceClick { led.connect(f) }.clip(RoundedCornerShape(16.dp))
                                .background(if (demo) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else Color.Transparent)
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (demo) "leds do quarto" else f.name.ifBlank { "Dispositivo sem nome" },
                                    style = MaterialTheme.typography.bodyLarge, fontWeight = if (demo) FontWeight.Bold else FontWeight.Normal,
                                )
                                Text(f.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("${f.rssi} dBm", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { tick(); onScan() }) { Text("Buscar novamente") }
            TextButton(onClick = onClose) { Text("Fechar") }
        }
    }
}

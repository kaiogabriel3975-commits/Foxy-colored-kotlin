package com.kitsune.color

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

@Composable
fun App(led: LedController) {
    val ctx = LocalContext.current
    var showList by remember { mutableStateOf(false) }
    val perms = remember { arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT) }
    fun granted() = perms.all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.all { it }) led.startScan() else led.say("Permita o acesso a dispositivos próximos para conectar.")
    }
    LaunchedEffect(Unit) { if (granted()) led.startScan() else launcher.launch(perms) }
    LaunchedEffect(led.link) {
        if (led.link == LedController.Link.ON || led.link == LedController.Link.CONNECTING) showList = false
    }

    val on = led.link == LedController.Link.ON
    val status = when (led.link) {
        LedController.Link.OFF -> "Desconectado"
        LedController.Link.SCANNING -> "Buscando aparelhos…"
        LedController.Link.CONNECTING -> "Conectando…"
        LedController.Link.ON -> "Conectado · ${led.deviceName}"
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Kitsune color", style = MaterialTheme.typography.headlineMedium)
                    Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilledTonalButton(onClick = {
                    if (on) {
                        led.disconnect()
                    } else {
                        showList = true
                        if (granted()) led.startScan() else launcher.launch(perms)
                    }
                }) { Text(if (on) "Desconectar" else "Conectar") }
            }

            if (led.message.isNotEmpty()) {
                Text(led.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(
                            Modifier.size(48.dp).clip(CircleShape)
                                .background(Color.hsv(led.hue.toFloat(), led.sat / 1000f, 1f)),
                        )
                        Text("  Cor", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Switch(checked = led.power, onCheckedChange = { led.updatePower(it) }, enabled = on)
                    }
                    Text("Matiz", style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = led.hue.toFloat(),
                        onValueChange = { led.setColor(it.toInt(), led.sat) },
                        valueRange = 0f..360f,
                        enabled = on,
                    )
                    Text("Saturação", style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = led.sat.toFloat(),
                        onValueChange = { led.setColor(led.hue, it.toInt()) },
                        valueRange = 0f..1000f,
                        enabled = on,
                    )
                    Text("Brilho", style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = led.bright.toFloat(),
                        onValueChange = { led.updateBrightness(it.toInt()) },
                        valueRange = 100f..1000f,
                        enabled = on,
                    )
                }
            }
        }
    }

    if (showList) {
        AlertDialog(
            onDismissRequest = { showList = false },
            title = { Text("Dispositivos") },
            text = {
                val list = led.found.sortedWith(
                    compareByDescending<Found> { LedController.isDemo(it.name) }.thenByDescending { it.rssi },
                )
                if (list.isEmpty()) {
                    Text(
                        if (led.link == LedController.Link.SCANNING) "Procurando dispositivos próximos…" else "Nenhum dispositivo encontrado.",
                    )
                } else {
                    LazyColumn {
                        items(list) { f ->
                            Row(
                                Modifier.fillMaxWidth().clickable { led.connect(f) }.padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        if (LedController.isDemo(f.name)) "leds do quarto" else f.name.ifBlank { "Dispositivo sem nome" },
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    Text(f.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("${f.rssi} dBm", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { if (granted()) led.startScan() else launcher.launch(perms) }) { Text("Buscar novamente") }
            },
            dismissButton = { TextButton(onClick = { showList = false }) { Text("Fechar") } },
        )
    }
}

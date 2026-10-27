package com.kitsune.color

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect

class MainActivity : ComponentActivity() {
    private lateinit var box: Container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        box = Container(applicationContext)
        Hub.box = box
        setContent {
            val sysDark = isSystemInDarkTheme()
            // textos e ícones se adaptam ao fundo: wallpaper escuro = tema escuro
            val dark = when (box.prefs.themeMode) {
                1 -> false
                2 -> true
                else -> if (box.wallLuma >= 0f) box.wallLuma < 0.5f else sysDark
            }
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                window.isNavigationBarContrastEnforced = false // sem a faixa escura na barra de navegação
                onDispose { }
            }
            KitsuneTheme(dark) { App(box, dark) }
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            box.stopAudio()
            box.led.close()
            Surfaces.cancelNotification(applicationContext)
        }
        super.onDestroy()
    }
}

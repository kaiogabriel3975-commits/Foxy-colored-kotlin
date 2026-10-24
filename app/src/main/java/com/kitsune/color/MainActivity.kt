package com.kitsune.color

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme

class MainActivity : ComponentActivity() {
    private lateinit var box: Container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        box = Container(applicationContext)
        setContent {
            val dark = when (box.prefs.themeMode) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }
            KitsuneTheme(dark) { App(box, dark) }
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            box.stopAudio()
            box.led.close()
        }
        super.onDestroy()
    }
}

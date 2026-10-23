package com.kitsune.color

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    private lateinit var led: LedController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        led = LedController(applicationContext)
        setContent { KitsuneTheme { App(led) } }
    }

    override fun onDestroy() {
        if (isFinishing) led.close()
        super.onDestroy()
    }
}

package com.kitsune.color

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/** Cores dinâmicas do Material You (Android 12+), que acompanham o papel de parede do sistema. */
@Composable
fun KitsuneTheme(dark: Boolean, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val scheme = if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    MaterialTheme(colorScheme = scheme, content = content)
}

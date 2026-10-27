@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.kitsune.color

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** Cores dinâmicas do Material You + física do Material Expressive. No escuro: fundo #121212, cartões #444444 e texto branco. */
@Composable
fun KitsuneTheme(dark: Boolean, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    var scheme = if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    if (dark) {
        val card = Color(0xFF444444)
        scheme = scheme.copy(
            background = Color(0xFF121212),
            surface = Color(0xFF121212),
            surfaceContainerLowest = Color(0xFF121212),
            surfaceContainerLow = card,
            surfaceContainer = card,
            surfaceContainerHigh = card,
            surfaceContainerHighest = card,
            onBackground = Color.White,
            onSurface = Color.White,
            onSurfaceVariant = Color(0xFFD0D0D0),
        )
    }
    MaterialExpressiveTheme(colorScheme = scheme, motionScheme = MotionScheme.expressive(), content = content)
}

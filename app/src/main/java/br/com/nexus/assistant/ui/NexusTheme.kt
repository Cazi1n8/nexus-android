package br.com.nexus.assistant.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val NexusCyan = Color(0xFF51E6FF)
val NexusBlue = Color(0xFF6E91FF)
val NexusBackground = Color(0xFF04090F)
val NexusPanel = Color(0xFF0C1823)
internal val LocalReducedEffects = staticCompositionLocalOf { false }

@Composable
fun NexusTheme(reducedEffects: Boolean = false, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalReducedEffects provides reducedEffects) {
    MaterialTheme(colorScheme = darkColorScheme(
        primary = NexusCyan, onPrimary = Color(0xFF002D36), secondary = NexusBlue,
        background = NexusBackground, onBackground = Color(0xFFE3F3FF),
        surface = NexusPanel, onSurface = Color(0xFFE3F3FF), surfaceVariant = Color(0xFF142632),
        onSurfaceVariant = Color(0xFFACC5D5), outline = Color(0xFF3B596A), error = Color(0xFFFFB4AB),
    ), content = content)
    }
}

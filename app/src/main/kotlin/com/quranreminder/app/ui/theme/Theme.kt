
package com.quranreminder.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** The parchment, emerald and antique-gold system from the Nur design. */
object NurColors {
    val Ink = Color(0xFF16332E)
    val InkSoft = Color(0xFF1F433C)
    val Parchment = Color(0xFFF6EFDE)
    val Sand = Color(0xFFEAE0C7)
    val SandDark = Color(0xFFDED0AC)
    val Gold = Color(0xFFB8912F)
    val GoldSoft = Color(0xFFD8C077)
    val Charcoal = Color(0xFF2A241C)
    val CharcoalSoft = Color(0xFF6B6152)
    val Terracotta = Color(0xFFA6503E)
    val TerracottaSoft = Color(0xFFE9CFC6)
    val Sage = Color(0xFF5C7A63)
    val SageSoft = Color(0xFFD9E3D8)
    val Night = Color(0xFF0D211E)
    val NightSoft = Color(0xFF12312C)
    val Cream = Color(0xFFFFFCF6)
}

private val DarkColorScheme = darkColorScheme(
    primary = NurColors.GoldSoft,
    onPrimary = NurColors.Night,
    primaryContainer = NurColors.InkSoft,
    onPrimaryContainer = NurColors.Cream,
    secondary = NurColors.Gold,
    onSecondary = NurColors.Night,
    secondaryContainer = Color(0xFF4C3B14),
    onSecondaryContainer = NurColors.GoldSoft,
    tertiary = Color(0xFF9AB59E),
    onTertiary = NurColors.Night,
    tertiaryContainer = Color(0xFF243F31),
    onTertiaryContainer = Color(0xFFD9E3D8),
    background = NurColors.Night,
    onBackground = NurColors.Cream,
    surface = NurColors.NightSoft,
    onSurface = NurColors.Cream,
    surfaceVariant = NurColors.InkSoft,
    onSurfaceVariant = Color(0xFFC7D7D0),
    outline = Color(0xFF31564D),
    error = Color(0xFFE7A092),
    onError = NurColors.Night
)

private val LightColorScheme = lightColorScheme(
    primary = NurColors.Ink,
    onPrimary = NurColors.Cream,
    primaryContainer = NurColors.SageSoft,
    onPrimaryContainer = NurColors.Ink,
    secondary = NurColors.Gold,
    onSecondary = Color(0xFF231B08),
    secondaryContainer = Color(0xFFF1E5BC),
    onSecondaryContainer = Color(0xFF4A390C),
    tertiary = NurColors.Sage,
    onTertiary = NurColors.Cream,
    tertiaryContainer = NurColors.SageSoft,
    onTertiaryContainer = Color(0xFF294332),
    background = NurColors.Parchment,
    onBackground = NurColors.Charcoal,
    surface = NurColors.Cream,
    onSurface = NurColors.Charcoal,
    surfaceVariant = NurColors.Sand,
    onSurfaceVariant = NurColors.CharcoalSoft,
    outline = NurColors.SandDark,
    error = NurColors.Terracotta,
    onError = Color.White
)

@Composable
fun QuranReminderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}

package com.quranreminder.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Noorain palette: deep mosque green, antique gold and warm paper.
 * Older names (Ink, Parchment, Cream…) are kept so existing call sites map onto the new system.
 */
object NurColors {
    // Greens
    val Green = Color(0xFF0E3B2E)
    val GreenDeep = Color(0xFF082A20)
    val GreenBright = Color(0xFF1C5A47)
    val GreenMist = Color(0xFFDCEBE3)
    val NightGreen = Color(0xFF07211A)
    val NightSurface = Color(0xFF0C2C23)
    val NightRaised = Color(0xFF123A2F)

    // Golds
    val Gold = Color(0xFFC9A646)
    val GoldText = Color(0xFF8E6F1C) // gold dark enough for text on paper (≥4.5:1)
    val GoldLight = Color(0xFFE6CC7E)
    val GoldPale = Color(0xFFF3E6BF)

    // Paper
    val Paper = Color(0xFFF6F2E8)
    val PaperRaised = Color(0xFFFFFDF8)
    val PaperSunk = Color(0xFFEFE8D8)
    val Hairline = Color(0xFFDCCFAE)

    val InkText = Color(0xFF1B2620)
    val InkMuted = Color(0xFF55615A)
    val Danger = Color(0xFFA8432F)
    val DangerSoft = Color(0xFFF6DAD6)
    val Success = Color(0xFF2E7D5B)
    val SuccessSoft = Color(0xFFD6EEE2)

    // Legacy aliases
    val Ink = Green
    val InkSoft = GreenBright
    val Parchment = Paper
    val Sand = PaperSunk
    val SandDark = Hairline
    val GoldSoft = GoldLight
    val Charcoal = InkText
    val CharcoalSoft = InkMuted
    val Terracotta = Danger
    val TerracottaSoft = DangerSoft
    val Sage = Success
    val SageSoft = SuccessSoft
    val Night = NightGreen
    val NightSoft = NightSurface
    val Cream = PaperRaised
}

/** Colors that sit outside Material's roles: the green header band and its ornament. */
data class NurExtraColors(
    val band: Color,
    val bandDeep: Color,
    val onBand: Color,
    val onBandMuted: Color,
    val ornament: Color,
    val goldText: Color,
    val frame: Color,
    val isDark: Boolean
)

private val LightExtras = NurExtraColors(
    band = NurColors.Green,
    bandDeep = NurColors.GreenDeep,
    onBand = NurColors.PaperRaised,
    onBandMuted = Color(0xFFC9DCD2),
    ornament = NurColors.Gold,
    goldText = NurColors.GoldText,
    frame = NurColors.Gold.copy(alpha = 0.55f),
    isDark = false
)

private val DarkExtras = NurExtraColors(
    band = Color(0xFF0B3227),
    bandDeep = Color(0xFF051A14),
    onBand = Color(0xFFF3EEDF),
    onBandMuted = Color(0xFFA9C2B6),
    ornament = NurColors.GoldLight,
    goldText = NurColors.GoldLight,
    frame = NurColors.GoldLight.copy(alpha = 0.35f),
    isDark = true
)

val LocalNurColors = staticCompositionLocalOf { LightExtras }

private val DarkColorScheme = darkColorScheme(
    primary = NurColors.GoldLight,
    onPrimary = NurColors.GreenDeep,
    primaryContainer = NurColors.NightRaised,
    onPrimaryContainer = Color(0xFFF3EEDF),
    secondary = NurColors.GoldLight,
    onSecondary = NurColors.GreenDeep,
    secondaryContainer = Color(0xFF3D3214),
    onSecondaryContainer = NurColors.GoldPale,
    tertiary = Color(0xFF8FC3AB),
    onTertiary = NurColors.GreenDeep,
    tertiaryContainer = Color(0xFF1B4638),
    onTertiaryContainer = Color(0xFFCDE6DA),
    background = NurColors.NightGreen,
    onBackground = Color(0xFFF3EEDF),
    surface = NurColors.NightSurface,
    onSurface = Color(0xFFF3EEDF),
    surfaceVariant = NurColors.NightRaised,
    onSurfaceVariant = Color(0xFFB9CCC2),
    outline = Color(0xFF2E5A4C),
    outlineVariant = Color(0xFF1E4337),
    // Warm terracotta, not Material's default pink, so errors stay inside the green/gold world.
    error = Color(0xFFE59A7A),
    onError = Color(0xFF3A120A),
    errorContainer = Color(0xFF4A2117),
    onErrorContainer = Color(0xFFF6D3C4)
)

private val LightColorScheme = lightColorScheme(
    primary = NurColors.Green,
    onPrimary = NurColors.PaperRaised,
    primaryContainer = NurColors.GreenMist,
    onPrimaryContainer = NurColors.Green,
    secondary = NurColors.GoldText,
    onSecondary = Color.White,
    secondaryContainer = NurColors.GoldPale,
    onSecondaryContainer = Color(0xFF3F300A),
    tertiary = NurColors.GreenBright,
    onTertiary = Color.White,
    tertiaryContainer = NurColors.SuccessSoft,
    onTertiaryContainer = Color(0xFF0F3B29),
    background = NurColors.Paper,
    onBackground = NurColors.InkText,
    surface = NurColors.PaperRaised,
    onSurface = NurColors.InkText,
    surfaceVariant = NurColors.PaperSunk,
    onSurfaceVariant = NurColors.InkMuted,
    outline = NurColors.Hairline,
    outlineVariant = Color(0xFFE8DFCB),
    error = NurColors.Danger,
    onError = Color.White,
    errorContainer = NurColors.DangerSoft,
    onErrorContainer = Color(0xFF5C1A15)
)

@Composable
fun QuranReminderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalNurColors provides if (darkTheme) DarkExtras else LightExtras) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = Typography,
            shapes = NurShapes,
            content = content
        )
    }
}

/** Shorthand for the non-Material colors of the current theme. */
val nurColors: NurExtraColors
    @Composable get() = LocalNurColors.current

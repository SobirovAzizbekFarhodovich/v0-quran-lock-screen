package com.quranreminder.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quranreminder.app.R

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun golos(weight: FontWeight) = Font(
    R.font.golos_text,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight))
)

object NurFonts {
    /** Interface face: Golos Text (variable weight; Latin, Cyrillic incl. Uzbek). */
    val Interface = FontFamily(
        golos(FontWeight.Normal),
        golos(FontWeight.Medium),
        golos(FontWeight.SemiBold),
        golos(FontWeight.Bold)
    )

    /** Headings: PT Serif. */
    val Heading = FontFamily(
        Font(R.font.pt_serif_regular, FontWeight.Normal),
        Font(R.font.pt_serif_bold, FontWeight.Bold)
    )

    /** Quran text: Amiri Quran (Uthmani marks). */
    val Quran = FontFamily(Font(R.font.amiri_quran, FontWeight.Normal))
}

/** Arabic ayah text. Line height is generous so stacked marks never collide. */
fun quranTextStyle(sizeSp: Int = 26) = TextStyle(
    fontFamily = NurFonts.Quran,
    fontSize = sizeSp.sp,
    lineHeight = (sizeSp * 2.0f).sp,
    textDirection = TextDirection.Rtl
)

private val H = NurFonts.Heading
private val UI = NurFonts.Interface

val Typography = Typography(
    displayLarge = TextStyle(fontFamily = H, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 46.sp),
    displayMedium = TextStyle(fontFamily = H, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp),
    displaySmall = TextStyle(fontFamily = H, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp),
    headlineLarge = TextStyle(fontFamily = H, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
    headlineMedium = TextStyle(fontFamily = H, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),
    headlineSmall = TextStyle(fontFamily = H, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleLarge = TextStyle(fontFamily = H, fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 27.sp),
    titleMedium = TextStyle(fontFamily = UI, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 23.sp),
    titleSmall = TextStyle(fontFamily = UI, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 21.sp),
    bodyLarge = TextStyle(fontFamily = UI, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontFamily = UI, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = UI, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = UI, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = UI, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 17.sp),
    labelSmall = TextStyle(fontFamily = UI, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp)
)

val NurShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

package com.nexoratech.markets.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.nexoratech.markets.R

/** JetBrains Mono — every number on a trading screen uses this family:
 *  prices, scores, levels. Terminal-grade legibility, tabular by nature,
 *  and the core of Nexora's visual identity vs template-styled competitors. */
val NexoraMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

object NexoraNumeric {
    /** Big headline price on detail screens. */
    val priceLarge = TextStyle(
        fontFamily = NexoraMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        letterSpacing = (-0.5).sp,
    )
    /** Price in list rows. */
    val priceMedium = TextStyle(
        fontFamily = NexoraMono,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
    )
    /** Percent change. */
    val change = TextStyle(
        fontFamily = NexoraMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
    )
    /** Composite score readouts. */
    val score = TextStyle(
        fontFamily = NexoraMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
    )
}

val NexoraTypography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 30.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, letterSpacing = 0.15.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.5.sp),
)

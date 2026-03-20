package com.cleanx.lcx.core.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.cleanx.lcx.core.R

private val LcxDisplayFontFamily = FontFamily(
    Font(R.font.nexa_extralight, FontWeight.Light),
    Font(R.font.nexa_extralight, FontWeight.Normal),
    Font(R.font.nexa_heavy, FontWeight.SemiBold),
    Font(R.font.nexa_heavy, FontWeight.Bold),
    Font(R.font.nexa_heavy, FontWeight.ExtraBold),
)

private val LcxEditorialFontFamily = FontFamily(
    Font(R.font.baskerville_regular, FontWeight.Normal),
    Font(R.font.baskerville_regular, FontWeight.Medium),
    Font(R.font.baskerville_semibold_italic, FontWeight.SemiBold, style = FontStyle.Italic),
)

/**
 * Typography mapped from the Clean X brand kit.
 * - Nexa drives titles, labels, and primary actions.
 * - Baskerville supports editorial and descriptive copy.
 */
val LcxTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = LcxDisplayFontFamily,
        fontSize = 56.sp,
        lineHeight = 62.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.4).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = LcxDisplayFontFamily,
        fontSize = 44.sp,
        lineHeight = 50.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.25).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = LcxDisplayFontFamily,
        fontSize = 36.sp,
        lineHeight = 42.sp,
        fontWeight = FontWeight.Bold,
    ),
    headlineLarge = TextStyle(
        fontFamily = LcxDisplayFontFamily,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        fontWeight = FontWeight.Bold,
    ),
    headlineMedium = TextStyle(
        fontFamily = LcxDisplayFontFamily,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.Bold,
    ),
    headlineSmall = TextStyle(
        fontFamily = LcxDisplayFontFamily,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.Bold,
    ),
    titleLarge = TextStyle(
        fontFamily = LcxDisplayFontFamily,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold,
    ),
    titleMedium = TextStyle(
        fontFamily = LcxDisplayFontFamily,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = LcxDisplayFontFamily,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = LcxDisplayFontFamily,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.2.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = LcxEditorialFontFamily,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodySmall = TextStyle(
        fontFamily = LcxEditorialFontFamily,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
    ),
    labelLarge = TextStyle(
        fontFamily = LcxDisplayFontFamily,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.2.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = LcxDisplayFontFamily,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.3.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = LcxDisplayFontFamily,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.35.sp,
    ),
)

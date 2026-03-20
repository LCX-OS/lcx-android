package com.cleanx.lcx.core.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = LcxBrandInk,
    onPrimary = LcxBrandWhite,
    primaryContainer = LcxBrandBlue,
    onPrimaryContainer = LcxBrandInk,
    secondary = LcxBrandOlive,
    onSecondary = LcxBrandInk,
    secondaryContainer = LcxBrandBlueSoft,
    onSecondaryContainer = LcxBrandInk,
    tertiary = LcxBrandCream,
    onTertiary = LcxBrandInk,
    tertiaryContainer = LcxBrandCream,
    onTertiaryContainer = LcxBrandInk,
    background = LcxBackgroundWarm,
    onBackground = LcxBrandInk,
    surface = LcxSurface,
    onSurface = LcxOnSurface,
    surfaceVariant = Color(0xFFF0F6F8),
    onSurfaceVariant = LcxOnSurfaceMuted,
    outline = LcxOutlineStrong,
    outlineVariant = LcxOutline,
    inverseSurface = LcxBrandInk,
    inverseOnSurface = LcxBrandWhite,
    inversePrimary = LcxBrandBlue,
    error = LcxError,
    onError = LcxBrandWhite,
    errorContainer = Color(0xFFF6E1DE),
    onErrorContainer = Color(0xFF7C423D),
    surfaceTint = LcxBrandBlue,
)

private val DarkColorScheme = darkColorScheme(
    primary = LcxBrandBlue,
    onPrimary = LcxBrandInk,
    primaryContainer = Color(0xFF203238),
    onPrimaryContainer = Color(0xFFE7F3F8),
    secondary = Color(0xFFC5C7A0),
    onSecondary = Color(0xFF2F352A),
    secondaryContainer = Color(0xFF354247),
    onSecondaryContainer = Color(0xFFE7F1F4),
    tertiary = Color(0xFFE6DCC9),
    onTertiary = LcxBrandInk,
    tertiaryContainer = Color(0xFF463F35),
    onTertiaryContainer = Color(0xFFF5EEDF),
    background = Color(0xFF101718),
    onBackground = Color(0xFFF2F6F7),
    surface = Color(0xFF162021),
    onSurface = Color(0xFFF2F6F7),
    surfaceVariant = Color(0xFF233338),
    onSurfaceVariant = Color(0xFFC3D2D7),
    outline = Color(0xFF74898F),
    outlineVariant = Color(0xFF33474C),
    inverseSurface = LcxBrandWhite,
    inverseOnSurface = LcxBrandInk,
    inversePrimary = LcxBrandInk,
    error = LcxErrorDark,
    onError = Color(0xFF54221E),
    errorContainer = Color(0xFF6F3631),
    onErrorContainer = Color(0xFFF8D6D0),
    surfaceTint = LcxBrandBlue,
)

@Composable
fun LcxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    CompositionLocalProvider(
        LocalLcxSpacing provides LcxSpacing,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = LcxTypography,
            shapes = LcxShapes,
            content = content,
        )
    }
}

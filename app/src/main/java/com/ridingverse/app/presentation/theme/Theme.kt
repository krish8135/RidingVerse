package com.ridingverse.app.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val RidingVerseColorScheme = darkColorScheme(
    primary = Emerald,
    onPrimary = VoidBlack,
    secondary = Cyan,
    onSecondary = VoidBlack,
    tertiary = Amber,
    background = VoidBlack,
    onBackground = OffWhite,
    surface = PanelBlack,
    onSurface = OffWhite,
    surfaceVariant = PanelElevated,
    onSurfaceVariant = SteelGrey,
    error = SignalRed,
    onError = OffWhite,
    outline = DimWhite
)

@Composable
fun RidingVerseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RidingVerseColorScheme,
        typography = RidingVerseTypography,
        content = content
    )
}

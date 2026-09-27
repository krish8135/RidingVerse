package com.ridingverse.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val RidingVerseDarkColorScheme = darkColorScheme(
    primary = RidingVerseAccent,
    secondary = RidingVerseCyan,
    tertiary = RidingVerseAmber,
    background = RidingVerseBackground,
    surface = RidingVersePanel,
    onPrimary = RidingVerseBackground,
    onSecondary = RidingVerseBackground,
    onBackground = RidingVerseText,
    onSurface = RidingVerseText
)

@Composable
fun RidingVerseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = RidingVerseDarkColorScheme

    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}

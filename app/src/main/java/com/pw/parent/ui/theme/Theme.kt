package com.pw.parent.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// The Parent App is a light-only product: the web app it hosts has no dark theme, so the
// native chrome (status bar, navigation bar, error panels, progress bar) stays pure white
// with dark system icons. There is intentionally no dark color scheme any more — the
// system dark toggle must never change how the container looks.
private val LightColorScheme = lightColorScheme(
    primary = ParentPrimaryLight,
    secondary = ParentSecondaryLight,
    tertiary = ParentAccent,
    background = ParentLightBackground,
    surface = ParentSurfaceLight,
    surfaceVariant = ParentSurfaceCardLight,
    onPrimary = ParentSurfaceLight,
    onSecondary = ParentSurfaceLight,
    onBackground = ParentDarkBackground,
    onSurface = ParentDarkBackground
)

@Composable
fun MyApplicationTheme(
    // Kept for call-site compatibility (tests / previews pass it); the app is light-only,
    // so the value is ignored on purpose.
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            SideEffect {
                // White bars + dark icons on every API level.
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = true
                insetsController.isAppearanceLightNavigationBars = true
            }
        }
    }

    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}

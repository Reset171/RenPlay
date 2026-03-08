package ru.reset.renplay.ui.theme

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import ru.reset.renplay.ui.settings.ThemeOption

private val BlackColorScheme = DarkColors.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black
)

@Composable
fun MyComposeApplicationTheme(
    themeOption: ThemeOption = ThemeOption.SYSTEM,
    dynamicColor: Boolean,
    content: @Composable () -> Unit
) {
    val isDarkTheme = when (themeOption) {
        ThemeOption.SYSTEM -> isSystemInDarkTheme()
        ThemeOption.LIGHT -> false
        ThemeOption.DARK, ThemeOption.BLACK -> true
    }

    val context = LocalContext.current

    val colorScheme = remember(themeOption, dynamicColor, isDarkTheme) {
        selectColorScheme(themeOption, dynamicColor, isDarkTheme, context)
    }

    SystemBarStyler(isDarkTheme = isDarkTheme)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

private fun selectColorScheme(
    themeOption: ThemeOption,
    useDynamicColor: Boolean,
    isDarkTheme: Boolean,
    context: Context
): ColorScheme {
    val dynamicColorsAvailable = useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    return when {
        dynamicColorsAvailable -> {
            when {
                themeOption == ThemeOption.BLACK ->
                    dynamicDarkColorScheme(context).copy(background = Color.Black, surface = Color.Black)

                isDarkTheme -> {
                    val darkScheme = dynamicDarkColorScheme(context)
                    darkScheme.copy(
                        background = darkScheme.surfaceContainerLowest,
                        surface = darkScheme.surfaceContainerLowest
                    )
                }
                
                else -> dynamicLightColorScheme(context)
            }
        }
        themeOption == ThemeOption.BLACK -> BlackColorScheme
        isDarkTheme -> DarkColors
        else -> LightColors
    }
}

@Suppress("DEPRECATION")
@Composable
private fun SystemBarStyler(isDarkTheme: Boolean) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            
            WindowCompat.setDecorFitsSystemWindows(window, false)
            
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !isDarkTheme
            insetsController.isAppearanceLightNavigationBars = !isDarkTheme
        }
    }
}

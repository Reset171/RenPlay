package ru.reset.renplay.ui.settings

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

import ru.reset.renplay.ui.settings.components.LocalSettingsTransition
import ru.reset.renplay.ui.settings.components.SettingsTransitionOverlay
import ru.reset.renplay.ui.settings.components.SettingsTransitionState
import ru.reset.renplay.ui.navigation.Screen

@Composable
fun SettingsNavGraph(
    mainNavController: NavController,
    uiStyle: UiStyle,
    onUiStyleChange: (UiStyle) -> Unit,
    useDynamicTheme: Boolean,
    onDynamicThemeChange: (Boolean) -> Unit,
    themeOption: ThemeOption,
    onThemeOptionChange: (ThemeOption) -> Unit,
    appLanguage: String,
    onAppLanguageChange: (String) -> Unit,
    enableBlur: Boolean,
    onEnableBlurChange: (Boolean) -> Unit
) {
    val settingsNavController = rememberNavController()

    val physicsSpecFloat = spring<Float>(dampingRatio = 0.9f, stiffness = 280f)
    val physicsSpecInt = spring<IntOffset>(dampingRatio = 0.9f, stiffness = 280f)

    val transitionState = remember { SettingsTransitionState() }

    CompositionLocalProvider(LocalSettingsTransition provides transitionState) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            NavHost(
                navController = settingsNavController,
                startDestination = Screen.SettingsList.route,
                modifier = Modifier.fillMaxSize(),

                enterTransition = {
                slideInHorizontally(initialOffsetX = { it }, animationSpec = physicsSpecInt)
            },
            exitTransition = {
                scaleOut(targetScale = 0.9f, animationSpec = physicsSpecFloat) +
                fadeOut(targetAlpha = 0.3f, animationSpec = physicsSpecFloat) +
                slideOutHorizontally(targetOffsetX = { -it / 4 }, animationSpec = physicsSpecInt)
            },
            popEnterTransition = {
                scaleIn(initialScale = 0.9f, animationSpec = physicsSpecFloat) +
                fadeIn(initialAlpha = 0.3f, animationSpec = physicsSpecFloat) +
                slideInHorizontally(initialOffsetX = { -it / 4 }, animationSpec = physicsSpecInt)
            },
            popExitTransition = {
                slideOutHorizontally(targetOffsetX = { it }, animationSpec = physicsSpecInt)
            }
        ) {

                composable(Screen.SettingsList.route) {
                    SettingsListScreen(
                        mainNavController = mainNavController,
                        navController = settingsNavController
                    )
                }

                composable(Screen.About.route) {
                    AboutScreen(
                        navController = settingsNavController,
                        transition = this.transition
                    )
                }

                composable(Screen.Appearance.route) {
                    AppearanceScreen(
                        navController = settingsNavController,
                        uiStyle = uiStyle,
                        onUiStyleChange = onUiStyleChange,
                        useDynamicTheme = useDynamicTheme,
                        onDynamicThemeChange = onDynamicThemeChange,
                        themeOption = themeOption,
                        onThemeOptionChange = onThemeOptionChange,
                        appLanguage = appLanguage,
                        onAppLanguageChange = onAppLanguageChange,
                        enableBlur = enableBlur,
                        onEnableBlurChange = onEnableBlurChange,
                        transition = this.transition
                    )
                }

                composable(Screen.EngineSettings.route) {
                    EngineSettingsScreen(
                        mainNavController = mainNavController,
                        navController = settingsNavController,
                        transition = this.transition
                    )
                }

                composable(Screen.TranslationSettings.route) {
                    TranslationSettingsScreen(
                        navController = settingsNavController,
                        transition = this.transition
                    )
                }
            }
            SettingsTransitionOverlay()
        }
    }
}


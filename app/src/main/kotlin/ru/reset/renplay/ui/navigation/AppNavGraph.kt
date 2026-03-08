package ru.reset.renplay.ui.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ru.reset.renplay.ui.components.feedback.LocalAppBlurState
import ru.reset.renplay.ui.components.feedback.appBlurSource
import ru.reset.renplay.ui.picker.FolderPickerScreen
import ru.reset.renplay.ui.details.GameDetailsScreen
import ru.reset.renplay.ui.library.LibraryScreen
import ru.reset.renplay.ui.settings.ThemeOption
import ru.reset.renplay.ui.settings.SettingsNavGraph

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AppNavGraph(
    useDynamicTheme: Boolean,
    onDynamicThemeChange: (Boolean) -> Unit,
    themeOption: ThemeOption,
    onThemeOptionChange: (ThemeOption) -> Unit,
    appLanguage: String,
    onAppLanguageChange: (String) -> Unit,
    enableBlur: Boolean,
    onEnableBlurChange: (Boolean) -> Unit
) {
    val navController = rememberNavController()

    val physicsSpecFloat = spring<Float>(dampingRatio = 0.9f, stiffness = 280f)
    val physicsSpecInt = spring<IntOffset>(dampingRatio = 0.9f, stiffness = 280f)

    val fastTweenFloat = tween<Float>(durationMillis = 350, easing = FastOutSlowInEasing)
    val fastTweenInt = tween<IntOffset>(durationMillis = 350, easing = FastOutSlowInEasing)

    Box(modifier = Modifier.fillMaxSize()) {
        SharedTransitionLayout {
            NavHost(
                navController = navController,
                startDestination = Screen.Library.route,
                modifier = Modifier.appBlurSource(LocalAppBlurState.current),
                enterTransition = {
                    slideInHorizontally(initialOffsetX = { it }, animationSpec = physicsSpecInt)
                },
                exitTransition = {
                    scaleOut(targetScale = 0.9f, animationSpec = physicsSpecFloat) + fadeOut(targetAlpha = 0.3f, animationSpec = physicsSpecFloat) + slideOutHorizontally(targetOffsetX = { -it / 4 }, animationSpec = physicsSpecInt)
                },
                popEnterTransition = {
                    scaleIn(initialScale = 0.9f, animationSpec = physicsSpecFloat) + fadeIn(initialAlpha = 0.3f, animationSpec = physicsSpecFloat) + slideInHorizontally(initialOffsetX = { -it / 4 }, animationSpec = physicsSpecInt)
                },
                popExitTransition = {
                    slideOutHorizontally(targetOffsetX = { it }, animationSpec = physicsSpecInt)
                }
            ) {
                composable(
                    route = Screen.Library.route,
                    exitTransition = {
                        if (targetState.destination.route?.startsWith("game_details") == true) {
                            scaleOut(targetScale = 0.9f, animationSpec = fastTweenFloat) + fadeOut(targetAlpha = 0.3f, animationSpec = fastTweenFloat) + slideOutHorizontally(targetOffsetX = { -it / 4 }, animationSpec = fastTweenInt)
                        } else null
                    },
                    popEnterTransition = {
                        if (initialState.destination.route?.startsWith("game_details") == true) {
                            scaleIn(initialScale = 0.9f, animationSpec = fastTweenFloat) + fadeIn(initialAlpha = 0.3f, animationSpec = fastTweenFloat) + slideInHorizontally(initialOffsetX = { -it / 4 }, animationSpec = fastTweenInt)
                        } else null
                    }
                ) { 
                    LibraryScreen(
                        navController = navController,
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this@composable
                    ) 
                }

                composable(
                    route = Screen.GameDetails.route,
                    arguments = listOf(navArgument("projectId") { type = NavType.StringType }),
                    enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = fastTweenInt) },
                    popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = fastTweenInt) }
                ) { backStackEntry ->
                    val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
                    GameDetailsScreen(
                        navController = navController,
                        projectId = projectId,
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this@composable
                    )
                }

                composable(Screen.Settings.route) {
                    SettingsNavGraph(
                        mainNavController = navController,
                        useDynamicTheme = useDynamicTheme,
                        onDynamicThemeChange = onDynamicThemeChange,
                        themeOption = themeOption,
                        onThemeOptionChange = onThemeOptionChange,
                        appLanguage = appLanguage,
                        onAppLanguageChange = onAppLanguageChange,
                        enableBlur = enableBlur,
                        onEnableBlurChange = onEnableBlurChange
                    )
                }

                composable(
                    route = Screen.FolderPicker.route,
                    arguments = listOf(
                        navArgument("requestKey") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val requestKey = backStackEntry.arguments?.getString("requestKey") ?: ""
                    FolderPickerScreen(navController = navController, requestKey = requestKey)
                }
            }
        }
    }
}
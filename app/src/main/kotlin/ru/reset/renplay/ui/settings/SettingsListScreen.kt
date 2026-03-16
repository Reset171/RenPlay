package ru.reset.renplay.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import ru.reset.renplay.R
import ru.reset.renplay.ui.components.layout.*
import ru.reset.renplay.ui.components.inputs.*
import ru.reset.renplay.ui.components.feedback.*
import ru.reset.renplay.ui.components.typography.*
import ru.reset.renplay.ui.components.appbar.*
import ru.reset.renplay.ui.components.icons.*
import ru.reset.renplay.ui.settings.components.*
import ru.reset.renplay.ui.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsListScreen(
    mainNavController: NavController,
    navController: NavController
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settingsViewModel: ru.reset.renplay.ui.settings.SettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        viewModelStoreOwner = context as androidx.activity.ComponentActivity,
        factory = ru.reset.renplay.di.AppViewModelProvider.Factory
    )
    val advancedAnimationsEnabled by settingsViewModel.advancedAnimationsEnabled.collectAsState()

    val transitionState = LocalSettingsTransition.current
    val listFontSize = MaterialTheme.typography.bodyLarge.fontSize

    AppScaffold(
        topBar = {
            RenPlayAppBar(
                title = stringResource(R.string.nav_settings),
                navigationIcon = {
                    AppIconButton(onClick = { mainNavController.navigateUp() }) {
                        AppIcon(painterResource(R.drawable.ic_arrow_back), null)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsGroup {
                val appearanceTitle = stringResource(R.string.nav_appearance)
                SettingsItem(
                    title = appearanceTitle,
                    icon = painterResource(id = R.drawable.ic_palette),
                    onClick = { 
                        if (advancedAnimationsEnabled) {
                            transitionState.activeRoute = Screen.Appearance.route
                            transitionState.text = appearanceTitle
                        }
                        navController.navigate(Screen.Appearance.route) { launchSingleTop = true }
                    },
                    showDivider = false,
                    titleModifier = if (advancedAnimationsEnabled) {
                        Modifier.settingsTransitionSource(Screen.Appearance.route, listFontSize)
                    } else Modifier
                )
            }

            SettingsGroup {
                val engineTitle = stringResource(R.string.nav_engine_settings)
                SettingsItem(
                    title = engineTitle,
                    icon = painterResource(id = R.drawable.ic_memory),
                    onClick = { 
                        if (advancedAnimationsEnabled) {
                            transitionState.activeRoute = Screen.EngineSettings.route
                            transitionState.text = engineTitle
                        }
                        navController.navigate(Screen.EngineSettings.route) { launchSingleTop = true }
                    },
                    showDivider = false,
                    titleModifier = if (advancedAnimationsEnabled) {
                        Modifier.settingsTransitionSource(Screen.EngineSettings.route, listFontSize)
                    } else Modifier
                )
            }

            SettingsGroup {
                val aboutTitle = stringResource(R.string.nav_about)
                SettingsItem(
                    title = aboutTitle,
                    icon = painterResource(id = R.drawable.ic_info),
                    onClick = { 
                        if (advancedAnimationsEnabled) {
                            transitionState.activeRoute = Screen.About.route
                            transitionState.text = aboutTitle
                        }
                        navController.navigate(Screen.About.route) { launchSingleTop = true }
                    },
                    showDivider = false,
                    titleModifier = if (advancedAnimationsEnabled) {
                        Modifier.settingsTransitionSource(Screen.About.route, listFontSize)
                    } else Modifier
                )
            }
        }
    }
}

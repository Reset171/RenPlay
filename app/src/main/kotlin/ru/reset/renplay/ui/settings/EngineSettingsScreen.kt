package ru.reset.renplay.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.res.painterResource
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Transition
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import ru.reset.renplay.R
import ru.reset.renplay.di.AppViewModelProvider
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
fun EngineSettingsScreen(
    navController: NavController,
    transition: Transition<EnterExitState>
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val viewModel: SettingsViewModel = viewModel(viewModelStoreOwner = context as androidx.activity.ComponentActivity, factory = AppViewModelProvider.Factory)

    val advancedAnimationsEnabled by viewModel.advancedAnimationsEnabled.collectAsState()
    val hwVideoEnabled by viewModel.hwVideoEnabled.collectAsState()
    val phoneVariantEnabled by viewModel.phoneVariantEnabled.collectAsState()
    val modelRenderingEnabled by viewModel.modelRenderingEnabled.collectAsState()
    val forceRecompileEnabled by viewModel.forceRecompileEnabled.collectAsState()

    AppScaffold(
        topBar = {
            RenPlayAppBar(
                title = stringResource(R.string.nav_engine_settings),
                navigationIcon = {
                    AppIconButton(onClick = { navController.navigateUp() }) {
                        AppIcon(painterResource(R.drawable.ic_arrow_back), null)
                    }
                },
                titleModifier = if (advancedAnimationsEnabled) {
                    Modifier.settingsTransitionTarget(Screen.EngineSettings.route, MaterialTheme.typography.titleLarge.fontSize, transition)
                } else Modifier
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
            SettingsItem(
                title = stringResource(R.string.engine_hw_video),
                description = stringResource(R.string.engine_hw_video_desc),
                onClick = { viewModel.onHwVideoChanged(!hwVideoEnabled) },
                trailingContent = {
                    AppSwitch(
                        checked = hwVideoEnabled,
                        onCheckedChange = { viewModel.onHwVideoChanged(it) }
                    )
                },
                showDivider = true
            )

            SettingsItem(
                title = stringResource(R.string.engine_phone_variant),
                description = stringResource(R.string.engine_phone_variant_desc),
                onClick = { viewModel.onPhoneVariantChanged(!phoneVariantEnabled) },
                trailingContent = {
                    AppSwitch(
                        checked = phoneVariantEnabled,
                        onCheckedChange = { viewModel.onPhoneVariantChanged(it) }
                    )
                },
                showDivider = true
            )

            SettingsItem(
                title = stringResource(R.string.engine_model_rendering),
                description = stringResource(R.string.engine_model_rendering_desc),
                onClick = { viewModel.onModelRenderingChanged(!modelRenderingEnabled) },
                trailingContent = {
                    AppSwitch(
                        checked = modelRenderingEnabled,
                        onCheckedChange = { viewModel.onModelRenderingChanged(it) }
                    )
                },
                showDivider = true
            )

            SettingsItem(
                title = stringResource(R.string.engine_force_recompile),
                description = stringResource(R.string.engine_force_recompile_desc),
                onClick = { viewModel.onForceRecompileChanged(!forceRecompileEnabled) },
                trailingContent = {
                    AppSwitch(
                        checked = forceRecompileEnabled,
                        onCheckedChange = { viewModel.onForceRecompileChanged(it) }
                    )
                },
                showDivider = false
            )
        }
    }
    }
}
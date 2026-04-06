package ru.reset.renplay.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.res.painterResource
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Transition
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.animateColorAsState
import ru.reset.renplay.ui.components.feedback.LocalAppBlurState
import ru.reset.renplay.ui.components.feedback.rememberAppBlurState
import ru.reset.renplay.ui.components.feedback.appBlurEffect
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
    mainNavController: NavController,
    navController: NavController,
    transition: Transition<EnterExitState>
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val viewModel: SettingsViewModel = viewModel(viewModelStoreOwner = context as androidx.activity.ComponentActivity, factory = AppViewModelProvider.Factory)

    val advancedAnimationsEnabled by viewModel.advancedAnimationsEnabled.collectAsState()
    val engines by viewModel.engines.collectAsState()
    var engineToDelete by remember { mutableStateOf<String?>(null) }

    val currentBackStackEntry = mainNavController.currentBackStackEntry
    val engineZipPath by currentBackStackEntry?.savedStateHandle?.getStateFlow("engine_zip", "")?.collectAsState(initial = "") ?: androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }

    androidx.compose.runtime.LaunchedEffect(engineZipPath) {
        if (engineZipPath.isNotEmpty()) {
            viewModel.installEngine(android.net.Uri.fromFile(java.io.File(engineZipPath)))
            currentBackStackEntry?.savedStateHandle?.remove<String>("engine_zip")
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.loadEngines()
    }
    val hwVideoEnabled by viewModel.hwVideoEnabled.collectAsState()
    val phoneVariantEnabled by viewModel.phoneVariantEnabled.collectAsState()
    val modelRenderingEnabled by viewModel.modelRenderingEnabled.collectAsState()
    val forceRecompileEnabled by viewModel.forceRecompileEnabled.collectAsState()

    val scrollState = rememberScrollState()
    val scrollProgress by remember { derivedStateOf { (scrollState.value / 80f).coerceIn(0f, 1f) } }
    val blurActive = LocalAppBlurState.current.blurEnabled
    val screenBlurState = rememberAppBlurState()
    screenBlurState.blurEnabled = blurActive
    val buttonBgColor = if (blurActive) Color.Transparent else androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0f), MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f), scrollProgress)

    CompositionLocalProvider(LocalAppBlurState provides screenBlurState) {
        AppScaffold(
            topBar = {
            RenPlayAppBar(
                title = stringResource(R.string.nav_engine_settings),
                scrollProgress = scrollProgress,
                navigationIcon = {
                    AppIconButton(
                        onClick = { navController.navigateUp() },
                        backgroundColor = buttonBgColor,
                        shape = CircleShape,
                        modifier = Modifier.appBlurEffect(
                            state = screenBlurState,
                            shape = CircleShape,
                            blurRadius = 16.dp * scrollProgress,
                            tint = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f * scrollProgress),
                            forceInvalidation = true
                        )
                    ) {
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
                .fillMaxSize()
                .appBlurSource(screenBlurState)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState)
        ) {
            Spacer(Modifier.height(paddingValues.calculateTopPadding() + 56.dp))

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

        SettingsGroup {
            engines.forEach { engine ->
                SettingsItem(
                    title = stringResource(R.string.engine_version_format, engine.version),
                    description = stringResource(R.string.engine_installed),
                    icon = painterResource(R.drawable.ic_memory),
                    onClick = {},
                    trailingContent = {
                        AppIconButton(
                            onClick = { engineToDelete = engine.version },
                            size = 40.dp,
                            shape = CircleShape,
                            backgroundColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            iconTint = MaterialTheme.colorScheme.onSurfaceVariant
                        ) {
                            AppIcon(painterResource(R.drawable.ic_delete), null)
                        }
                    },
                    showDivider = true
                )
            }
            SettingsItem(
                title = stringResource(R.string.engine_install_zip),
                description = stringResource(R.string.engine_install_zip_desc),
                icon = painterResource(R.drawable.ic_folder_zip),
                onClick = { mainNavController.navigate(Screen.FolderPicker.createRoute("engine_zip", "zip")) },
                showDivider = false
            )
        }
        Spacer(Modifier.height(paddingValues.calculateBottomPadding() + 24.dp))
    }

        val targetEngine = engineToDelete
        if (targetEngine != null) {
            AppBottomPanel(
                onDismissRequest = { engineToDelete = null },
                title = stringResource(R.string.engine_delete_title),
                icon = painterResource(R.drawable.ic_delete),
                buttons = { dismiss ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AppButton(
                            onClick = { dismiss() },
                            text = stringResource(R.string.action_cancel),
                            modifier = Modifier.weight(1f),
                            cornerRadius = 16.dp,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        AppButton(
                            onClick = {
                                viewModel.deleteEngine(targetEngine)
                                dismiss()
                                engineToDelete = null
                            },
                            text = stringResource(R.string.action_delete),
                            modifier = Modifier.weight(1f),
                            cornerRadius = 16.dp,
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    }
                }
            ) {
                AppText(
                    text = stringResource(R.string.engine_delete_confirm, targetEngine),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
        }
    }
    }
}

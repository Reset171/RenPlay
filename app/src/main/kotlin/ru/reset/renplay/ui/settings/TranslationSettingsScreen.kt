package ru.reset.renplay.ui.settings

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Transition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import ru.reset.renplay.R
import ru.reset.renplay.di.AppViewModelProvider
import ru.reset.renplay.ui.components.appbar.RenPlayAppBar
import ru.reset.renplay.ui.components.feedback.LocalAppBlurState
import ru.reset.renplay.ui.components.feedback.appBlurEffect
import ru.reset.renplay.ui.components.feedback.appBlurSource
import ru.reset.renplay.ui.components.feedback.rememberAppBlurState
import ru.reset.renplay.ui.components.icons.AppIcon
import ru.reset.renplay.ui.components.icons.AppIconButton
import ru.reset.renplay.ui.components.inputs.AppSwitch
import ru.reset.renplay.ui.components.layout.AppBottomPanel
import ru.reset.renplay.ui.components.layout.AppScaffold
import ru.reset.renplay.ui.navigation.Screen
import ru.reset.renplay.ui.settings.components.SettingsGroup
import ru.reset.renplay.ui.settings.components.SettingsItem
import ru.reset.renplay.ui.settings.components.settingsTransitionTarget

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslationSettingsScreen(
    navController: NavController,
    transition: Transition<EnterExitState>
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val viewModel: SettingsViewModel = viewModel(viewModelStoreOwner = context as androidx.activity.ComponentActivity, factory = AppViewModelProvider.Factory)

    val advancedAnimationsEnabled by viewModel.advancedAnimationsEnabled.collectAsState()
    val enableTranslation by viewModel.enableTranslation.collectAsState()
    val sourceLang by viewModel.transSourceLang.collectAsState()
    val targetLang by viewModel.transTargetLang.collectAsState()
    val langModelStates by viewModel.langModelStates.collectAsState()

    val scrollState = rememberScrollState()
    val scrollProgress by remember { derivedStateOf { (scrollState.value / 80f).coerceIn(0f, 1f) } }
    val blurActive = LocalAppBlurState.current.blurEnabled
    val screenBlurState = rememberAppBlurState()
    screenBlurState.blurEnabled = blurActive
    val buttonBgColor = if (blurActive) Color.Transparent else androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0f), MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f), scrollProgress)

    var showSourceDialog by remember { mutableStateOf(false) }
    var showTargetDialog by remember { mutableStateOf(false) }

    val langMap = remember {
        mapOf(
            "en" to R.string.lang_en,
            "ru" to R.string.lang_ru,
            "ja" to R.string.lang_ja,
            "zh" to R.string.lang_zh,
            "ko" to R.string.lang_ko,
            "es" to R.string.lang_es,
            "fr" to R.string.lang_fr,
            "de" to R.string.lang_de
        )
    }

    CompositionLocalProvider(LocalAppBlurState provides screenBlurState) {
        AppScaffold(
            topBar = {
                RenPlayAppBar(
                    title = stringResource(R.string.translation_settings_title),
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
                        Modifier.settingsTransitionTarget(Screen.TranslationSettings.route, MaterialTheme.typography.titleLarge.fontSize, transition)
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
                        title = stringResource(R.string.trans_enable),
                        description = stringResource(R.string.trans_enable_desc),
                        icon = painterResource(R.drawable.ic_translate),
                        onClick = { viewModel.onEnableTranslationChanged(!enableTranslation) },
                        trailingContent = {
                            AppSwitch(
                                checked = enableTranslation,
                                onCheckedChange = { viewModel.onEnableTranslationChanged(it) }
                            )
                        },
                        showDivider = true
                    )
                    SettingsItem(
                        title = stringResource(R.string.trans_source_lang),
                        description = stringResource(langMap[sourceLang] ?: R.string.lang_en),
                        icon = painterResource(R.drawable.ic_language),
                        onClick = { showSourceDialog = true },
                        showDivider = true
                    )
                    SettingsItem(
                        title = stringResource(R.string.trans_target_lang),
                        description = stringResource(langMap[targetLang] ?: R.string.lang_ru),
                        icon = painterResource(R.drawable.ic_language),
                        onClick = { showTargetDialog = true },
                        showDivider = false
                    )
                }

                Spacer(Modifier.height(paddingValues.calculateBottomPadding() + 24.dp))
            }
        }

        val renderDialog = @Composable { titleRes: Int, currentVal: String, onSelect: (String) -> Unit, onDismiss: () -> Unit ->
            AppBottomPanel(
                onDismissRequest = onDismiss,
                title = stringResource(titleRes),
                icon = painterResource(R.drawable.ic_translate)
            ) {
                Column(
                    modifier = Modifier.padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    langMap.forEach { (code, nameResId) ->
                        val state = langModelStates[code] ?: TransModelState.NOT_DOWNLOADED
                        AppSelectionItem(
                            text = stringResource(nameResId),
                            isSelected = (currentVal == code),
                            onClick = { onSelect(code) },
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                            trailingContent = {
                                if (code == "en") {
                                    AppIconButton(onClick = { }, size = 32.dp, backgroundColor = MaterialTheme.colorScheme.surfaceContainerHighest, enabled = false) {
                                        AppIcon(painterResource(R.drawable.ic_delete), modifier = Modifier.size(16.dp))
                                    }
                                } else {
                                    when (state) {
                                        TransModelState.DOWNLOADED -> {
                                            AppIconButton(onClick = { viewModel.deleteTranslationModel(code) }, size = 32.dp, backgroundColor = MaterialTheme.colorScheme.surfaceContainerHighest) {
                                                AppIcon(painterResource(R.drawable.ic_delete), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                        TransModelState.DOWNLOADING -> {
                                            Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                            }
                                        }
                                        TransModelState.NOT_DOWNLOADED -> {
                                            AppIconButton(onClick = { viewModel.downloadTranslationModel(code) }, size = 32.dp, backgroundColor = MaterialTheme.colorScheme.primaryContainer) {
                                                AppIcon(painterResource(R.drawable.ic_download), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }

        if (showSourceDialog) {
            renderDialog(R.string.trans_source_lang, sourceLang, { viewModel.onTransSourceLangChanged(it); showSourceDialog = false }, { showSourceDialog = false })
        }
        if (showTargetDialog) {
            renderDialog(R.string.trans_target_lang, targetLang, { viewModel.onTransTargetLangChanged(it); showTargetDialog = false }, { showTargetDialog = false })
        }
    }
}
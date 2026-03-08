package ru.reset.renplay.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Transition
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import ru.reset.renplay.R
import ru.reset.renplay.ui.components.layout.*
import ru.reset.renplay.ui.components.inputs.*
import ru.reset.renplay.ui.components.feedback.*
import ru.reset.renplay.ui.components.typography.*
import ru.reset.renplay.ui.components.appbar.*
import ru.reset.renplay.ui.components.icons.*
import ru.reset.renplay.ui.settings.components.*
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.reset.renplay.di.AppViewModelProvider
import ru.reset.renplay.ui.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(
    navController: NavController,
    useDynamicTheme: Boolean,
    onDynamicThemeChange: (Boolean) -> Unit,
    themeOption: ThemeOption,
    onThemeOptionChange: (ThemeOption) -> Unit,
    appLanguage: String = "system",
    onAppLanguageChange: (String) -> Unit = {},
    enableBlur: Boolean,
    onEnableBlurChange: (Boolean) -> Unit,
    transition: Transition<EnterExitState>
) {
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    val languageOptionsMap = remember {
        mapOf(
            "system" to R.string.language_system,
            "ru" to R.string.language_ru,
            "en" to R.string.language_en
        )
    }
    val currentLanguageName = stringResource(languageOptionsMap[appLanguage] ?: R.string.language_system)

    val themeOptionsMap = remember {
        mapOf(
            ThemeOption.SYSTEM to R.string.theme_system,
            ThemeOption.LIGHT to R.string.theme_light,
            ThemeOption.DARK to R.string.theme_dark,
            ThemeOption.BLACK to R.string.theme_black
        )
    }
    val currentThemeName = stringResource(themeOptionsMap[themeOption] ?: R.string.theme_system)

    val context = androidx.compose.ui.platform.LocalContext.current
    val settingsViewModel: SettingsViewModel = viewModel(viewModelStoreOwner = context as androidx.activity.ComponentActivity, factory = AppViewModelProvider.Factory)
    val useGameDetailsScreen by settingsViewModel.useGameDetailsScreen.collectAsState()
    val advancedAnimationsEnabled by settingsViewModel.advancedAnimationsEnabled.collectAsState()

    AppScaffold(
        topBar = {
            RenPlayAppBar(
                title = stringResource(R.string.nav_appearance),
                navigationIcon = {
                    AppIconButton(onClick = { navController.navigateUp() }) {
                        AppIcon(painterResource(R.drawable.ic_arrow_back), null)
                    }
                },
                titleModifier = if (advancedAnimationsEnabled) {
                    Modifier.settingsTransitionTarget(Screen.Appearance.route, MaterialTheme.typography.titleLarge.fontSize, transition)
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

            SettingsGroup(title = stringResource(R.string.group_language)) {
            SettingsItem(
                title = stringResource(R.string.item_app_language),
                description = currentLanguageName,
                icon = painterResource(id = R.drawable.ic_language),
                onClick = { showLanguageDialog = true },
                showDivider = false
            )
        }

        SettingsGroup(title = stringResource(R.string.group_theme)) {
            SettingsItem(
                title = stringResource(R.string.item_app_theme),
                description = currentThemeName,
                icon = painterResource(id = R.drawable.ic_theme),
                onClick = { showThemeDialog = true },
                showDivider = true
            )
            
            SettingsItem(
                title = stringResource(R.string.item_dynamic_colors),
                icon = painterResource(id = R.drawable.ic_format_paint),
                onClick = { onDynamicThemeChange(!useDynamicTheme) },
                trailingContent = {
                    AppSwitch(
                        checked = useDynamicTheme,
                        onCheckedChange = onDynamicThemeChange
                    )
                },
                showDivider = true
            )

            SettingsItem(
                title = stringResource(R.string.item_enable_blur),
                icon = painterResource(id = R.drawable.ic_blur_circular),
                onClick = { onEnableBlurChange(!enableBlur) },
                trailingContent = {
                    AppSwitch(
                        checked = enableBlur,
                        onCheckedChange = onEnableBlurChange
                    )
                },
                showDivider = true
            )

            SettingsItem(
                title = stringResource(R.string.setting_advanced_animations),
                description = stringResource(R.string.setting_advanced_animations_desc),
                icon = painterResource(id = R.drawable.ic_animation),
                onClick = { settingsViewModel.onAdvancedAnimationsChanged(!advancedAnimationsEnabled) },
                trailingContent = {
                    AppSwitch(
                        checked = advancedAnimationsEnabled,
                        onCheckedChange = { settingsViewModel.onAdvancedAnimationsChanged(it) }
                    )
                },
                showDivider = true
            )

            SettingsItem(
                title = stringResource(R.string.setting_use_game_details),
                description = stringResource(R.string.setting_use_game_details_desc),
                icon = painterResource(id = R.drawable.ic_view_carousel),
                onClick = { settingsViewModel.onUseGameDetailsChanged(!useGameDetailsScreen) },
                trailingContent = {
                    AppSwitch(
                        checked = useGameDetailsScreen,
                        onCheckedChange = { settingsViewModel.onUseGameDetailsChanged(it) }
                    )
                },
                showDivider = false
            )
        }
    }
    }

    if (showLanguageDialog) {
        AppBottomPanel(
            onDismissRequest = { showLanguageDialog = false },
            title = stringResource(R.string.item_app_language),
            icon = painterResource(id = R.drawable.ic_language)
        ) {
            Column(
                modifier = Modifier.padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                languageOptionsMap.forEach { (code, nameResId) ->
                    AppSelectionItem(
                        text = stringResource(nameResId),
                        isSelected = (appLanguage == code),
                        onClick = {
                            onAppLanguageChange(code)
                            showLanguageDialog = false
                        }
                    )
                }
            }
        }
    }

    if (showThemeDialog) {
        AppBottomPanel(
            onDismissRequest = { showThemeDialog = false },
            title = stringResource(R.string.item_app_theme),
            icon = painterResource(id = R.drawable.ic_theme)
        ) {
            Column(
                modifier = Modifier.padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                themeOptionsMap.forEach { (option, nameResId) ->
                    AppSelectionItem(
                        text = stringResource(nameResId),
                        isSelected = (themeOption == option),
                        onClick = {
                            onThemeOptionChange(option)
                            showThemeDialog = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AppSelectionItem(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    scope.launch {
                        scale.animateTo(0.96f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
                    }
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    scope.launch {
                        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
                    }
                }
            }
        }
    }

    val containerColor by animateColorAsState(
        targetValue = if (isSelected) 
            MaterialTheme.colorScheme.primaryContainer 
        else 
            MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = tween(durationMillis = 200),
        label = "SelectionContainer"
    )
    
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) 
            MaterialTheme.colorScheme.onPrimaryContainer 
        else 
            MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(durationMillis = 200),
        label = "SelectionContent"
    )

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        elevation = CardDefaults.cardElevation(0.dp),
        interactionSource = interactionSource
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 20.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                textAlign = TextAlign.Start
            )
        }
    }
}
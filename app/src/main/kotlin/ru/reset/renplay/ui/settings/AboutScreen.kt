package ru.reset.renplay.ui.settings

import android.content.pm.PackageInfo
import androidx.annotation.RawRes
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Transition
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.animation.animateColorAsState
import ru.reset.renplay.ui.components.feedback.LocalAppBlurState
import ru.reset.renplay.ui.components.feedback.rememberAppBlurState
import ru.reset.renplay.ui.components.feedback.appBlurEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.reset.renplay.R
import ru.reset.renplay.ui.components.layout.*
import ru.reset.renplay.ui.components.inputs.*
import ru.reset.renplay.ui.components.feedback.*
import ru.reset.renplay.ui.components.typography.*
import ru.reset.renplay.ui.components.appbar.*
import ru.reset.renplay.ui.components.icons.*
import ru.reset.renplay.ui.settings.components.*
import ru.reset.renplay.ui.navigation.Screen

data class LicenseInfo(val name: String, val copyright: String, val license: String, val url: String, @param:RawRes val textResId: Int)

private const val COPYRIGHT_AOSP = "Copyright © The Android Open Source Project"
private const val COPYRIGHT_JB = "Copyright © JetBrains s.r.o."
private const val LICENSE_APACHE_2 = "Apache License 2.0"

private val libraryData = listOf(
    LicenseInfo("Android Jetpack & Compose", COPYRIGHT_AOSP, LICENSE_APACHE_2, "https://source.android.com/license", R.raw.apache_2_0),
    LicenseInfo("Kotlin & Kotlinx", COPYRIGHT_JB, LICENSE_APACHE_2, "https://github.com/Kotlin/kotlinx.serialization", R.raw.apache_2_0),
    LicenseInfo("Ren'Py Visual Novel Engine", "Copyright © Tom Rothamel", "MIT License", "https://www.renpy.org/doc/html/license.html", R.raw.mit),
    LicenseInfo("Python", "Copyright © Python Software Foundation", "PSF License", "https://docs.python.org/3/license.html", R.raw.psf),
    LicenseInfo("SDL2", "Copyright © Sam Lantinga", "zlib License", "https://libsdl.org/license.php", R.raw.zlib),
    LicenseInfo("JTar", "Copyright © Kamran Zafar", LICENSE_APACHE_2, "https://github.com/kamranzafar/jtar", R.raw.apache_2_0),
    LicenseInfo("Material Design Icons", "Copyright © Google LLC", LICENSE_APACHE_2, "https://developers.google.com/fonts/faq", R.raw.apache_2_0),
    LicenseInfo("OpenDyslexic", "Copyright © Abbie Gonzalez", "SIL Open Font License 1.1", "https://opendyslexic.org/", R.raw.ofl_1_1),
    LicenseInfo("DejaVu Fonts", "Copyright © Bitstream, Inc.", "Bitstream Vera License", "https://dejavu-fonts.github.io/", R.raw.bitstream),
    LicenseInfo("Twemoji", "Copyright © Twitter, Inc and other contributors", "CC-BY 4.0 / MIT License", "https://github.com/twitter/twemoji", R.raw.cc_by_4_0),
    LicenseInfo("PyJNIus", "Copyright © Kivy Team and other contributors", "MIT License", "https://github.com/kivy/pyjnius", R.raw.mit),
    LicenseInfo("SDL_GameControllerDB", "Copyright © Gabriel Jacobo", "zlib License", "https://github.com/mdqinc/SDL_GameControllerDB", R.raw.zlib),
    LicenseInfo("Ren'Py Third-Party Dependencies", "Copyright © Various Authors", "LGPL / Mixed", "https://www.renpy.org/doc/html/license.html", R.raw.renpy_disclaimer),
    LicenseInfo("FFmpeg", "Copyright © FFmpeg developers", "LGPL v2.1+", "https://ffmpeg.org/", R.raw.lgpl_2_1),
    LicenseInfo("FreeType", "Copyright © The FreeType Project", "Zlib License", "https://freetype.org/", R.raw.zlib),
    LicenseInfo("HarfBuzz", "Copyright © Behdad Esfahbod and contributors", "Old MIT License", "https://harfbuzz.github.io/", R.raw.mit),
    LicenseInfo("libpng", "Copyright © Glenn Randers-Pehrson and contributors", "PNG License", "http://www.libpng.org/", R.raw.libpng),
    LicenseInfo("libjpeg-turbo", "Copyright © The libjpeg-turbo Project", "IJG License", "https://libjpeg-turbo.org/", R.raw.ijg),
    LicenseInfo("libwebp", "Copyright © Google LLC", "Libwebp Licenses", "https://developers.google.com/speed/webp/", R.raw.libwebp_license),
    LicenseInfo("OneUI Icons", "Copyright © 2022 Yanndroid & BlackMesa123", "MIT License", "https://github.com/OneUIProject/oneui-icons", R.raw.mit),
    LicenseInfo("OneUI Design (Original Base)", "Copyright © 2022 Yanndroid & BlackMesa123", "MIT License", "https://github.com/OneUIProject/oneui-design", R.raw.mit),
    LicenseInfo("OneUI Design & SESL (Tribalfs Fork)", "Copyright © 2024 Tribalfs", "MIT License", "https://github.com/tribalfs/oneui-design", R.raw.mit),
    LicenseInfo("unrpyc", "Copyright © 2012-2024 Yuri K. Schlesner, CensoredUsername, Jackmcbarn", "MIT License", "https://github.com/CensoredUsername/unrpyc", R.raw.unrpyc)
).sortedBy { it.name.lowercase() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    navController: NavController,
    transition: Transition<EnterExitState>
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settingsViewModel: ru.reset.renplay.ui.settings.SettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        viewModelStoreOwner = context as androidx.activity.ComponentActivity, 
        factory = ru.reset.renplay.di.AppViewModelProvider.Factory
    )
    val advancedAnimationsEnabled by settingsViewModel.advancedAnimationsEnabled.collectAsState()

    val uriHandler = LocalUriHandler.current
    val scrollState = rememberScrollState()
    val scrollProgress by remember { derivedStateOf { (scrollState.value / 80f).coerceIn(0f, 1f) } }
    val blurActive = LocalAppBlurState.current.blurEnabled
    val screenBlurState = rememberAppBlurState()
    screenBlurState.blurEnabled = blurActive
    val buttonBgColor = if (blurActive) Color.Transparent else androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0f), MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f), scrollProgress)

    var showLicensesDialog by remember { mutableStateOf(false) }

    val logoScale = remember { Animatable(0f) }
    val logoAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { logoScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) }
        launch { logoAlpha.animateTo(1f, tween(durationMillis = 300)) }
    }

    CompositionLocalProvider(LocalAppBlurState provides screenBlurState) {
        AppScaffold(
            topBar = {
            RenPlayAppBar(
                title = stringResource(R.string.nav_about),
                scrollProgress = scrollProgress,
                navigationIcon = {
                    AppIconButton(
                        onClick = { navController.navigateUp() },
                        backgroundColor = buttonBgColor,
                        shape = androidx.compose.foundation.shape.CircleShape,
                        modifier = Modifier.appBlurEffect(
                            state = screenBlurState,
                            shape = androidx.compose.foundation.shape.CircleShape,
                            blurRadius = 16.dp * scrollProgress,
                            tint = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f * scrollProgress),
                            forceInvalidation = true
                        )
                    ) {
                        AppIcon(painterResource(R.drawable.ic_arrow_back), null)
                    }
                },
                titleModifier = if (advancedAnimationsEnabled) {
                    Modifier.settingsTransitionTarget(Screen.About.route, MaterialTheme.typography.titleLarge.fontSize, transition)
                } else Modifier
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .appBlurSource(screenBlurState)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(paddingValues.calculateTopPadding() + 56.dp + 40.dp))

            Image(
                painter = painterResource(R.drawable.ic_launcher),
                contentDescription = null,
                modifier = Modifier
                    .size(110.dp)
                    .padding(bottom = 16.dp)
                    .graphicsLayer {
                        scaleX = logoScale.value
                        scaleY = logoScale.value
                        alpha = logoAlpha.value
                    }
            )

            AppText(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = CircleShape
            ) {
                AppText(
                    text = stringResource(R.string.version_prefix, getAppVersion()),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            SettingsGroup(title = stringResource(R.string.group_resources)) {
                SettingsItem(
                    title = stringResource(R.string.item_github),
                    description = stringResource(R.string.desc_github),
                    icon = painterResource(R.drawable.ic_github),
                    onClick = { uriHandler.openUri("https://github.com/Reset171/RenPlay") },
                    showDivider = true
                )
                SettingsItem(
                    title = stringResource(R.string.item_licenses),
                    description = stringResource(R.string.desc_licenses),
                    icon = painterResource(R.drawable.ic_info),
                    onClick = { showLicensesDialog = true },
                    showDivider = false
                )
            }

            SettingsGroup(title = stringResource(R.string.group_development)) {
                SettingsItem(
                    title = stringResource(R.string.author_name),
                    description = null,
                    icon = painterResource(R.drawable.ic_person),
                    onClick = { uriHandler.openUri("https://github.com/Reset171") },
                    showDivider = false
                )
            }

            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.height(paddingValues.calculateBottomPadding() + 24.dp))
        }
    }

    if (showLicensesDialog) {
        var selectedLicense by remember { mutableStateOf<LicenseInfo?>(null) }
        var licenseText by remember { mutableStateOf("") }

        LaunchedEffect(selectedLicense) {
            val resId = selectedLicense?.textResId
            if (resId != null) {
                licenseText = withContext(Dispatchers.IO) {
                    context.resources.openRawResource(resId).bufferedReader().use { it.readText() }
                }
            } else {
                licenseText = ""
            }
        }

        AppBottomPanel(
            onDismissRequest = { showLicensesDialog = false },
            title = stringResource(R.string.licenses_title),
            icon = painterResource(R.drawable.ic_info)
        ) {
            Column(
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                libraryData.forEach { license ->
                    AppCard(
                        onClick = { uriHandler.openUri(license.url) },
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        elevation = 0.dp,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(16.dp)) {
                                AppText(
                                    text = license.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(2.dp))
                                AppText(
                                    text = license.copyright,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                                Spacer(Modifier.height(6.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                                ) {
                                    AppText(
                                        text = license.license,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            AppIconButton(
                                onClick = { selectedLicense = license },
                                modifier = Modifier.padding(end = 16.dp),
                                backgroundColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                size = 42.dp,
                                iconSize = 20.dp
                            ) {
                                AppIcon(painterResource(R.drawable.ic_copyright), null)
                            }
                        }
                    }
                }
            }
        }

        if (selectedLicense != null) {
            AppBottomPanel(
                onDismissRequest = { 
                    selectedLicense = null 
                    licenseText = ""
                },
                title = selectedLicense!!.license,
                icon = painterResource(R.drawable.ic_copyright)
            ) {
                androidx.compose.foundation.text.selection.SelectionContainer {
                    AppText(
                        text = licenseText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 450.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 16.dp)
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun getAppVersion(): String {
    val context = LocalContext.current
    return try {
        val packageInfo: PackageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        packageInfo.versionName ?: "1.0"
    } catch (e: Exception) {
        "Unknown"
    }
}

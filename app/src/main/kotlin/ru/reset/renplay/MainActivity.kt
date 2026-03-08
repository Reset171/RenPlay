package ru.reset.renplay

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.reset.renplay.R
import ru.reset.renplay.di.AppViewModelProvider
import ru.reset.renplay.ui.settings.SettingsViewModel
import ru.reset.renplay.ui.settings.ThemeOption
import ru.reset.renplay.ui.theme.MyComposeApplicationTheme
import ru.reset.renplay.ui.navigation.AppNavGraph
import java.util.Locale

import ru.reset.renplay.ui.components.feedback.LocalAppBlurState
import ru.reset.renplay.ui.components.feedback.rememberAppBlurState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent?.action == "ru.reset.renplay.LAUNCH_GAME") {
            val gamePath = intent.getStringExtra("GAME_PATH")
            if (gamePath != null) {
                val gameIntent = Intent(this, org.renpy.android.PythonSDLActivity::class.java).apply {
                    putExtra("GAME_PATH", gamePath)
                }
                startActivity(gameIntent)
                finish()
                return
            }
        }

        setContent {
            val context = LocalContext.current
            val settingsViewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory)
            
            val useDynamicTheme by settingsViewModel.useDynamicTheme.collectAsState()
            val themeOption by settingsViewModel.themeOption.collectAsState()
            val appLanguage by settingsViewModel.appLanguage.collectAsState()
            val enableBlur by settingsViewModel.enableBlur.collectAsState()

            LaunchedEffect(appLanguage) {
                val currentLocale = context.resources.configuration.locales[0]
                val targetLocale = if (appLanguage == "system") {
                    Resources.getSystem().configuration.locales[0]
                } else {
                    Locale.forLanguageTag(appLanguage)
                }

                if (currentLocale.language != targetLocale.language) {
                    updateLocale(context, targetLocale)
                    (context as? Activity)?.recreate()
                }
            }

            MyComposeApplicationTheme(
                dynamicColor = useDynamicTheme,
                themeOption = themeOption 
            ) {
                val onDynamicThemeChange = { enabled: Boolean -> settingsViewModel.onDynamicThemeChanged(enabled) }
                val onThemeOptionChange = { option: ThemeOption -> settingsViewModel.onThemeOptionChanged(option) }
                val onAppLanguageChange = { code: String -> settingsViewModel.onAppLanguageChanged(code) }
                val onEnableBlurChange = { enabled: Boolean -> settingsViewModel.onEnableBlurChanged(enabled) }
                
                MainScreen(
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
        }
    }

    private fun updateLocale(context: Context, locale: Locale) {
        Locale.setDefault(locale)
        val resources = context.resources
        val configuration = resources.configuration
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        @Suppress("DEPRECATION")
        resources.updateConfiguration(configuration, resources.displayMetrics)
    }
}

fun checkPermission(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true
}

fun requestPermission(context: Context, launcher: ActivityResultLauncher<Intent>) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = Uri.fromParts("package", context.packageName, null)
            launcher.launch(intent)
        } catch (e: Exception) {
            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            launcher.launch(intent)
        }
    }
}

@Composable
fun MainScreen(
    useDynamicTheme: Boolean,
    onDynamicThemeChange: (Boolean) -> Unit,
    themeOption: ThemeOption,
    onThemeOptionChange: (ThemeOption) -> Unit,
    appLanguage: String,
    onAppLanguageChange: (String) -> Unit,
    enableBlur: Boolean,
    onEnableBlurChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var hasFilePermission by remember { mutableStateOf(checkPermission()) }
    val filePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        hasFilePermission = checkPermission()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val appBlurState = rememberAppBlurState()
    appBlurState.blurEnabled = enableBlur

    CompositionLocalProvider(LocalAppBlurState provides appBlurState) {
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
            if (!hasFilePermission) {
                PermissionRequestUI { requestPermission(context, filePermissionLauncher) }
            } else {
                AppNavGraph( 
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
        }
    }
}

@Composable
fun PermissionRequestUI(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.perm_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 16.dp))
        Text(stringResource(R.string.perm_desc), textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 24.dp))
        Button(onClick = onRequest) { Text(stringResource(R.string.perm_button)) }
    }
}

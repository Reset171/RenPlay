package ru.reset.renplay.ui.settings

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val KEY_DYNAMIC_THEME = "use_dynamic_theme"
private const val KEY_THEME_OPTION = "theme_option"
private const val KEY_APP_LANGUAGE = "app_language"
private const val KEY_ENABLE_BLUR = "enable_blur"
private const val KEY_USE_GAME_DETAILS = "use_game_details"
private const val KEY_ADVANCED_ANIMATIONS = "advanced_animations"

private const val KEY_HW_VIDEO = "hw_video"
private const val KEY_PHONE_VARIANT = "phone_variant"
private const val KEY_MODEL_RENDERING = "model_rendering"
private const val KEY_FORCE_RECOMPILE = "force_recompile"
private const val KEY_ENABLE_TRANSLATION = "enable_translation"

enum class ThemeOption {
    SYSTEM, LIGHT, DARK, BLACK
}

enum class UiStyle {
    MATERIAL3, ONEUI
}

enum class TransModelState { NOT_DOWNLOADED, DOWNLOADING, DOWNLOADED }

class SettingsViewModel(private val prefs: SharedPreferences, private val engineManager: ru.reset.renplay.domain.EngineManager) : ViewModel() {

    val engines = kotlinx.coroutines.flow.MutableStateFlow<List<ru.reset.renplay.domain.models.EnginePlugin>>(emptyList())

    fun loadEngines() {
        engines.value = engineManager.getInstalledEngines()
    }

    fun installEngine(uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            engineManager.installEngine(uri)
            val list = engineManager.getInstalledEngines()
            withContext(Dispatchers.Main) {
                engines.value = list
            }
        }
    }

    fun deleteEngine(version: String) {
        engineManager.deleteEngine(version)
        loadEngines()
    }

    private val _uiStyle = MutableStateFlow(
        try {
            UiStyle.valueOf(prefs.getString("ui_style", UiStyle.MATERIAL3.name)!!)
        } catch (e: IllegalArgumentException) {
            UiStyle.MATERIAL3
        }
    )
    val uiStyle = _uiStyle.asStateFlow()

    fun onUiStyleChanged(style: UiStyle) {
        _uiStyle.value = style
        prefs.edit().putString("ui_style", style.name).apply()
    }

    private val _useDynamicTheme = MutableStateFlow(prefs.getBoolean(KEY_DYNAMIC_THEME, true))
    val useDynamicTheme = _useDynamicTheme.asStateFlow()

    fun onDynamicThemeChanged(enabled: Boolean) {
        _useDynamicTheme.value = enabled
        prefs.edit().putBoolean(KEY_DYNAMIC_THEME, enabled).apply()
    }

    private val _themeOption = MutableStateFlow(
        try {
            ThemeOption.valueOf(prefs.getString(KEY_THEME_OPTION, ThemeOption.SYSTEM.name)!!)
        } catch (e: IllegalArgumentException) {
            ThemeOption.SYSTEM
        }
    )
    val themeOption = _themeOption.asStateFlow()

    fun onThemeOptionChanged(option: ThemeOption) {
        _themeOption.value = option
        prefs.edit().putString(KEY_THEME_OPTION, option.name).apply()
    }

    private val _appLanguage = MutableStateFlow(prefs.getString(KEY_APP_LANGUAGE, "system")!!)
    val appLanguage = _appLanguage.asStateFlow()

    fun onAppLanguageChanged(code: String) {
        _appLanguage.value = code
        prefs.edit().putString(KEY_APP_LANGUAGE, code).apply()
    }

    private val _enableBlur = MutableStateFlow(prefs.getBoolean(KEY_ENABLE_BLUR, true))
    val enableBlur = _enableBlur.asStateFlow()

    fun onEnableBlurChanged(enabled: Boolean) {
        _enableBlur.value = enabled
        prefs.edit().putBoolean(KEY_ENABLE_BLUR, enabled).apply()
    }

    private val _useGameDetailsScreen = MutableStateFlow(prefs.getBoolean(KEY_USE_GAME_DETAILS, false))
    val useGameDetailsScreen = _useGameDetailsScreen.asStateFlow()

    fun onUseGameDetailsChanged(enabled: Boolean) {
        _useGameDetailsScreen.value = enabled
        prefs.edit().putBoolean(KEY_USE_GAME_DETAILS, enabled).apply()
    }

    private val _advancedAnimationsEnabled = MutableStateFlow(prefs.getBoolean(KEY_ADVANCED_ANIMATIONS, true))
    val advancedAnimationsEnabled = _advancedAnimationsEnabled.asStateFlow()

    fun onAdvancedAnimationsChanged(enabled: Boolean) {
        _advancedAnimationsEnabled.value = enabled
        prefs.edit().putBoolean(KEY_ADVANCED_ANIMATIONS, enabled).apply()
    }

    private val _hwVideoEnabled = MutableStateFlow(prefs.getBoolean(KEY_HW_VIDEO, true))
    val hwVideoEnabled = _hwVideoEnabled.asStateFlow()

    fun onHwVideoChanged(enabled: Boolean) {
        _hwVideoEnabled.value = enabled
        prefs.edit().putBoolean(KEY_HW_VIDEO, enabled).apply()
    }

    private val _phoneVariantEnabled = MutableStateFlow(prefs.getBoolean(KEY_PHONE_VARIANT, false))
    val phoneVariantEnabled = _phoneVariantEnabled.asStateFlow()

    fun onPhoneVariantChanged(enabled: Boolean) {
        _phoneVariantEnabled.value = enabled
        prefs.edit().putBoolean(KEY_PHONE_VARIANT, enabled).apply()
    }

    private val _modelRenderingEnabled = MutableStateFlow(prefs.getBoolean(KEY_MODEL_RENDERING, true))
    val modelRenderingEnabled = _modelRenderingEnabled.asStateFlow()

    fun onModelRenderingChanged(enabled: Boolean) {
        _modelRenderingEnabled.value = enabled
        prefs.edit().putBoolean(KEY_MODEL_RENDERING, enabled).apply()
    }

    private val _forceRecompileEnabled = MutableStateFlow(prefs.getBoolean(KEY_FORCE_RECOMPILE, false))
    val forceRecompileEnabled = _forceRecompileEnabled.asStateFlow()

    fun onForceRecompileChanged(enabled: Boolean) {
        _forceRecompileEnabled.value = enabled
        prefs.edit().putBoolean(KEY_FORCE_RECOMPILE, enabled).apply()
    }

    private val _enableTranslation = MutableStateFlow(prefs.getBoolean(KEY_ENABLE_TRANSLATION, false))
    val enableTranslation = _enableTranslation.asStateFlow()

    fun onEnableTranslationChanged(enabled: Boolean) {
        _enableTranslation.value = enabled
        prefs.edit().putBoolean(KEY_ENABLE_TRANSLATION, enabled).apply()
    }

    private val _transSourceLang = MutableStateFlow(prefs.getString("trans_source", "en") ?: "en")
    val transSourceLang = _transSourceLang.asStateFlow()

    private val _transTargetLang = MutableStateFlow(prefs.getString("trans_target", "ru") ?: "ru")
    val transTargetLang = _transTargetLang.asStateFlow()

    private val _langModelStates = MutableStateFlow<Map<String, TransModelState>>(emptyMap())
    val langModelStates = _langModelStates.asStateFlow()

    val sourceModelState = combine(_transSourceLang, _langModelStates) { lang, states ->
        states[lang] ?: TransModelState.NOT_DOWNLOADED
    }.stateIn(viewModelScope, SharingStarted.Lazily, TransModelState.NOT_DOWNLOADED)

    val targetModelState = combine(_transTargetLang, _langModelStates) { lang, states ->
        states[lang] ?: TransModelState.NOT_DOWNLOADED
    }.stateIn(viewModelScope, SharingStarted.Lazily, TransModelState.NOT_DOWNLOADED)

    init {
        checkTranslationModels()
    }

    fun onTransSourceLangChanged(lang: String) {
        _transSourceLang.value = lang
        prefs.edit().putString("trans_source", lang).apply()
    }

    fun onTransTargetLangChanged(lang: String) {
        _transTargetLang.value = lang
        prefs.edit().putString("trans_target", lang).apply()
    }

    fun checkTranslationModels() {
        ru.reset.renplay.utils.RenPlayTranslator.getDownloadedTranslateModels { downloadedLangs ->
            val currentStates = _langModelStates.value.toMutableMap()
            val supportedLangs = listOf("en", "ru", "ja", "zh", "ko", "es", "fr", "de")
            supportedLangs.forEach { lang ->
                if (currentStates[lang] != TransModelState.DOWNLOADING) {
                    currentStates[lang] = if (downloadedLangs.contains(lang)) TransModelState.DOWNLOADED else TransModelState.NOT_DOWNLOADED
                }
            }
            _langModelStates.value = currentStates
        }
    }

    fun downloadTranslationModel(lang: String) {
        _langModelStates.value = _langModelStates.value.toMutableMap().apply { put(lang, TransModelState.DOWNLOADING) }
        ru.reset.renplay.utils.RenPlayTranslator.downloadModel(
            lang,
            onSuccess = {
                _langModelStates.value = _langModelStates.value.toMutableMap().apply { put(lang, TransModelState.DOWNLOADED) }
            },
            onError = {
                _langModelStates.value = _langModelStates.value.toMutableMap().apply { put(lang, TransModelState.NOT_DOWNLOADED) }
            }
        )
    }

    fun deleteTranslationModel(lang: String) {
        ru.reset.renplay.utils.RenPlayTranslator.deleteModel(lang) {
            _langModelStates.value = _langModelStates.value.toMutableMap().apply { put(lang, TransModelState.NOT_DOWNLOADED) }
        }
    }

    fun downloadSourceModel() = downloadTranslationModel(_transSourceLang.value)
    fun downloadTargetModel() = downloadTranslationModel(_transTargetLang.value)
    fun deleteSourceModel() = deleteTranslationModel(_transSourceLang.value)
    fun deleteTargetModel() = deleteTranslationModel(_transTargetLang.value)
}


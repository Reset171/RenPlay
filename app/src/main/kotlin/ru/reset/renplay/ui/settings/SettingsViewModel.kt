package ru.reset.renplay.ui.settings

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

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

enum class ThemeOption {
    SYSTEM, LIGHT, DARK, BLACK
}

enum class UiStyle {
    MATERIAL3, ONEUI
}

class SettingsViewModel(private val prefs: SharedPreferences) : ViewModel() {

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
}


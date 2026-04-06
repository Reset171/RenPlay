package ru.reset.renplay.ui.oneui.settings

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import dev.oneuiproject.oneui.preference.HorizontalRadioPreference
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import ru.reset.renplay.R
import ru.reset.renplay.di.AppViewModelProvider
import ru.reset.renplay.ui.settings.SettingsViewModel
import ru.reset.renplay.ui.settings.ThemeOption
import ru.reset.renplay.ui.settings.UiStyle
import ru.reset.renplay.utils.PREFS_NAME

class OneUiSettingsFragment : PreferenceFragmentCompat() {

    private lateinit var viewModel: SettingsViewModel

    private val zipPickerLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val path = result.data?.getStringExtra("selectedPath")
            if (path != null) {
                viewModel.installEngine(android.net.Uri.fromFile(java.io.File(path)))
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = PREFS_NAME
        viewModel = ViewModelProvider(requireActivity(), AppViewModelProvider.Factory)[SettingsViewModel::class.java]
        setPreferencesFromResource(R.xml.oneui_preferences, rootKey)

        findPreference<HorizontalRadioPreference>("darkMode")?.apply {
            value = when (viewModel.themeOption.value) {
                ThemeOption.LIGHT -> "0"
                ThemeOption.DARK, ThemeOption.BLACK -> "1"
                else -> "0"
            }
            setOnPreferenceChangeListener { _, newValue ->
                val isDark = newValue.toString() == "1"
                val newTheme = if (isDark) ThemeOption.DARK else ThemeOption.LIGHT
                viewModel.onThemeOptionChanged(newTheme)
                true
            }
        }

        findPreference<SwitchPreferenceCompat>("darkModeAuto")?.apply {
            isChecked = viewModel.themeOption.value == ThemeOption.SYSTEM
            setOnPreferenceChangeListener { _, newValue ->
                val isAuto = newValue as Boolean
                if (isAuto) {
                    viewModel.onThemeOptionChanged(ThemeOption.SYSTEM)
                } else {
                    val radio = findPreference<HorizontalRadioPreference>("darkMode")
                    val isDark = radio?.value == "1"
                    val newTheme = if (isDark) ThemeOption.DARK else ThemeOption.LIGHT
                    viewModel.onThemeOptionChanged(newTheme)
                }
                true
            }
        }

        findPreference<androidx.preference.DropDownPreference>("ui_style")?.apply {
            value = viewModel.uiStyle.value.name
            setOnPreferenceChangeListener { _, newValue ->
                val newStyle = UiStyle.valueOf(newValue.toString())
                viewModel.onUiStyleChanged(newStyle)
                val intent = android.content.Intent(requireContext(), ru.reset.renplay.MainActivity::class.java)
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                startActivity(intent)
                true
            }
        }

        findPreference<SwitchPreferenceCompat>("use_dynamic_theme")?.apply {
            isVisible = false
        }

        findPreference<SwitchPreferenceCompat>("hw_video")?.apply {
            isChecked = viewModel.hwVideoEnabled.value
            setOnPreferenceChangeListener { _, newValue ->
                viewModel.onHwVideoChanged(newValue as Boolean)
                true
            }
        }

        findPreference<SwitchPreferenceCompat>("phone_variant")?.apply {
            isChecked = viewModel.phoneVariantEnabled.value
            setOnPreferenceChangeListener { _, newValue ->
                viewModel.onPhoneVariantChanged(newValue as Boolean)
                true
            }
        }

        findPreference<SwitchPreferenceCompat>("model_rendering")?.apply {
            isChecked = viewModel.modelRenderingEnabled.value
            setOnPreferenceChangeListener { _, newValue ->
                viewModel.onModelRenderingChanged(newValue as Boolean)
                true
            }
        }

        findPreference<SwitchPreferenceCompat>("force_recompile")?.apply {
            isChecked = viewModel.forceRecompileEnabled.value
            setOnPreferenceChangeListener { _, newValue ->
                viewModel.onForceRecompileChanged(newValue as Boolean)
                true
            }
        }

        viewModel.loadEngines()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.themeOption.collectLatest { theme ->
                        val mode = when (theme) {
                            ThemeOption.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                            ThemeOption.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                            ThemeOption.DARK, ThemeOption.BLACK -> AppCompatDelegate.MODE_NIGHT_YES
                        }
                        AppCompatDelegate.setDefaultNightMode(mode)
                    }
                }

                launch {
                    viewModel.engines.collectLatest { engines ->
                        val category = findPreference<androidx.preference.PreferenceCategory>("category_engine_management")
                        category?.removeAll()

                        engines.forEach { engine ->
                            val pref = androidx.preference.Preference(requireContext()).apply {
                                key = "engine_${engine.version}"
                                title = getString(R.string.engine_version_format, engine.version)
                                summary = getString(R.string.engine_installed_click_to_delete)
                                setOnPreferenceClickListener {
                                    androidx.appcompat.app.AlertDialog.Builder(requireContext())
                                        .setTitle(R.string.engine_delete_title)
                                        .setMessage(getString(R.string.engine_delete_confirm, engine.version))
                                        .setPositiveButton(R.string.engine_delete_btn) { _, _ ->
                                            viewModel.deleteEngine(engine.version)
                                        }
                                        .setNegativeButton(R.string.engine_cancel_btn, null)
                                        .show()
                                    true
                                }
                            }
                            category?.addPreference(pref)
                        }

                        val installPref = androidx.preference.Preference(requireContext()).apply {
                            key = "install_engine_zip"
                            title = getString(R.string.engine_install_zip)
                            summary = getString(R.string.engine_install_zip_desc)
                            setIcon(dev.oneuiproject.oneui.R.drawable.ic_oui_file_type_zip)
                            setOnPreferenceClickListener {
                                val intent = android.content.Intent(requireContext(), ru.reset.renplay.ui.oneui.picker.OneUiFolderPickerActivity::class.java).apply {
                                    putExtra("mode", "zip")
                                }
                                zipPickerLauncher.launch(intent)
                                true
                            }
                        }
                        category?.addPreference(installPref)
                    }
                }
            }
        }
    }
}

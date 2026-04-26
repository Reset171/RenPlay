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
import ru.reset.renplay.utils.applyCrossWindowBlur

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

        findPreference<SwitchPreferenceCompat>("enable_translation")?.apply {
            isChecked = viewModel.enableTranslation.value
            setOnPreferenceChangeListener { _, newValue ->
                viewModel.onEnableTranslationChanged(newValue as Boolean)
                true
            }
        }

        findPreference<androidx.preference.Preference>("trans_source")?.apply {
            setOnPreferenceClickListener {
                showLanguageDialog(isSource = true)
                true
            }
        }

        findPreference<androidx.preference.Preference>("trans_target")?.apply {
            setOnPreferenceClickListener {
                showLanguageDialog(isSource = false)
                true
            }
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

    private fun showLanguageDialog(isSource: Boolean) {
        val container = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(0, 16, 0, 16)
        }
        val scrollView = androidx.core.widget.NestedScrollView(requireContext()).apply {
            addView(container)
        }

        val langValues = resources.getStringArray(R.array.trans_lang_values)
        val langNames = resources.getStringArray(R.array.trans_lang_entries)
        val rowViews = mutableMapOf<String, android.view.View>()

        val typedValue = android.util.TypedValue()
        requireContext().theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimaryDark, typedValue, true)
        val colorPrimary = typedValue.data
        val colorError = android.graphics.Color.parseColor("#E75656")

        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(if (isSource) R.string.trans_source_lang else R.string.trans_target_lang)
            .setView(scrollView)
            .setNegativeButton(dev.oneuiproject.oneui.design.R.string.oui_des_common_cancel, null)
            .create()

        for (i in langValues.indices) {
            val code = langValues[i]
            val name = langNames[i]
            val row = android.view.LayoutInflater.from(requireContext()).inflate(R.layout.oneui_item_language_row, container, false)
            rowViews[code] = row

            val tvTitle = row.findViewById<android.widget.TextView>(R.id.title)
            val btnContainer = row.findViewById<android.widget.FrameLayout>(R.id.btn_action_container)
            tvTitle.text = name

            row.setOnClickListener {
                if (isSource) viewModel.onTransSourceLangChanged(code)
                else viewModel.onTransTargetLangChanged(code)
                dialog.dismiss()
            }

            btnContainer.setOnClickListener {
                val currentState = viewModel.langModelStates.value[code] ?: ru.reset.renplay.ui.settings.TransModelState.NOT_DOWNLOADED
                when (currentState) {
                    ru.reset.renplay.ui.settings.TransModelState.NOT_DOWNLOADED -> viewModel.downloadTranslationModel(code)
                    ru.reset.renplay.ui.settings.TransModelState.DOWNLOADED -> viewModel.deleteTranslationModel(code)
                    ru.reset.renplay.ui.settings.TransModelState.DOWNLOADING -> {}
                }
            }
            container.addView(row)
        }

        val job = viewLifecycleOwner.lifecycleScope.launch {
            launch {
                val activeLangFlow = if (isSource) viewModel.transSourceLang else viewModel.transTargetLang
                activeLangFlow.collectLatest { selectedLang ->
                    langValues.forEach { code ->
                        val row = rowViews[code]
                        val radio = row?.findViewById<android.widget.RadioButton>(R.id.radio_button)
                        val tvTitle = row?.findViewById<android.widget.TextView>(R.id.title)
                        val isSelected = code == selectedLang
                        radio?.isChecked = isSelected
                        if (isSelected) {
                            tvTitle?.typeface = dev.oneuiproject.oneui.utils.getSemiBoldFont()
                        } else {
                            tvTitle?.typeface = dev.oneuiproject.oneui.utils.getRegularFont()
                        }
                    }
                }
            }
            launch {
                viewModel.langModelStates.collectLatest { states ->
                    langValues.forEach { code ->
                        val row = rowViews[code]
                        val btnAction = row?.findViewById<android.widget.ImageView>(R.id.btn_action)
                        val progressBar = row?.findViewById<android.view.View>(R.id.progress_bar)
                        val state = states[code] ?: ru.reset.renplay.ui.settings.TransModelState.NOT_DOWNLOADED

                        when (state) {
                            ru.reset.renplay.ui.settings.TransModelState.NOT_DOWNLOADED -> {
                                btnAction?.visibility = android.view.View.VISIBLE
                                progressBar?.visibility = android.view.View.GONE
                                btnAction?.setImageResource(dev.oneuiproject.oneui.R.drawable.ic_oui_download)
                                btnAction?.imageTintList = android.content.res.ColorStateList.valueOf(colorPrimary)
                            }
                            ru.reset.renplay.ui.settings.TransModelState.DOWNLOADING -> {
                                btnAction?.visibility = android.view.View.GONE
                                progressBar?.visibility = android.view.View.VISIBLE
                            }
                            ru.reset.renplay.ui.settings.TransModelState.DOWNLOADED -> {
                                btnAction?.visibility = android.view.View.VISIBLE
                                progressBar?.visibility = android.view.View.GONE
                                btnAction?.setImageResource(dev.oneuiproject.oneui.R.drawable.ic_oui_delete_outline)
                                btnAction?.imageTintList = android.content.res.ColorStateList.valueOf(colorError)
                            }
                        }
                    }
                }
            }
        }

        dialog.setOnDismissListener {
            job.cancel()
        }

        dialog.show()
        dialog.window?.decorView?.findViewById<android.view.View>(androidx.appcompat.R.id.parentPanel)?.let {
            dialog.applyCrossWindowBlur(requireContext(), it)
        }
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

                val langValues = resources.getStringArray(R.array.trans_lang_values)
                val langNames = resources.getStringArray(R.array.trans_lang_entries)

                launch {
                    viewModel.transSourceLang.collectLatest { lang ->
                        val index = langValues.indexOf(lang)
                        if (index != -1) {
                            findPreference<androidx.preference.Preference>("trans_source")?.summary = langNames[index]
                        }
                    }
                }

                launch {
                    viewModel.transTargetLang.collectLatest { lang ->
                        val index = langValues.indexOf(lang)
                        if (index != -1) {
                            findPreference<androidx.preference.Preference>("trans_target")?.summary = langNames[index]
                        }
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

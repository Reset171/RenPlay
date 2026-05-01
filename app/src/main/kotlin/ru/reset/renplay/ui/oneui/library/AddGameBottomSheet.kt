package ru.reset.renplay.ui.oneui.library

import android.app.Application
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import dev.oneuiproject.oneui.app.SemBottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.reset.renplay.R
import ru.reset.renplay.di.AppViewModelProvider
import ru.reset.renplay.ui.library.LibraryViewModel
import ru.reset.renplay.utils.applyCrossWindowBlur
import java.io.File

class AddGameBottomSheet : SemBottomSheetDialogFragment(R.layout.oneui_bottomsheet_add_game) {

    override fun onStart() {
        super.onStart()
        view?.rootView?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let {
            dialog?.applyCrossWindowBlur(requireContext(), it)
        }
        @Suppress("DEPRECATION")
        dialog?.window?.decorView?.apply {
            systemUiVisibility = systemUiVisibility and View.SYSTEM_UI_FLAG_FULLSCREEN.inv() and View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY.inv()
        }
    }

    companion object {
        fun newInstance(gamePath: String): AddGameBottomSheet {
            return AddGameBottomSheet().apply {
                arguments = Bundle().apply { putString("gamePath", gamePath) }
            }
        }
    }

    private var customIconPath: String? = null
    private var ivIconPreview: ImageView? = null
    private lateinit var libraryViewModel: LibraryViewModel

    private val iconPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val path = result.data?.getStringExtra("selectedPath")
            if (path != null) {
                customIconPath = path
                ivIconPreview?.setImageBitmap(BitmapFactory.decodeFile(customIconPath))
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        libraryViewModel = ViewModelProvider(requireActivity(), AppViewModelProvider.Factory)[LibraryViewModel::class.java]
        val gamePath = requireArguments().getString("gamePath") ?: return

        val etName = view.findViewById<EditText>(R.id.et_name)
        val etVersion = view.findViewById<EditText>(R.id.et_version)
        ivIconPreview = view.findViewById<ImageView>(R.id.iv_icon_preview)

        view.findViewById<Button>(R.id.btn_pick_icon).setOnClickListener {
            val intent = android.content.Intent(requireContext(), ru.reset.renplay.ui.oneui.picker.OneUiFolderPickerActivity::class.java).apply {
                putExtra("mode", "image")
            }
            iconPickerLauncher.launch(intent)
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val meta = ru.reset.renplay.utils.RenpyProjectParser.parse(gamePath)

            val parsedName = meta.name ?: File(gamePath).name
            val parsedVersion = meta.version ?: "1.0"

            val assets = ru.reset.renplay.utils.GameAssetExtractor.getGameAssets(requireContext(), gamePath)
            var parsedIcon = assets.iconPath

            if (parsedIcon == null && meta.iconRelPath != null) {
                val iconFile = File(gamePath, "game/" + meta.iconRelPath)
                if (iconFile.exists()) {
                    parsedIcon = iconFile.absolutePath
                }
            }

            val autoEngine = libraryViewModel.engineManager.getEngine(meta.scriptVersion)

            withContext(Dispatchers.Main) {
                etName.setText(parsedName)
                etVersion.setText(parsedVersion)
                if (parsedIcon != null) {
                    customIconPath = parsedIcon
                    ivIconPreview?.setImageBitmap(BitmapFactory.decodeFile(customIconPath))
                }

                val btnEngine = view.findViewById<Button>(R.id.btn_select_engine)
                btnEngine.tag = autoEngine?.version
                if (autoEngine != null) {
                    btnEngine.text = getString(R.string.engine_version_format, autoEngine.version)
                } else {
                    btnEngine.text = getString(R.string.engine_not_installed)
                }
            }
        }

        val btnEngine = view.findViewById<Button>(R.id.btn_select_engine)
        btnEngine.setOnClickListener {
            val engines = libraryViewModel.engineManager.getInstalledEngines()
            if (engines.isEmpty()) return@setOnClickListener

            val names = engines.map { getString(R.string.engine_version_format, it.version) }.toTypedArray()
            val currentVersion = btnEngine.tag as? String
            val checkedItem = engines.indexOfFirst { it.version == currentVersion }.takeIf { it >= 0 } ?: 0

            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.engine_version_title)
                .setSingleChoiceItems(names, checkedItem) { dialog, which ->
                    val selected = engines[which]
                    btnEngine.text = getString(R.string.engine_version_format, selected.version)
                    btnEngine.tag = selected.version
                    dialog.dismiss()
                }
                .show()
        }

        view.findViewById<Button>(R.id.btn_add_game).setOnClickListener {
            val name = etName.text.toString().trim()
            if (name.isNotEmpty()) {
                val engineVersion = btnEngine.tag as? String
                libraryViewModel.addProject(
                    name = name,
                    version = etVersion.text.toString().trim(),
                    path = gamePath,
                    customIconPath = customIconPath,
                    engineVersion = engineVersion,
                    application = requireContext().applicationContext as Application
                )
                dismiss()
            }
        }
    }
}

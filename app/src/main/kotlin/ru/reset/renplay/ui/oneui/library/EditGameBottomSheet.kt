package ru.reset.renplay.ui.oneui.library

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import dev.oneuiproject.oneui.app.SemBottomSheetDialogFragment
import ru.reset.renplay.R
import ru.reset.renplay.di.AppViewModelProvider
import ru.reset.renplay.ui.library.LibraryViewModel
import ru.reset.renplay.utils.applyCrossWindowBlur

class EditGameBottomSheet : SemBottomSheetDialogFragment(R.layout.oneui_bottomsheet_add_game) {

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
        fun newInstance(projectId: String): EditGameBottomSheet {
            return EditGameBottomSheet().apply {
                arguments = Bundle().apply { putString("projectId", projectId) }
            }
        }
    }

    private var customIconPath: String? = null
    private var customBgPath: String? = null
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
        val projectId = requireArguments().getString("projectId") ?: return
        val project = libraryViewModel.projectsList.value.find { it.id == projectId } ?: return

        view.findViewById<android.widget.TextView>(R.id.title).text = getString(R.string.edit_game_title)
        view.findViewById<Button>(R.id.btn_add_game).text = getString(R.string.edit_game_save)

        val etName = view.findViewById<EditText>(R.id.et_name)
        val etVersion = view.findViewById<EditText>(R.id.et_version)
        ivIconPreview = view.findViewById<ImageView>(R.id.iv_icon_preview)
        val btnEngine = view.findViewById<Button>(R.id.btn_select_engine)
        
        btnEngine.visibility = View.GONE
        view.findViewById<android.widget.TextView>(R.id.tv_engine_label)?.visibility = View.GONE

        etName.setText(project.name)
        etVersion.setText(project.version)
        customIconPath = project.customIconPath
        customBgPath = project.customBackgroundPath

        if (customIconPath != null) {
            ivIconPreview?.setImageBitmap(BitmapFactory.decodeFile(customIconPath))
        }

        view.findViewById<Button>(R.id.btn_pick_icon).setOnClickListener {
            val intent = android.content.Intent(requireContext(), ru.reset.renplay.ui.oneui.picker.OneUiFolderPickerActivity::class.java).apply { putExtra("mode", "image") }
            iconPickerLauncher.launch(intent)
        }

        view.findViewById<Button>(R.id.btn_add_game).setOnClickListener {
            val name = etName.text.toString().trim()
            if (name.isNotEmpty()) {
                libraryViewModel.updateProject(project.copy(
                    name = name,
                    version = etVersion.text.toString().trim(),
                    customIconPath = customIconPath,
                    customBackgroundPath = customBgPath
                ))
                dismiss()
            }
        }
    }
}
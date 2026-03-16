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

    private val iconPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val cachedFile = File(requireContext().cacheDir, "custom_icon_${System.currentTimeMillis()}.png")
                    requireContext().contentResolver.openInputStream(uri)?.use { input ->
                        cachedFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    withContext(Dispatchers.Main) {
                        customIconPath = cachedFile.absolutePath
                        ivIconPreview?.setImageBitmap(BitmapFactory.decodeFile(customIconPath))
                    }
                } catch (e: Exception) {}
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
            iconPickerLauncher.launch("image/*")
        }

        lifecycleScope.launch(Dispatchers.IO) {
            var parsedName = File(gamePath).name
            var parsedVersion = "1.0"
            var parsedIcon: String? = null

            val optionsFile = File(gamePath, "game/options.rpy")
            if (optionsFile.exists()) {
                try {
                    optionsFile.forEachLine { line ->
                        val trimmed = line.trim()
                        if (trimmed.startsWith("define config.name")) {
                            val firstQuote = trimmed.indexOfAny(charArrayOf('"', '\''))
                            val lastQuote = trimmed.lastIndexOfAny(charArrayOf('"', '\''))
                            if (firstQuote != -1 && lastQuote != -1 && firstQuote < lastQuote) {
                                parsedName = trimmed.substring(firstQuote + 1, lastQuote)
                            }
                        } else if (trimmed.startsWith("define config.version")) {
                            val firstQuote = trimmed.indexOfAny(charArrayOf('"', '\''))
                            val lastQuote = trimmed.lastIndexOfAny(charArrayOf('"', '\''))
                            if (firstQuote != -1 && lastQuote != -1 && firstQuote < lastQuote) {
                                parsedVersion = trimmed.substring(firstQuote + 1, lastQuote)
                            }
                        } else if (trimmed.startsWith("define config.window_icon")) {
                            val firstQuote = trimmed.indexOfAny(charArrayOf('"', '\''))
                            val lastQuote = trimmed.lastIndexOfAny(charArrayOf('"', '\''))
                            if (firstQuote != -1 && lastQuote != -1 && firstQuote < lastQuote) {
                                val iconRelPath = trimmed.substring(firstQuote + 1, lastQuote)
                                val iconFile = File(gamePath, "game/$iconRelPath")
                                if (iconFile.exists()) {
                                    parsedIcon = iconFile.absolutePath
                                }
                            }
                        }
                    }
                } catch (e: Exception) {}
            }

            withContext(Dispatchers.Main) {
                etName.setText(parsedName)
                etVersion.setText(parsedVersion)
                if (parsedIcon != null) {
                    customIconPath = parsedIcon
                    ivIconPreview?.setImageBitmap(BitmapFactory.decodeFile(customIconPath))
                }
            }
        }

        view.findViewById<Button>(R.id.btn_add_game).setOnClickListener {
            val name = etName.text.toString().trim()
            if (name.isNotEmpty()) {
                libraryViewModel.addProject(
                    name = name,
                    version = etVersion.text.toString().trim(),
                    path = gamePath,
                    customIconPath = customIconPath,
                    application = requireContext().applicationContext as Application
                )
                dismiss()
            }
        }
    }
}

package ru.reset.renplay.ui.oneui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import dev.oneuiproject.oneui.app.SemBottomSheetDialogFragment
import ru.reset.renplay.R
import ru.reset.renplay.utils.applyCrossWindowBlur

class OneUiTextBottomSheet(
    private val sheetTitle: String,
    private val content: String,
    private val isLogs: Boolean
) : SemBottomSheetDialogFragment(R.layout.oneui_bottomsheet_text) {

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TextView>(R.id.bottom_sheet_title).text = sheetTitle
        view.findViewById<TextView>(R.id.bottom_sheet_text).text = content

        val btnAction = view.findViewById<Button>(R.id.btn_action)
        if (isLogs) {
            btnAction.text = getString(R.string.logs_copy_button)
            btnAction.setOnClickListener {
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.clip_logs), content))
                Toast.makeText(requireContext(), R.string.logs_copied_toast, Toast.LENGTH_SHORT).show()
                dismiss()
            }
        } else {
            btnAction.text = getString(dev.oneuiproject.oneui.design.R.string.oui_des_common_close)
            btnAction.setOnClickListener {
                dismiss()
            }
        }
    }
}

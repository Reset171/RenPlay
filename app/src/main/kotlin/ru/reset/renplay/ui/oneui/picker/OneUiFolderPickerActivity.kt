package ru.reset.renplay.ui.oneui.picker

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import dev.oneuiproject.oneui.layout.ToolbarLayout
import dev.oneuiproject.oneui.recyclerview.ktx.enableCoreSeslFeatures
import dev.oneuiproject.oneui.utils.ItemDecorRule
import dev.oneuiproject.oneui.utils.SemItemDecoration
import dev.oneuiproject.oneui.widget.RoundedRecyclerView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import ru.reset.renplay.R
import ru.reset.renplay.di.AppViewModelProvider
import ru.reset.renplay.ui.picker.FolderPickerViewModel

class OneUiFolderPickerActivity : AppCompatActivity() {

    private lateinit var viewModel: FolderPickerViewModel
    private lateinit var adapter: OneUiFolderPickerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_oneui_folder_picker)

        val mode = intent.getStringExtra("mode") ?: "game"

        viewModel = ViewModelProvider(this, AppViewModelProvider.Factory)[FolderPickerViewModel::class.java]
        viewModel.initMode("base_path", mode)

        val toolbar = findViewById<ToolbarLayout>(R.id.toolbar_layout)
        toolbar.setTitle(when(mode) {
            "zip" -> getString(R.string.picker_title_zip)
            "image" -> getString(R.string.picker_title_image)
            else -> getString(R.string.picker_title)
        })
        toolbar.setNavigationButtonOnClickListener {
            finish()
        }

        findViewById<android.view.View>(R.id.bottom_dock).visibility = android.view.View.GONE

        val tvPath = findViewById<android.widget.TextView>(R.id.tv_current_path)
        val btnUp = findViewById<android.widget.ImageView>(R.id.btn_path_up)
        btnUp.setOnClickListener { viewModel.navigateUp() }

        onBackPressedDispatcher.addCallback(this) {
            if (viewModel.canNavigateUp()) {
                viewModel.navigateUp()
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }

        val recyclerView = findViewById<RoundedRecyclerView>(R.id.recycler_view)
        adapter = OneUiFolderPickerAdapter(lifecycleScope) { item ->
            if (item.isGame || !item.isDirectory) {
                val resultIntent = Intent().apply {
                    putExtra("selectedPath", item.file.absolutePath)
                }
                setResult(RESULT_OK, resultIntent)
                finish()
            } else {
                viewModel.navigateTo(item.file)
            }
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        recyclerView.enableCoreSeslFeatures()

        val btnSelect = findViewById<Button>(R.id.btn_select_folder)
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                tvPath.text = state.path.absolutePath
                btnUp.alpha = if (viewModel.canNavigateUp()) 1f else 0.4f
                adapter.submitList(state.items)

                if (mode == "game") {
                    btnSelect.isEnabled = state.isValidGameFolder
                    btnSelect.alpha = if (state.isValidGameFolder) 1f else 0.4f
                }
            }
        }

        lifecycleScope.launch {
            viewModel.hasPermission.collectLatest { hasPerm ->
                if (!hasPerm) {
                    Toast.makeText(this@OneUiFolderPickerActivity, R.string.picker_access_denied, Toast.LENGTH_SHORT).show()
                }
            }
        }

        findViewById<Button>(R.id.btn_select_folder).setOnClickListener {
            val intent = Intent().apply {
                putExtra("selectedPath", viewModel.uiState.value.path.absolutePath)
            }
            setResult(RESULT_OK, intent)
            finish()
        }
    }
}

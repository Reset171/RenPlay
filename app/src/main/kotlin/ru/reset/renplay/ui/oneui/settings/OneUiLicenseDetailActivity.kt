package ru.reset.renplay.ui.oneui.settings

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dev.oneuiproject.oneui.layout.ToolbarLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.reset.renplay.R

class OneUiLicenseDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_oneui_license_detail)

        val libraryName = intent.getStringExtra("EXTRA_NAME") ?: ""
        val licenseType = intent.getStringExtra("EXTRA_LICENSE") ?: ""
        val copyright = intent.getStringExtra("EXTRA_COPYRIGHT") ?: ""
        val textResId = intent.getIntExtra("EXTRA_TEXT_RES_ID", 0)

        val toolbar = findViewById<ToolbarLayout>(R.id.toolbar_layout)
        toolbar.setTitle(libraryName)
        toolbar.setNavigationButtonOnClickListener { onBackPressedDispatcher.onBackPressed() }

        findViewById<TextView>(R.id.tv_license_type).text = licenseType
        findViewById<TextView>(R.id.tv_copyright).text = copyright

        val tvText = findViewById<TextView>(R.id.tv_license_text)

        if (textResId != 0) {
            lifecycleScope.launch(Dispatchers.IO) {
                val rawText = resources.openRawResource(textResId).bufferedReader().use { it.readText() }
                val formattedText = rawText.replace(Regex("(?<!\\n)\\n(?!\\n)"), " ")
                withContext(Dispatchers.Main) {
                    tvText.text = formattedText
                }
            }
        }
    }
}

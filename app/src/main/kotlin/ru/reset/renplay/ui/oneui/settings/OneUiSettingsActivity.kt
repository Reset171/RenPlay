package ru.reset.renplay.ui.oneui.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import dev.oneuiproject.oneui.layout.ToolbarLayout
import ru.reset.renplay.R

class OneUiSettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_oneui_settings)

        val toolbar = findViewById<ToolbarLayout>(R.id.settings_toolbar_layout)
        toolbar.setNavigationButtonOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_fragment_container, OneUiSettingsFragment())
                .commit()
        }
    }
}

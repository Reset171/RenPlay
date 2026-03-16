package ru.reset.renplay.ui.oneui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import dev.oneuiproject.oneui.layout.ToolbarLayout
import dev.oneuiproject.oneui.widget.CardItemView
import ru.reset.renplay.R

class OneUiAboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_oneui_about)

        val toolbar = findViewById<ToolbarLayout>(R.id.toolbar_layout)
        toolbar.setNavigationButtonOnClickListener { onBackPressedDispatcher.onBackPressed() }

        val versionText = try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            packageInfo.versionName ?: "1.0"
        } catch (e: Exception) { "Unknown" }
        findViewById<TextView>(R.id.tv_app_version).text = getString(R.string.version_prefix, versionText)

        findViewById<CardItemView>(R.id.card_about_github).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Reset171/RenPlay")))
        }

        findViewById<CardItemView>(R.id.card_about_licenses).setOnClickListener {
            startActivity(Intent(this, OneUiLicensesActivity::class.java))
        }

        findViewById<CardItemView>(R.id.card_about_dev).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Reset171")))
        }
    }
}

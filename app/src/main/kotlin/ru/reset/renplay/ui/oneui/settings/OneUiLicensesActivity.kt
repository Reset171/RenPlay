package ru.reset.renplay.ui.oneui.settings

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import dev.oneuiproject.oneui.layout.ToolbarLayout
import dev.oneuiproject.oneui.widget.CardItemView
import dev.oneuiproject.oneui.widget.RoundedLinearLayout
import ru.reset.renplay.R

class OneUiLicensesActivity : AppCompatActivity() {

    private data class LicenseInfo(val name: String, val copyright: String, val license: String, val textResId: Int)

    private val libraryData = listOf(
        LicenseInfo("Android Jetpack & Compose", "Copyright © The Android Open Source Project", "Apache License 2.0", R.raw.apache_2_0),
        LicenseInfo("Kotlin & Kotlinx", "Copyright © JetBrains s.r.o.", "Apache License 2.0", R.raw.apache_2_0),
        LicenseInfo("Ren'Py Visual Novel Engine", "Copyright © Tom Rothamel", "MIT License", R.raw.mit),
        LicenseInfo("Python", "Copyright © Python Software Foundation", "PSF License", R.raw.psf),
        LicenseInfo("SDL2", "Copyright © Sam Lantinga", "zlib License", R.raw.zlib),
        LicenseInfo("JTar", "Copyright © Kamran Zafar", "Apache License 2.0", R.raw.apache_2_0),
        LicenseInfo("Material Design Icons", "Copyright © Google LLC", "Apache License 2.0", R.raw.apache_2_0),
        LicenseInfo("OpenDyslexic", "Copyright © Abbie Gonzalez", "SIL Open Font License 1.1", R.raw.ofl_1_1),
        LicenseInfo("DejaVu Fonts", "Copyright © Bitstream, Inc.", "Bitstream Vera License", R.raw.bitstream),
        LicenseInfo("Twemoji", "Copyright © Twitter, Inc and other contributors", "CC-BY 4.0 / MIT License", R.raw.cc_by_4_0),
        LicenseInfo("PyJNIus", "Copyright © Kivy Team and other contributors", "MIT License", R.raw.mit),
        LicenseInfo("SDL_GameControllerDB", "Copyright © Gabriel Jacobo", "zlib License", R.raw.zlib),
        LicenseInfo("Ren'Py Third-Party Dependencies", "Copyright © Various Authors", "LGPL / Mixed", R.raw.renpy_disclaimer),
        LicenseInfo("FFmpeg", "Copyright © FFmpeg developers", "LGPL v2.1+", R.raw.lgpl_2_1),
        LicenseInfo("FreeType", "Copyright © The FreeType Project", "Zlib License", R.raw.zlib),
        LicenseInfo("HarfBuzz", "Copyright © Behdad Esfahbod and contributors", "Old MIT License", R.raw.mit),
        LicenseInfo("libpng", "Copyright © Glenn Randers-Pehrson and contributors", "PNG License", R.raw.libpng),
        LicenseInfo("libjpeg-turbo", "Copyright © The libjpeg-turbo Project", "IJG License", R.raw.ijg),
        LicenseInfo("libwebp", "Copyright © Google LLC", "Libwebp Licenses", R.raw.libwebp_license),
        LicenseInfo("OneUI Icons", "Copyright © 2022 Yanndroid & BlackMesa123", "MIT License", R.raw.mit),
        LicenseInfo("OneUI Design (Original Base)", "Copyright © 2022 Yanndroid & BlackMesa123", "MIT License", R.raw.mit),
        LicenseInfo("OneUI Design & SESL (Tribalfs Fork)", "Copyright © 2024 Tribalfs", "MIT License", R.raw.mit)
    ).sortedBy { it.name.lowercase() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_oneui_licenses)

        val toolbar = findViewById<ToolbarLayout>(R.id.toolbar_layout)
        toolbar.setNavigationButtonOnClickListener { onBackPressedDispatcher.onBackPressed() }

        val container = findViewById<RoundedLinearLayout>(R.id.licenses_container)

        libraryData.forEachIndexed { index, license ->
            val card = CardItemView(this).apply {
                title = license.name
                showTopDivider = index != 0
                showBottomDivider = false
                setOnClickListener {
                    val intent = Intent(this@OneUiLicensesActivity, OneUiLicenseDetailActivity::class.java).apply {
                        putExtra("EXTRA_NAME", license.name)
                        putExtra("EXTRA_LICENSE", license.license)
                        putExtra("EXTRA_COPYRIGHT", license.copyright)
                        putExtra("EXTRA_TEXT_RES_ID", license.textResId)
                    }
                    startActivity(intent)
                }
            }
            container.addView(card)
        }
    }
}

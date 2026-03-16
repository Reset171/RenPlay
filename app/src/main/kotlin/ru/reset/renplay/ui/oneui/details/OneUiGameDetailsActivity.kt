package ru.reset.renplay.ui.oneui.details

import android.content.Intent
import android.graphics.Bitmap
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Outline
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import dev.oneuiproject.oneui.layout.ToolbarLayout
import dev.oneuiproject.oneui.widget.CardItemView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.reset.renplay.R
import ru.reset.renplay.di.AppViewModelProvider
import ru.reset.renplay.ui.library.LibraryViewModel
import ru.reset.renplay.ui.oneui.components.OneUiTextBottomSheet
import ru.reset.renplay.utils.PREFS_NAME
import ru.reset.renplay.utils.archive.RpaExtractor
import java.io.File

class OneUiGameDetailsActivity : AppCompatActivity() {

    private lateinit var viewModel: LibraryViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_oneui_game_details)
        
        viewModel = ViewModelProvider(this, AppViewModelProvider.Factory)[LibraryViewModel::class.java]
        
        val projectId = intent.getStringExtra("projectId") ?: return
        val project = viewModel.projectsList.value.find { it.id == projectId }

        if (project == null) {
            finish()
            return
        }

        val iconPath = project.customIconPath ?: project.iconPath
        setupBackgroundBlur(project.path, iconPath)

        val toolbar = findViewById<ToolbarLayout>(R.id.toolbar_layout)
        toolbar.setNavigationButtonOnClickListener { onBackPressedDispatcher.onBackPressed() }

        val ivIcon = findViewById<ImageView>(R.id.iv_game_icon)
        ivIcon.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, view.height * 0.25f)
            }
        }
        ivIcon.clipToOutline = true

        findViewById<TextView>(R.id.tv_game_title).text = project.name
        
        val versionText = findViewById<TextView>(R.id.tv_game_version)
        if (project.version.isNotEmpty()) {
            versionText.text = getString(R.string.version_prefix, project.version)
            versionText.visibility = View.VISIBLE
        } else {
            versionText.visibility = View.GONE
        }

        findViewById<Button>(R.id.btn_play).setOnClickListener {
            val gameFolder = File(project.path, "game")
            if (!gameFolder.exists() || !gameFolder.isDirectory) {
                Toast.makeText(this, getString(R.string.error_no_game_folder), Toast.LENGTH_LONG).show()
            } else {
                val launchIntent = Intent(this, org.renpy.android.PythonSDLActivity::class.java).apply {
                    putExtra("GAME_PATH", project.path)
                }
                startActivity(launchIntent)
            }
        }

        val playtime = viewModel.getPlaytimeStats(project.path)
        findViewById<CardItemView>(R.id.card_stat_total).summary = formatPlaytime(playtime.totalMillis)
        findViewById<CardItemView>(R.id.card_stat_today).summary = formatPlaytime(playtime.todayMillis)

        findViewById<CardItemView>(R.id.card_action_shortcut).setOnClickListener {
            if (ShortcutManagerCompat.isRequestPinShortcutSupported(this)) {
                val launchIntent = Intent(this, ru.reset.renplay.MainActivity::class.java).apply {
                    action = "ru.reset.renplay.LAUNCH_GAME"
                    putExtra("GAME_PATH", project.path)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                val iconPathToLoad = project.customIconPath ?: project.iconPath
                val shortcutBitmap = iconPathToLoad?.let { BitmapFactory.decodeFile(it) }
                val icon = if (shortcutBitmap != null) {
                    val scaled = Bitmap.createScaledBitmap(shortcutBitmap, 192, 192, true)
                    IconCompat.createWithBitmap(scaled)
                } else {
                    IconCompat.createWithResource(this, R.mipmap.ic_launcher)
                }
                val shortcutInfo = ShortcutInfoCompat.Builder(this, "game_${project.id}")
                    .setShortLabel(project.name)
                    .setIntent(launchIntent)
                    .setIcon(icon)
                    .build()
                ShortcutManagerCompat.requestPinShortcut(this, shortcutInfo, null)
            }
        }

        findViewById<CardItemView>(R.id.card_action_logs).setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "threadtime"))
                    val lines = process.inputStream.bufferedReader().readLines().takeLast(300)
                    val sb = StringBuilder()
                    lines.forEach { line ->
                        if (line.contains("python") || line.contains("SDL") || line.contains("System.err") || line.contains("FATAL") || line.contains("AndroidRuntime")) {
                            sb.append(line).append("\n")
                        }
                    }
                    val result = if (sb.isEmpty()) getString(R.string.logs_not_found) else sb.toString()
                    withContext(Dispatchers.Main) {
                        OneUiTextBottomSheet(getString(R.string.logs_dialog_title), result, true).show(supportFragmentManager, null)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        OneUiTextBottomSheet(getString(R.string.logs_dialog_title), getString(R.string.logs_read_error, e.message), true).show(supportFragmentManager, null)
                    }
                }
            }
        }

        findViewById<CardItemView>(R.id.card_action_delete).setOnClickListener {
            viewModel.removeProject(project)
            finish()
        }
    }

    private fun formatPlaytime(millis: Long): String {
        if (millis < 60000L) return getString(R.string.playtime_less_than_minute)
        val minutes = (millis / (1000 * 60)) % 60
        val hours = (millis / (1000 * 60 * 60))
        return if (hours > 0) getString(R.string.playtime_format, hours, minutes) else getString(R.string.playtime_format_min, minutes)
    }

    private fun setupBackgroundBlur(projectPath: String, iconPath: String?) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val enableBlur = prefs.getBoolean("enable_blur", true)

        val dimView = findViewById<View>(R.id.v_background_dim)
        val ivBackground = findViewById<ImageView>(R.id.iv_background_blur)

        val typedValue = TypedValue()
        theme.resolveAttribute(androidx.appcompat.R.attr.roundedCornerColor, typedValue, true)
        val bgColor = typedValue.data

        val typedValueBg = TypedValue()
        theme.resolveAttribute(android.R.attr.colorBackground, typedValueBg, true)
        val cardBgColor = typedValueBg.data

        lifecycleScope.launch(Dispatchers.IO) {
            val gameIconBitmap = iconPath?.let { BitmapFactory.decodeFile(it) }
            var bgBitmap = if (enableBlur) extractGameBackground(projectPath) else null

            if (enableBlur && bgBitmap == null) {
                val canvasSize = 400
                val opaqueBitmap = Bitmap.createBitmap(canvasSize, canvasSize, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(opaqueBitmap)

                val primaryVal = TypedValue()
                theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimaryDark, primaryVal, true)
                val pColor = primaryVal.data

                val gradient = android.graphics.LinearGradient(
                    0f, 0f, canvasSize.toFloat(), canvasSize.toFloat(),
                    pColor, bgColor, android.graphics.Shader.TileMode.CLAMP
                )
                val paint = android.graphics.Paint().apply { shader = gradient }
                canvas.drawRect(0f, 0f, canvasSize.toFloat(), canvasSize.toFloat(), paint)

                val iconSize = 200f
                val offset = (canvasSize - iconSize) / 2f

                if (gameIconBitmap != null) {
                    val destRect = android.graphics.RectF(offset, offset, offset + iconSize, offset + iconSize)
                    canvas.drawBitmap(gameIconBitmap, null, destRect, null)
                } else {
                    androidx.core.content.ContextCompat.getDrawable(this@OneUiGameDetailsActivity, R.drawable.ic_no_icon)?.let {
                        it.setBounds(offset.toInt(), offset.toInt(), (offset + iconSize).toInt(), (offset + iconSize).toInt())
                        androidx.core.graphics.drawable.DrawableCompat.setTint(it, android.graphics.Color.WHITE)
                        it.draw(canvas)
                    }
                }
                bgBitmap = opaqueBitmap
            }

            withContext(Dispatchers.Main) {
                val ivIcon = findViewById<ImageView>(R.id.iv_game_icon)
                if (gameIconBitmap != null) {
                    ivIcon.setImageBitmap(gameIconBitmap)
                } else {
                    ivIcon.setImageResource(R.drawable.ic_no_icon)
                }

                if (enableBlur && bgBitmap != null) {
                    androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
                    window.statusBarColor = android.graphics.Color.TRANSPARENT
                    window.navigationBarColor = android.graphics.Color.TRANSPARENT
                    window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

                    findViewById<dev.oneuiproject.oneui.layout.ToolbarLayout>(R.id.toolbar_layout)?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    findViewById<View>(dev.oneuiproject.oneui.design.R.id.toolbarlayout_coordinator_layout)?.setBackgroundColor(android.graphics.Color.TRANSPARENT)

                    findViewById<com.google.android.material.appbar.AppBarLayout>(dev.oneuiproject.oneui.design.R.id.toolbarlayout_app_bar)?.setBackgroundColor(android.graphics.Color.TRANSPARENT)

                    findViewById<com.google.android.material.appbar.CollapsingToolbarLayout>(dev.oneuiproject.oneui.design.R.id.toolbarlayout_collapsing_toolbar)?.apply {
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        setContentScrimColor(android.graphics.Color.TRANSPARENT)
                        setStatusBarScrimColor(android.graphics.Color.TRANSPARENT)
                    }

                    findViewById<androidx.appcompat.widget.Toolbar>(dev.oneuiproject.oneui.design.R.id.toolbarlayout_main_toolbar)?.setBackgroundColor(android.graphics.Color.TRANSPARENT)

                    findViewById<View>(dev.oneuiproject.oneui.design.R.id.tbl_main_content)?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    findViewById<View>(dev.oneuiproject.oneui.design.R.id.tbl_main_content_parent)?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    findViewById<View>(dev.oneuiproject.oneui.design.R.id.tbl_bottom_corners)?.setBackgroundColor(android.graphics.Color.TRANSPARENT)

                    dimView.setBackgroundColor(ColorUtils.setAlphaComponent(bgColor, 100))

                    val translucentCardColor = ColorUtils.setAlphaComponent(cardBgColor, 160)
                    val cardRadius = 24f * resources.displayMetrics.density

                    val cardOutlineProvider = object : ViewOutlineProvider() {
                        override fun getOutline(view: View, outline: android.graphics.Outline) {
                            outline.setRoundRect(0, 0, view.width, view.height, cardRadius)
                        }
                    }

                    arrayOf(R.id.container_header, R.id.container_stats, R.id.container_actions).forEach { id ->
                        findViewById<View>(id)?.apply {
                            setBackgroundColor(translucentCardColor)
                            clipToOutline = true
                            outlineProvider = cardOutlineProvider
                            if (this is dev.oneuiproject.oneui.delegates.ViewRoundedCorner) {
                                roundedCorners = androidx.appcompat.util.SeslRoundedCorner.ROUNDED_CORNER_NONE
                            }
                        }
                    }

                    val finalBitmap = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                        Bitmap.createScaledBitmap(bgBitmap, (bgBitmap.width / 10).coerceAtLeast(1), (bgBitmap.height / 10).coerceAtLeast(1), true)
                    } else {
                        bgBitmap
                    }
                    ivBackground.setImageBitmap(finalBitmap)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        ivBackground.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(150f, 150f, android.graphics.Shader.TileMode.CLAMP))
                    }
                    ivBackground.visibility = View.VISIBLE
                } else {
                    dimView.setBackgroundColor(bgColor)
                    ivBackground.visibility = View.GONE

                    findViewById<View>(R.id.container_header)?.setBackgroundColor(cardBgColor)
                    findViewById<View>(R.id.container_stats)?.setBackgroundColor(cardBgColor)
                    findViewById<View>(R.id.container_actions)?.setBackgroundColor(cardBgColor)
                }
            }
        }
    }

    private fun extractGameBackground(path: String): Bitmap? {
        val gameDir = File(path, "game")
        val guiDir = File(gameDir, "gui")
        val exts = listOf("png", "jpg", "jpeg", "webp")
        for (ext in exts) {
            val f = File(guiDir, "main_menu.$ext")
            if (f.exists()) {
                val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                return BitmapFactory.decodeFile(f.absolutePath, opts)
            }
        }
        val rpaFiles = gameDir.listFiles { _, name -> name.endsWith(".rpa", true) } ?: emptyArray()
        for (rpa in rpaFiles) {
            for (ext in exts) {
                val bytes = RpaExtractor.extractSingleFileBytes(rpa, "gui/main_menu.$ext")
                if (bytes != null) {
                    val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                }
            }
        }
        return null
    }
}

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

        val toolbar = findViewById<ToolbarLayout>(R.id.toolbar_layout)
        toolbar.setNavigationButtonOnClickListener { onBackPressedDispatcher.onBackPressed() }

        findViewById<dev.oneuiproject.oneui.widget.CardItemView>(R.id.card_action_edit)?.setOnClickListener {
            ru.reset.renplay.ui.oneui.library.EditGameBottomSheet.newInstance(projectId).show(supportFragmentManager, null)
        }

        val ivIcon = findViewById<ImageView>(R.id.iv_game_icon)
        ivIcon.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, view.height * 0.25f)
            }
        }
        ivIcon.clipToOutline = true

        lifecycleScope.launch {
            viewModel.projectsList.collect { projects ->
                val project = projects.find { it.id == projectId }
                if (project == null) {
                    finish()
                    return@collect
                }

                val iconPath = project.customIconPath ?: project.iconPath
                setupBackgroundBlur(project.path, iconPath)

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
                        Toast.makeText(this@OneUiGameDetailsActivity, getString(R.string.error_no_game_folder), Toast.LENGTH_LONG).show()
                    } else {
                        val engine = viewModel.engineManager.getEngine(project.engineVersion)
                        if (engine != null) {
                            val launchIntent = Intent(this@OneUiGameDetailsActivity, org.renpy.android.PythonSDLActivity::class.java).apply {
                                putExtra("GAME_PATH", project.path)
                                putExtra("GAME_NAME", project.name)
                                putExtra("ENGINE_PATH", engine.dirPath)
                                putExtra("ENGINE_VERSION", engine.version)
                                putExtra("ENGINE_ZIP", engine.zipPath)
                                putExtra("ENGINE_LIB", engine.dirPath + "/lib")
                            }
                            startActivity(launchIntent)
                        }
                    }
                }

                val playtime = viewModel.getPlaytimeStats(project.path)
                findViewById<CardItemView>(R.id.card_stat_total).summary = formatPlaytime(playtime.totalMillis)
                findViewById<CardItemView>(R.id.card_stat_today).summary = formatPlaytime(playtime.todayMillis)

                val actualEngine = viewModel.engineManager.getEngine(project.engineVersion)
                val tvEngine = findViewById<CardItemView>(R.id.card_action_engine)
                if (actualEngine != null) {
                    tvEngine.summary = getString(R.string.engine_version_format, actualEngine.version)
                } else {
                    tvEngine.summary = getString(R.string.engine_not_installed)
                }

                tvEngine.setOnClickListener {
                    val engines = viewModel.engineManager.getInstalledEngines()
                    if (engines.isEmpty()) return@setOnClickListener

                    val names = engines.map { getString(R.string.engine_version_format, it.version) }.toTypedArray()
                    val checkedItem = engines.indexOfFirst { it.version == actualEngine?.version }.takeIf { it >= 0 } ?: 0

                    androidx.appcompat.app.AlertDialog.Builder(this@OneUiGameDetailsActivity)
                        .setTitle(R.string.engine_version_title)
                        .setSingleChoiceItems(names, checkedItem) { dialog, which ->
                            val selected = engines[which]
                            viewModel.updateProject(project.copy(engineVersion = selected.version))
                            dialog.dismiss()
                        }
                        .show()
                }

                findViewById<CardItemView>(R.id.card_action_shortcut).setOnClickListener {
                    if (ShortcutManagerCompat.isRequestPinShortcutSupported(this@OneUiGameDetailsActivity)) {
                        val engine = viewModel.engineManager.getEngine(project.engineVersion) ?: return@setOnClickListener
                        val launchIntent = Intent(this@OneUiGameDetailsActivity, ru.reset.renplay.MainActivity::class.java).apply {
                            action = "ru.reset.renplay.LAUNCH_GAME"
                            putExtra("GAME_PATH", project.path)
                            putExtra("GAME_NAME", project.name)
                            putExtra("ENGINE_PATH", engine.dirPath)
                            putExtra("ENGINE_VERSION", engine.version)
                            putExtra("ENGINE_ZIP", engine.zipPath)
                            putExtra("ENGINE_LIB", engine.dirPath + "/lib")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        }
                        val iconPathToLoad = project.customIconPath ?: project.iconPath
                        val shortcutBitmap = iconPathToLoad?.let { BitmapFactory.decodeFile(it) }
                        val icon = if (shortcutBitmap != null) {
                            val scaled = Bitmap.createScaledBitmap(shortcutBitmap, 192, 192, true)
                            IconCompat.createWithBitmap(scaled)
                        } else {
                            IconCompat.createWithResource(this@OneUiGameDetailsActivity, R.mipmap.ic_launcher)
                        }
                        val shortcutInfo = ShortcutInfoCompat.Builder(this@OneUiGameDetailsActivity, "game_${project.id}")
                            .setShortLabel(project.name)
                            .setIntent(launchIntent)
                            .setIcon(icon)
                            .build()
                        ShortcutManagerCompat.requestPinShortcut(this@OneUiGameDetailsActivity, shortcutInfo, null)
                    }
                }

                findViewById<CardItemView>(R.id.card_action_logs).setOnClickListener {
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            val sb = StringBuilder()
                            val logFile = File(project.path, "log.txt")
                            val tracebackFile = File(project.path, "traceback.txt")

                            if (tracebackFile.exists()) {
                                sb.append("--- traceback.txt ---\n")
                                sb.append(tracebackFile.readText())
                                sb.append("\n\n")
                            }
                            if (logFile.exists()) {
                                sb.append("--- log.txt ---\n")
                                sb.append(logFile.readText())
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
        val project = viewModel.projectsList.value.find { it.id == intent.getStringExtra("projectId") }
        val customBg = project?.customBackgroundPath

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
            var bgBitmap: Bitmap? = null
            if (enableBlur) {
                if (customBg != null) {
                    bgBitmap = ru.reset.renplay.utils.GameAssetExtractor.loadBitmap(customBg, 600, 400)
                } else {
                    val assets = ru.reset.renplay.utils.GameAssetExtractor.getGameAssets(this@OneUiGameDetailsActivity, projectPath)
                    if (assets.backgroundPath != null) {
                        bgBitmap = ru.reset.renplay.utils.GameAssetExtractor.loadBitmap(assets.backgroundPath, 600, 400)
                    }
                }
            }

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
                    @Suppress("DEPRECATION")
                    window.statusBarColor = android.graphics.Color.TRANSPARENT
                    @Suppress("DEPRECATION")
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

                    arrayOf(R.id.container_header, R.id.container_stats, R.id.container_engine, R.id.container_actions).forEach { id ->
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
                    findViewById<View>(R.id.container_engine)?.setBackgroundColor(cardBgColor)
                    findViewById<View>(R.id.container_actions)?.setBackgroundColor(cardBgColor)
                }
            }
        }
    }

}

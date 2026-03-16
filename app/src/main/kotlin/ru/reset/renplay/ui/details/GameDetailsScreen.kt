package ru.reset.renplay.ui.details

import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.renpy.android.PythonSDLActivity
import ru.reset.renplay.R
import ru.reset.renplay.di.AppViewModelProvider
import ru.reset.renplay.ui.components.layout.*
import ru.reset.renplay.ui.components.inputs.*
import ru.reset.renplay.ui.components.feedback.*
import ru.reset.renplay.ui.components.typography.*
import ru.reset.renplay.ui.components.appbar.*
import ru.reset.renplay.ui.components.icons.*
import ru.reset.renplay.ui.library.LibraryViewModel
import ru.reset.renplay.ui.library.PlaytimeStats
import ru.reset.renplay.ui.settings.SettingsViewModel
import ru.reset.renplay.ui.settings.components.*
import java.io.File

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
fun GameDetailsScreen(
    navController: NavController,
    projectId: String,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    val context = LocalContext.current
    val activity = context as androidx.activity.ComponentActivity
    val coroutineScope = rememberCoroutineScope()
    val viewModel: LibraryViewModel = viewModel(viewModelStoreOwner = activity, factory = AppViewModelProvider.Factory)
    val settingsViewModel: SettingsViewModel = viewModel(viewModelStoreOwner = activity, factory = AppViewModelProvider.Factory)

    val projectsList by viewModel.projectsList.collectAsState()
    val iconCache by viewModel.iconCache.collectAsState()
    val advancedAnimationsEnabled by settingsViewModel.advancedAnimationsEnabled.collectAsState()
    
    var displayProject by remember { mutableStateOf(projectsList.find { it.id == projectId }) }
    LaunchedEffect(projectsList) {
        val p = projectsList.find { it.id == projectId }
        if (p != null) displayProject = p
    }

    if (displayProject == null) {
        LaunchedEffect(Unit) { navController.popBackStack() }
        return
    }
    val project = displayProject!!

    var showCrashLogsDialog by remember { mutableStateOf(false) }
    var crashLogContent by remember { mutableStateOf("") }

    var playtimeStats by remember { mutableStateOf(PlaytimeStats(0L, 0L)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                playtimeStats = viewModel.getPlaytimeStats(project.path)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun formatPlaytime(millis: Long): String {
        if (millis < 60000L) return context.getString(R.string.playtime_less_than_minute)
        val minutes = (millis / (1000 * 60)) % 60
        val hours = (millis / (1000 * 60 * 60))
        return if (hours > 0) context.getString(R.string.playtime_format, hours, minutes) else context.getString(R.string.playtime_format_min, minutes)
    }

    val launchGame: () -> Unit = {
        val projectFile = File(project.path)
        val gameSubFolder = File(projectFile, "game")

        if (!gameSubFolder.exists() || !gameSubFolder.isDirectory) {
            Toast.makeText(context, context.getString(R.string.error_no_game_folder), Toast.LENGTH_LONG).show()
        } else {
            val intent = Intent(context, PythonSDLActivity::class.java).apply {
                putExtra("GAME_PATH", projectFile.absolutePath)
            }
            context.startActivity(intent)
        }
    }

    val fetchLogs: () -> Unit = {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "threadtime"))
                val reader = process.inputStream.bufferedReader()
                val sb = StringBuilder()
                val lines = reader.readLines().takeLast(300)

                lines.forEach { line ->
                    if (line.contains("python") || line.contains("SDL") || line.contains("System.err") || line.contains("FATAL") || line.contains("AndroidRuntime")) {
                        sb.append(line).append("\n")
                    }
                }

                val result = if (sb.isEmpty()) context.getString(R.string.logs_not_found) else sb.toString()
                withContext(Dispatchers.Main) {
                    crashLogContent = result
                    showCrashLogsDialog = true
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    crashLogContent = context.getString(R.string.logs_read_error, e.message)
                    showCrashLogsDialog = true
                }
            }
        }
    }

    val bitmap = iconCache[project.customIconPath ?: project.iconPath ?: ""]
    val localBlurState = rememberAppBlurState()
    localBlurState.blurEnabled = LocalAppBlurState.current.blurEnabled
    val blurActive = localBlurState.blurEnabled

    val initialBg = remember(project.path) {
        try {
            val bgCacheDir = File(context.cacheDir, "project_backgrounds")
            val cachedBgFile = File(bgCacheDir, "${project.path.hashCode()}_bg.jpg")
            if (cachedBgFile.exists()) {
                val options = android.graphics.BitmapFactory.Options().apply {
                    inSampleSize = 2
                }
                android.graphics.BitmapFactory.decodeFile(cachedBgFile.absolutePath, options)
            } else null
        } catch (e: Exception) { null }
    }

    var backgroundBitmap by remember { mutableStateOf<Bitmap?>(initialBg) }

    LaunchedEffect(project.path) {
        if (backgroundBitmap != null) return@LaunchedEffect

        withContext(Dispatchers.IO) {
            val bgCacheDir = File(context.cacheDir, "project_backgrounds")
            if (!bgCacheDir.exists()) bgCacheDir.mkdirs()
            val cachedBgFile = File(bgCacheDir, "${project.path.hashCode()}_bg.jpg")

            val gameDir = File(project.path, "game")
            val guiDir = File(gameDir, "gui")
            var foundBitmap: Bitmap? = null

            val imageExtensions = listOf("png", "jpg", "jpeg", "webp")
            for (ext in imageExtensions) {
                val imgFile = File(guiDir, "main_menu.$ext")
                if (imgFile.exists()) {
                    foundBitmap = android.graphics.BitmapFactory.decodeFile(imgFile.absolutePath)
                    break
                }
            }

            if (foundBitmap == null) {
                val videoExtensions = listOf("webm", "mp4", "mkv")
                for (ext in videoExtensions) {
                    val vidFile = File(guiDir, "main_menu.$ext")
                    if (vidFile.exists()) {
                        try {
                            val retriever = android.media.MediaMetadataRetriever()
                            retriever.setDataSource(vidFile.absolutePath)
                            foundBitmap = retriever.getFrameAtTime(0)
                            retriever.release()
                        } catch (e: Exception) {}
                        break
                    }
                }
            }

            if (foundBitmap == null) {
                val rpaFiles = gameDir.listFiles { _, name -> name.endsWith(".rpa", ignoreCase = true) } ?: emptyArray()
                val rpaTargets = listOf("gui/main_menu.png", "gui/main_menu.jpg", "gui/main_menu.webp")

                searchLoop@ for (rpa in rpaFiles) {
                    for (target in rpaTargets) {
                        val bytes = ru.reset.renplay.utils.archive.RpaExtractor.extractSingleFileBytes(rpa, target)
                        if (bytes != null) {
                            foundBitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            if (foundBitmap != null) break@searchLoop
                        }
                    }
                }
            }

            if (foundBitmap != null) {
                try {
                    cachedBgFile.outputStream().use { out ->
                        foundBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
                    }
                } catch (e: Exception) {}

                withContext(Dispatchers.Main) {
                    backgroundBitmap = foundBitmap
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .appBlurSource(localBlurState)
        ) {
            if (backgroundBitmap != null) {
                androidx.compose.animation.AnimatedContent(
                    targetState = backgroundBitmap,
                    transitionSpec = { androidx.compose.animation.fadeIn(tween(500)) togetherWith androidx.compose.animation.fadeOut(tween(500)) },
                    label = "BackgroundCrossfade"
                ) { targetBmp ->
                    if (targetBmp != null) {
                        Image(
                            bitmap = targetBmp.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f))
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            RenPlayAppBar(
                title = "",
                navigationIcon = {
                    AppIconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.appBlurEffect(
                            state = localBlurState,
                            shape = RoundedCornerShape(14.dp),
                            blurRadius = 16.dp,
                            tint = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                            forceInvalidation = true
                        ),
                        backgroundColor = if (LocalAppBlurState.current.blurEnabled) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        AppIcon(painterResource(R.drawable.ic_arrow_back), null)
                    }
                },
                containerColor = Color.Transparent
            )

            with(sharedTransitionScope) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp, bottom = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = project.name,
                            modifier = Modifier
                                .size(140.dp)
                                .then(
                                    if (advancedAnimationsEnabled) {
                                        Modifier.sharedElement(
                                            sharedContentState = rememberSharedContentState(key = "image-${project.id}"),
                                            animatedVisibilityScope = animatedVisibilityScope,
                                            boundsTransform = { _, _ -> tween(durationMillis = 350, easing = FastOutSlowInEasing) }
                                        )
                                    } else Modifier
                                )
                                .clip(RoundedCornerShape(32.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(140.dp)
                                .then(
                                    if (advancedAnimationsEnabled) {
                                        Modifier.sharedElement(
                                            sharedContentState = rememberSharedContentState(key = "image-${project.id}"),
                                            animatedVisibilityScope = animatedVisibilityScope,
                                            boundsTransform = { _, _ -> tween(durationMillis = 350, easing = FastOutSlowInEasing) }
                                        )
                                    } else Modifier
                                )
                                .clip(RoundedCornerShape(32.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            AppIcon(
                                painter = painterResource(id = R.drawable.ic_no_icon),
                                contentDescription = project.name,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    Spacer(Modifier.height(24.dp))
                    
                    AppText(
                        text = project.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .padding(horizontal = 24.dp)
                            .then(
                                if (advancedAnimationsEnabled) {
                                    Modifier.sharedBounds(
                                        sharedContentState = rememberSharedContentState(key = "title-${project.id}"),
                                        animatedVisibilityScope = animatedVisibilityScope,
                                        resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(),
                                        boundsTransform = { _, _ -> tween(durationMillis = 350, easing = FastOutSlowInEasing) }
                                    )
                                } else Modifier
                            )
                    )
                    
                    if (project.version.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            color = if (blurActive) Color.Transparent else MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.appBlurEffect(
                                state = localBlurState,
                                shape = RoundedCornerShape(12.dp),
                                blurRadius = 16.dp,
                                tint = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                forceInvalidation = true
                            )
                        ) {
                            AppText(
                                text = stringResource(R.string.version_prefix, project.version),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                                    .then(
                                        if (advancedAnimationsEnabled) {
                                            Modifier.sharedBounds(
                                                sharedContentState = rememberSharedContentState(key = "version-${project.id}"),
                                                animatedVisibilityScope = animatedVisibilityScope,
                                                resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(),
                                                boundsTransform = { _, _ -> tween(durationMillis = 350, easing = FastOutSlowInEasing) }
                                            )
                                        } else Modifier
                                    )
                            )
                        }
                    }
                }
            }

            AppButton(
                onClick = launchGame,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(64.dp)
                    .appBlurEffect(
                        state = localBlurState,
                        shape = RoundedCornerShape(24.dp),
                        blurRadius = 20.dp,
                        tint = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        forceInvalidation = true
                    ),
                cornerRadius = 24.dp,
                containerColor = if (blurActive) Color.Transparent else MaterialTheme.colorScheme.primaryContainer,
                elevation = if (blurActive) 0.dp else 2.dp
            ) {
                AppIcon(painterResource(R.drawable.ic_play_arrow), null, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.game_details_play),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(Modifier.height(24.dp))
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AppCard(
                    modifier = Modifier
                        .weight(1f)
                        .appBlurEffect(
                            state = localBlurState,
                            shape = RoundedCornerShape(20.dp),
                            blurRadius = 20.dp,
                            tint = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f),
                            forceInvalidation = true
                        ),
                    containerColor = if (blurActive) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh,
                    elevation = 0.dp,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(
                                painter = painterResource(R.drawable.ic_schedule),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            AppText(
                                text = stringResource(R.string.stat_total),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        AppText(
                            text = formatPlaytime(playtimeStats.totalMillis),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                AppCard(
                    modifier = Modifier
                        .weight(1f)
                        .appBlurEffect(
                            state = localBlurState,
                            shape = RoundedCornerShape(20.dp),
                            blurRadius = 20.dp,
                            tint = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f),
                            forceInvalidation = true
                        ),
                    containerColor = if (blurActive) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh,
                    elevation = 0.dp,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(
                                painter = painterResource(R.drawable.ic_schedule),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            AppText(
                                text = stringResource(R.string.stat_today),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        AppText(
                            text = formatPlaytime(playtimeStats.todayMillis),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                SettingsGroup(
                    title = stringResource(R.string.label_tool),
                    cardModifier = Modifier.appBlurEffect(
                        state = localBlurState,
                        shape = RoundedCornerShape(28.dp),
                        blurRadius = 20.dp,
                        tint = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.4f),
                        forceInvalidation = true
                    ),
                    containerColor = if (blurActive) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer
                ) {
                SettingsItem(
                    title = stringResource(R.string.game_details_shortcut),
                    description = stringResource(R.string.game_details_shortcut_desc),
                    icon = painterResource(R.drawable.ic_shortcut),
                    onClick = {
                        if (ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
                            val intent = Intent(context, ru.reset.renplay.MainActivity::class.java).apply {
                                action = "ru.reset.renplay.LAUNCH_GAME"
                                putExtra("GAME_PATH", project.path)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            }
                            val icon = if (bitmap != null) {
                                val scaled = Bitmap.createScaledBitmap(bitmap, 192, 192, true)
                                IconCompat.createWithBitmap(scaled)
                            } else {
                                IconCompat.createWithResource(context, R.mipmap.ic_launcher)
                            }
                            val shortcutInfo = ShortcutInfoCompat.Builder(context, "game_${project.id}")
                                .setShortLabel(project.name)
                                .setIntent(intent)
                                .setIcon(icon)
                                .build()
                            ShortcutManagerCompat.requestPinShortcut(context, shortcutInfo, null)
                        }
                    },
                    showDivider = true
                )

                SettingsItem(
                    title = stringResource(R.string.game_details_logs),
                    description = stringResource(R.string.game_details_logs_desc),
                    icon = painterResource(R.drawable.ic_bug_report),
                    onClick = fetchLogs,
                    showDivider = true
                )

                SettingsItem(
                    title = stringResource(R.string.game_details_delete),
                    description = stringResource(R.string.game_details_delete_desc),
                    icon = painterResource(R.drawable.ic_delete),
                    onClick = {
                        navController.popBackStack()
                        viewModel.removeProject(project)
                    },
                    showDivider = false
                )
            }
            }

            Spacer(Modifier.height(40.dp))
        }

        if (showCrashLogsDialog) {
            AppBottomPanel(
                onDismissRequest = { showCrashLogsDialog = false },
                title = stringResource(R.string.logs_dialog_title),
                icon = painterResource(R.drawable.ic_info),
                buttons = { dismiss ->
                    AppButton(
                        onClick = {
                            val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText(context.getString(R.string.clip_crash_logs), crashLogContent)
                            clipboardManager.setPrimaryClip(clip)
                            Toast.makeText(context, context.getString(R.string.logs_copied_toast), Toast.LENGTH_SHORT).show()
                        },
                        text = stringResource(R.string.logs_copy_button),
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 16.dp
                    )
                }
            ) {
                AppLogConsole(
                    logs = crashLogContent.lines(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                )
            }
        }
    }
}

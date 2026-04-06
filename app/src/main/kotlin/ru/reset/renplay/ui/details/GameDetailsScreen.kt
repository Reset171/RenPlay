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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import ru.reset.renplay.ui.navigation.Screen
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

    val appToast = ru.reset.renplay.ui.components.feedback.LocalAppToast.current

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

    val availableEngines = remember { viewModel.engineManager.getInstalledEngines() }
    var showEngineSelectorDialog by remember { mutableStateOf(false) }
    var showDeleteGameDialog by remember { mutableStateOf(false) }
    var showEditGameDialog by remember { mutableStateOf(false) }

    var editGameName by remember { mutableStateOf("") }
    var editGameVersion by remember { mutableStateOf("") }
    var editGameIcon by remember { mutableStateOf<String?>(null) }
    var editGameBg by remember { mutableStateOf<String?>(null) }

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
            appToast.show(context.getString(R.string.error_no_game_folder), R.drawable.ic_info)
        } else {
            val engine = viewModel.engineManager.getEngine(project.engineVersion)
            if (engine != null) {
                val intent = Intent(context, PythonSDLActivity::class.java).apply {
                    putExtra("GAME_PATH", projectFile.absolutePath)
                    putExtra("GAME_NAME", project.name)
                    putExtra("ENGINE_PATH", engine.dirPath)
                    putExtra("ENGINE_VERSION", engine.version)
                    putExtra("ENGINE_ZIP", engine.zipPath)
                    putExtra("ENGINE_LIB", engine.dirPath + "/lib")
                }
                context.startActivity(intent)
            } else {
                appToast.show(context.getString(R.string.engine_not_installed_toast), R.drawable.ic_info)
            }
        }
    }

    val fetchLogs: () -> Unit = {
        coroutineScope.launch(Dispatchers.IO) {
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
            val assets = ru.reset.renplay.utils.GameAssetExtractor.getGameAssets(context, project.path)
            if (assets.backgroundPath != null) {
                val bmp = ru.reset.renplay.utils.GameAssetExtractor.loadBitmap(assets.backgroundPath, 600, 400)
                withContext(Dispatchers.Main) {
                    backgroundBitmap = bmp
                }
            }
        }
    }

    CompositionLocalProvider(LocalAppBlurState provides localBlurState) {
        Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .appBlurSource(localBlurState)
            ) {
            val bgSource = backgroundBitmap ?: bitmap
            bgSource?.let { src ->
                androidx.compose.animation.AnimatedContent(
                    targetState = src,
                    transitionSpec = { androidx.compose.animation.fadeIn(tween(500)) togetherWith androidx.compose.animation.fadeOut(tween(500)) },
                    label = "BackgroundCrossfade"
                ) { targetBmp ->
                    Image(
                        bitmap = targetBmp.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(
                                radius = 80.dp,
                                edgeTreatment = BlurredEdgeTreatment.Unbounded
                            ),
                        contentScale = ContentScale.Crop
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.3f),
                                    Color.Black.copy(alpha = 0.85f)
                                )
                            )
                        )
                )
            } ?: run {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
            }
        }

        val scrollState = rememberScrollState()
        val scrollProgress by remember { androidx.compose.runtime.derivedStateOf { (scrollState.value / 80f).coerceIn(0f, 1f) } }
        val buttonBgColor = if (blurActive) Color.Transparent else androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0f), MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f), scrollProgress)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            Spacer(Modifier.statusBarsPadding().height(56.dp))

            with(sharedTransitionScope) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = project.name,
                            modifier = Modifier
                                .size(110.dp)
                                .then(
                                    if (advancedAnimationsEnabled) {
                                        Modifier.sharedElement(
                                            sharedContentState = rememberSharedContentState(key = "image-${project.id}"),
                                            animatedVisibilityScope = animatedVisibilityScope,
                                            boundsTransform = { _, _ -> tween(durationMillis = 350, easing = FastOutSlowInEasing) }
                                        )
                                    } else Modifier
                                )
                                .clip(RoundedCornerShape(28.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .then(
                                    if (advancedAnimationsEnabled) {
                                        Modifier.sharedElement(
                                            sharedContentState = rememberSharedContentState(key = "image-${project.id}"),
                                            animatedVisibilityScope = animatedVisibilityScope,
                                            boundsTransform = { _, _ -> tween(durationMillis = 350, easing = FastOutSlowInEasing) }
                                        )
                                    } else Modifier
                                )
                                .clip(RoundedCornerShape(28.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            AppIcon(
                                painter = painterResource(id = R.drawable.ic_no_icon),
                                contentDescription = project.name,
                                modifier = Modifier.size(52.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    Spacer(Modifier.width(20.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        AppText(
                            text = project.name,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = if (advancedAnimationsEnabled) {
                                Modifier.sharedBounds(
                                    sharedContentState = rememberSharedContentState(key = "title-${project.id}"),
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(),
                                    boundsTransform = { _, _ -> tween(durationMillis = 350, easing = FastOutSlowInEasing) }
                                )
                            } else Modifier
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
            
            AppCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
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
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
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
                        Spacer(Modifier.height(4.dp))
                        AppText(
                            text = formatPlaytime(playtimeStats.totalMillis),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(40.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    )

                    Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
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
                        Spacer(Modifier.height(4.dp))
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

            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppText(
                    text = stringResource(R.string.game_details_launch_params),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp, top = 8.dp)
                )

                val actualEngine = viewModel.engineManager.getEngine(project.engineVersion)
                val engineDesc = if (actualEngine != null) {
                    if (project.engineVersion == null) stringResource(R.string.engine_auto_selected, actualEngine.version) else stringResource(R.string.engine_bound_to, actualEngine.version)
                } else stringResource(R.string.engine_not_installed)

                AppCard(
                    onClick = { if (availableEngines.isNotEmpty()) showEngineSelectorDialog = true },
                    modifier = Modifier.fillMaxWidth()
                        .appBlurEffect(
                            state = localBlurState,
                            shape = RoundedCornerShape(24.dp),
                            blurRadius = 20.dp,
                            tint = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.4f),
                            forceInvalidation = true
                        ),
                    containerColor = if (blurActive) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(24.dp),
                    elevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            AppIcon(painterResource(R.drawable.ic_memory), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            AppText(stringResource(R.string.engine_version_renpy), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(2.dp))
                            AppText(engineDesc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                AppText(
                    text = stringResource(R.string.label_tool),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp, top = 12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ActionSquareCard(
                        title = stringResource(R.string.game_details_shortcut),
                        subtitle = stringResource(R.string.game_details_shortcut_desc),
                        icon = painterResource(R.drawable.ic_shortcut),
                        onClick = {
                            if (ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
                                val engine = viewModel.engineManager.getEngine(project.engineVersion)
                                if (engine != null) {
                                    val intent = Intent(context, ru.reset.renplay.MainActivity::class.java).apply {
                                        action = "ru.reset.renplay.LAUNCH_GAME"
                                        putExtra("GAME_PATH", project.path)
                                        putExtra("GAME_NAME", project.name)
                                        putExtra("ENGINE_PATH", engine.dirPath)
                                        putExtra("ENGINE_VERSION", engine.version)
                                        putExtra("ENGINE_ZIP", engine.zipPath)
                                        putExtra("ENGINE_LIB", engine.dirPath + "/lib")
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
                                } else {
                                    appToast.show(context.getString(R.string.engine_not_installed_toast), R.drawable.ic_info)
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        blurState = localBlurState,
                        blurActive = blurActive
                    )

                    ActionSquareCard(
                        title = stringResource(R.string.game_details_logs),
                        subtitle = stringResource(R.string.game_details_logs_desc),
                        icon = painterResource(R.drawable.ic_bug_report),
                        onClick = fetchLogs,
                        modifier = Modifier.weight(1f),
                        blurState = localBlurState,
                        blurActive = blurActive
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ActionSquareCard(
                        title = stringResource(R.string.game_details_edit),
                        subtitle = stringResource(R.string.game_details_edit_desc),
                        icon = painterResource(R.drawable.ic_format_paint),
                        onClick = {
                            editGameName = project.name
                            editGameVersion = project.version
                            editGameIcon = project.customIconPath
                            editGameBg = project.customBackgroundPath
                            showEditGameDialog = true
                        },
                        modifier = Modifier.weight(1f),
                        blurState = localBlurState,
                        blurActive = blurActive
                    )

                    AppCard(
                        onClick = { showDeleteGameDialog = true },
                        modifier = Modifier.weight(1f).aspectRatio(1f)
                            .appBlurEffect(
                                state = localBlurState,
                                shape = RoundedCornerShape(24.dp),
                                blurRadius = 20.dp,
                                tint = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.4f),
                                forceInvalidation = true
                            ),
                        containerColor = if (blurActive) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(24.dp),
                        elevation = 0.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp).fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.errorContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                AppIcon(painterResource(R.drawable.ic_delete), null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(24.dp))
                            }
                            Column {
                                AppText(stringResource(R.string.game_details_delete), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.height(4.dp))
                                AppText(stringResource(R.string.game_details_delete_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(40.dp))
        }

        RenPlayAppBar(
            title = "",
            scrollProgress = scrollProgress,
            modifier = Modifier.align(Alignment.TopCenter),
            navigationIcon = {
                AppIconButton(
                    onClick = { navController.popBackStack() },
                    backgroundColor = buttonBgColor,
                    shape = CircleShape,
                    modifier = Modifier.appBlurEffect(
                        state = localBlurState,
                        shape = CircleShape,
                        blurRadius = 16.dp * scrollProgress,
                        tint = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f * scrollProgress),
                        forceInvalidation = true
                    )
                ) {
                    AppIcon(painterResource(R.drawable.ic_arrow_back), null)
                }
            }
        )

        if (showEngineSelectorDialog) {
            AppBottomPanel(
                onDismissRequest = { showEngineSelectorDialog = false },
                title = stringResource(R.string.engine_version_dialog_title),
                icon = painterResource(R.drawable.ic_memory)
            ) {
                Column(modifier = Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    availableEngines.forEach { engine ->
                        AppCard(
                            onClick = {
                                viewModel.updateProject(project.copy(engineVersion = engine.version))
                                showEngineSelectorDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = if (project.engineVersion == engine.version) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            elevation = 0.dp,
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                Text(
                                    text = stringResource(R.string.engine_version_format, engine.version),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (project.engineVersion == engine.version) FontWeight.Bold else FontWeight.Medium,
                                    color = if (project.engineVersion == engine.version) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
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
                            appToast.show(context.getString(R.string.logs_copied_toast), R.drawable.ic_info)
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

        if (showEditGameDialog) {
            AppBottomPanel(
                onDismissRequest = { showEditGameDialog = false },
                title = stringResource(R.string.edit_game_title),
                icon = painterResource(R.drawable.ic_format_paint),
                buttons = { dismiss ->
                    AppButton(
                        onClick = {
                            viewModel.updateProject(project.copy(
                                name = editGameName,
                                version = editGameVersion,
                                customIconPath = editGameIcon,
                                customBackgroundPath = editGameBg
                            ))
                            dismiss()
                        },
                        text = stringResource(R.string.edit_game_save),
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 16.dp
                    )
                }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    AppTextField(
                        value = editGameName,
                        onValueChange = { editGameName = it },
                        label = stringResource(R.string.label_project)
                    )
                    AppTextField(
                        value = editGameVersion,
                        onValueChange = { editGameVersion = it },
                        label = stringResource(R.string.label_version)
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AppButton(
                            onClick = { navController.navigate(Screen.FolderPicker.createRoute("edit_icon_picker", "image")) },
                            modifier = Modifier.weight(1f),
                            cornerRadius = 12.dp,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ) {
                            Text(if (editGameIcon != null) stringResource(R.string.icon_selected) else stringResource(R.string.btn_choose_icon), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                        AppButton(
                            onClick = { navController.navigate(Screen.FolderPicker.createRoute("edit_bg_picker", "image")) },
                            modifier = Modifier.weight(1f),
                            cornerRadius = 12.dp,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ) {
                            Text(if (editGameBg != null) stringResource(R.string.icon_selected) else stringResource(R.string.btn_choose_bg), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (showDeleteGameDialog) {
            AppBottomPanel(
                onDismissRequest = { showDeleteGameDialog = false },
                title = stringResource(R.string.game_delete_confirm_title),
                icon = painterResource(R.drawable.ic_delete),
                buttons = { dismiss ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AppButton(
                            onClick = { dismiss() },
                            text = stringResource(R.string.action_cancel),
                            modifier = Modifier.weight(1f),
                            cornerRadius = 16.dp,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        AppButton(
                            onClick = {
                                dismiss()
                                navController.popBackStack()
                                viewModel.removeProject(project)
                            },
                            text = stringResource(R.string.action_delete),
                            modifier = Modifier.weight(1f),
                            cornerRadius = 16.dp,
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    }
                }
            ) {
                AppText(
                    text = stringResource(R.string.game_delete_confirm_desc, project.name),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
        }
    }
    }
}

@Composable
private fun ActionSquareCard(
    title: String,
    subtitle: String,
    icon: Painter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    blurState: AppBlurState,
    blurActive: Boolean
) {
    AppCard(
        onClick = onClick,
        modifier = modifier
            .aspectRatio(1f)
            .appBlurEffect(
                state = blurState,
                shape = RoundedCornerShape(24.dp),
                blurRadius = 20.dp,
                tint = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.4f),
                forceInvalidation = true
            ),
        containerColor = if (blurActive) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(24.dp),
        elevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                AppIcon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            }
            Column {
                AppText(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(4.dp))
                AppText(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
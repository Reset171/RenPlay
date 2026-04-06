package ru.reset.renplay.ui.library

import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.BiasAlignment
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.renpy.android.PythonSDLActivity
import ru.reset.renplay.R
import ru.reset.renplay.domain.models.Project
import ru.reset.renplay.di.AppViewModelProvider
import ru.reset.renplay.ui.components.layout.*
import ru.reset.renplay.ui.components.inputs.*
import ru.reset.renplay.ui.components.feedback.*
import ru.reset.renplay.ui.components.typography.*
import ru.reset.renplay.ui.components.appbar.*
import ru.reset.renplay.ui.components.icons.*
import ru.reset.renplay.ui.navigation.Screen
import ru.reset.renplay.ui.settings.SettingsViewModel
import ru.reset.renplay.ui.settings.components.*
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun LibraryScreen(
    navController: NavController,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = context as androidx.activity.ComponentActivity

    val viewModel: LibraryViewModel = viewModel(viewModelStoreOwner = activity, factory = AppViewModelProvider.Factory)
    val settingsViewModel: SettingsViewModel = viewModel(viewModelStoreOwner = activity, factory = AppViewModelProvider.Factory)

    val appToast = ru.reset.renplay.ui.components.feedback.LocalAppToast.current

    val useGameDetailsScreen by settingsViewModel.useGameDetailsScreen.collectAsState()
    val advancedAnimationsEnabled by settingsViewModel.advancedAnimationsEnabled.collectAsState()

    val projectsList by viewModel.projectsList.collectAsState()
    val filteredProjects by viewModel.filteredProjects.collectAsState()
    val iconCache by viewModel.iconCache.collectAsState()
    val isGridView by viewModel.isGridView.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()

    var isSearchActive by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    var showAddGamePanel by remember { mutableStateOf(false) }
    var newGamePath by remember { mutableStateOf("") }
    var newGameName by remember { mutableStateOf("") }
    var newGameVersion by remember { mutableStateOf("") }
    var newGameCustomIcon by remember { mutableStateOf<String?>(null) }

    val availableEngines = remember { viewModel.engineManager.getInstalledEngines() }
    var selectedEngine by remember { mutableStateOf(availableEngines.firstOrNull()?.version) }
    var showEngineSelectorDialog by remember { mutableStateOf(false) }

    var activeProjectId by remember { mutableStateOf<String?>(null) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                activeProjectId = null
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val onGameClick: (Project) -> Unit = { project ->
        if (navController.currentDestination?.route == Screen.Library.route) {
            activeProjectId = project.id
            if (useGameDetailsScreen) {
                navController.navigate(Screen.GameDetails.createRoute(project.id))
            } else {
                val gameSubFolder = File(project.path, "game")
                if (!gameSubFolder.exists() || !gameSubFolder.isDirectory) {
                    appToast.show(context.getString(R.string.error_no_game_folder), R.drawable.ic_info)
                } else {
                    val engine = viewModel.engineManager.getEngine(project.engineVersion)
                    if (engine != null) {
                        val intent = Intent(context, PythonSDLActivity::class.java).apply {
                            putExtra("GAME_PATH", project.path)
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
        }
    }

    val currentBackStackEntry = navController.currentBackStackEntry

    val iconReturnedPath by currentBackStackEntry?.savedStateHandle?.getStateFlow("custom_icon", "")?.collectAsState(initial = "") ?: remember { mutableStateOf("") }
    LaunchedEffect(iconReturnedPath) {
        if (iconReturnedPath.isNotEmpty()) {
            newGameCustomIcon = iconReturnedPath
            currentBackStackEntry?.savedStateHandle?.remove<String>("custom_icon")
        }
    }
    val returnedPath by currentBackStackEntry?.savedStateHandle?.getStateFlow("base_path", "")?.collectAsState(initial = "") ?: remember { mutableStateOf("") }

    LaunchedEffect(returnedPath) {
        if (returnedPath.isNotEmpty()) {
            newGamePath = returnedPath
            withContext(Dispatchers.IO) {
                val meta = ru.reset.renplay.utils.RenpyProjectParser.parse(returnedPath)
                val parsedName = meta.name ?: File(returnedPath).name
                val parsedVersion = meta.version ?: "1.0"

                val assets = ru.reset.renplay.utils.GameAssetExtractor.getGameAssets(context, returnedPath)
                var parsedIcon = assets.iconPath

                if (parsedIcon == null && meta.iconRelPath != null) {
                    val iconFile = File(returnedPath, "game/" + meta.iconRelPath)
                    if (iconFile.exists()) {
                        parsedIcon = iconFile.absolutePath
                    }
                }

                val autoEngine = viewModel.engineManager.getEngine(meta.scriptVersion)

                withContext(Dispatchers.Main) {
                    selectedEngine = autoEngine?.version
                    newGameName = parsedName
                    newGameVersion = parsedVersion
                    newGameCustomIcon = parsedIcon
                    showAddGamePanel = true
                    currentBackStackEntry?.savedStateHandle?.remove<String>("base_path")
                }
            }
        }
    }

    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val scrollProgress by remember {
        derivedStateOf {
            if (gridState.firstVisibleItemIndex > 0) {
                1f
            } else {
                (gridState.firstVisibleItemScrollOffset / 80f).coerceIn(0f, 1f)
            }
        }
    }
    val pillAlpha = scrollProgress
    val titleAlpha = 1f - scrollProgress
    val titleOffsetY = (-20).dp * scrollProgress
    val buttonBgColor = androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0f), scrollProgress)

    val blurActive = LocalAppBlurState.current.blurEnabled
    val screenBlurState = rememberAppBlurState()
    screenBlurState.blurEnabled = blurActive

    CompositionLocalProvider(LocalAppBlurState provides screenBlurState) {
        AppScaffold { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .appBlurSource(screenBlurState)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (projectsList.isEmpty()) {
                AppEmptyState(
                    icon = painterResource(R.drawable.ic_folder_open),
                    title = stringResource(R.string.main_empty_project_title),
                    description = stringResource(R.string.main_empty_project_desc)
                )
            } else if (filteredProjects.isEmpty()) {
                AppEmptyState(
                    icon = painterResource(R.drawable.ic_description),
                    title = stringResource(R.string.library_empty_search)
                )
            } else {
                MorphingLibraryList(
                    projects = filteredProjects,
                    gridState = gridState,
                    topPadding = paddingValues.calculateTopPadding() + 80.dp,
                    iconCache = iconCache,
                    isGridView = isGridView,
                    sortOrder = sortOrder,
                    searchQuery = searchQuery,
                    advancedAnimationsEnabled = advancedAnimationsEnabled,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    activeProjectId = activeProjectId,
                    onMove = { fromId, toId -> viewModel.moveProject(fromId, toId) },
                    onGameClick = onGameClick,
                    onInfoClick = { project ->
                        activeProjectId = project.id
                        navController.navigate(Screen.GameDetails.createRoute(project.id))
                    },
                    useGameDetailsScreen = useGameDetailsScreen
                )
            }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            ) {
                val normalUiAlpha by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (isSearchActive) 0f else 1f, 
                    label = "normalUiAlpha"
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                        .graphicsLayer { alpha = normalUiAlpha },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.main_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = titleAlpha),
                        modifier = Modifier.offset(y = titleOffsetY).graphicsLayer { alpha = titleAlpha }
                    )
                    Spacer(Modifier.weight(1f))

                    Surface(
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = if (blurActive) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f * pillAlpha),
                        modifier = Modifier
                            .appBlurEffect(
                                state = screenBlurState,
                                shape = androidx.compose.foundation.shape.CircleShape,
                                blurRadius = 16.dp * pillAlpha,
                                tint = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f * pillAlpha),
                                forceInvalidation = true
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp * scrollProgress, vertical = 4.dp * scrollProgress),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val leftShape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp, topEnd = 6.dp, bottomEnd = 6.dp)
                            val middleShape = RoundedCornerShape(6.dp)
                            val rightShape = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 24.dp, bottomEnd = 24.dp)

                            AppIconButton(
                                onClick = { isSearchActive = true },
                                backgroundColor = buttonBgColor,
                                size = 42.dp,
                                iconSize = 20.dp,
                                shape = leftShape
                            ) {
                                AppIcon(painterResource(R.drawable.ic_search), null)
                            }

                            Spacer(Modifier.width(2.dp))

                            AppIconButton(
                                onClick = { showSortMenu = true },
                                backgroundColor = buttonBgColor,
                                size = 42.dp,
                                iconSize = 20.dp,
                                shape = middleShape
                            ) {
                                AppIcon(painterResource(R.drawable.ic_sort), null)
                            }

                            Spacer(Modifier.width(2.dp))

                            AppIconButton(
                                onClick = { viewModel.toggleGridView() },
                                backgroundColor = buttonBgColor,
                                size = 42.dp,
                                iconSize = 20.dp,
                                shape = middleShape
                            ) {
                                androidx.compose.animation.AnimatedContent(
                                    targetState = isGridView,
                                    transitionSpec = {
                                        val springSpec = androidx.compose.animation.core.spring<Float>(
                                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                                            stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                                        )
                                        (androidx.compose.animation.scaleIn(initialScale = 0f, animationSpec = springSpec) + androidx.compose.animation.fadeIn()) togetherWith
                                        (androidx.compose.animation.scaleOut(targetScale = 0f, animationSpec = springSpec) + androidx.compose.animation.fadeOut())
                                    },
                                    label = "IconAnim"
                                ) { grid ->
                                    AppIcon(painterResource(if (grid) R.drawable.ic_view_list else R.drawable.ic_grid_view), null)
                                }
                            }

                            Spacer(Modifier.width(2.dp))

                            AppIconButton(
                                onClick = { navController.navigate(Screen.FolderPicker.createRoute("base_path")) },
                                backgroundColor = buttonBgColor,
                                size = 42.dp,
                                iconSize = 20.dp,
                                shape = middleShape
                            ) {
                                AppIcon(painterResource(R.drawable.ic_add), null)
                            }

                            Spacer(Modifier.width(2.dp))

                            AppIconButton(
                                onClick = { navController.navigate(Screen.Settings.route) },
                                backgroundColor = buttonBgColor,
                                size = 42.dp,
                                iconSize = 20.dp,
                                shape = rightShape
                            ) {
                                AppIcon(painterResource(R.drawable.ic_settings), null)
                            }
                        }
                    }
                }

                AppSearchAppBar(
                    query = searchQuery,
                    onQueryChange = { viewModel.updateSearchQuery(it) },
                    isActive = isSearchActive,
                    onActiveChange = { 
                        isSearchActive = it
                        if (!it) viewModel.updateSearchQuery("")
                    },
                    placeholder = stringResource(R.string.library_search_hint)
                )
            }
        }

        if (showSortMenu) {
            AppBottomPanel(
                onDismissRequest = { showSortMenu = false },
                title = stringResource(R.string.action_sort),
                icon = painterResource(R.drawable.ic_sort)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                    SettingsItem(
                        title = stringResource(R.string.sort_newest),
                        icon = null,
                        onClick = {
                            viewModel.updateSortOrder(LibraryViewModel.SortOrder.NEWEST_FIRST)
                            showSortMenu = false
                        },
                        showDivider = true
                    )
                    SettingsItem(
                        title = stringResource(R.string.sort_alphabetical),
                        icon = null,
                        onClick = {
                            viewModel.updateSortOrder(LibraryViewModel.SortOrder.ALPHABETICAL)
                            showSortMenu = false
                        },
                        showDivider = true
                    )
                    SettingsItem(
                        title = stringResource(R.string.sort_manual),
                        icon = null,
                        onClick = {
                            viewModel.updateSortOrder(LibraryViewModel.SortOrder.MANUAL)
                            showSortMenu = false
                            appToast.show(context.getString(R.string.sort_manual_hint), R.drawable.ic_info)
                        },
                        showDivider = false
                    )
                }
            }
        }

        if (showAddGamePanel) {
            AppBottomPanel(
                onDismissRequest = { showAddGamePanel = false },
                title = stringResource(R.string.add_game_title),
                icon = painterResource(R.drawable.ic_gamepad),
                buttons = { dismiss ->
                    AppButton(
                        onClick = {
                            viewModel.addProject(
                                name = newGameName,
                                version = newGameVersion,
                                path = newGamePath,
                                customIconPath = newGameCustomIcon,
                                engineVersion = selectedEngine,
                                application = context.applicationContext as Application
                            )
                            dismiss()
                        },
                        text = stringResource(R.string.add_game_button),
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 16.dp
                    )
                }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    AppTextField(
                        value = newGameName,
                        onValueChange = { newGameName = it },
                        label = stringResource(R.string.label_project)
                    )
                    AppTextField(
                        value = newGameVersion,
                        onValueChange = { newGameVersion = it },
                        label = stringResource(R.string.label_version)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val previewBitmap = remember(newGameCustomIcon) {
                            newGameCustomIcon?.let { path ->
                                try {
                                    android.graphics.BitmapFactory.decodeFile(path)
                                } catch (e: Exception) { null }
                            }
                        }

                        ProjectIcon(
                            bitmap = previewBitmap,
                            projectName = "",
                            modifier = Modifier.size(56.dp),
                            shape = RoundedCornerShape(14.dp)
                        )

                        Spacer(Modifier.width(16.dp))

                        AppButton(
                            onClick = { navController.navigate(Screen.FolderPicker.createRoute("custom_icon", "image")) },
                            modifier = Modifier.weight(1f),
                            cornerRadius = 16.dp
                        ) {
                            Text(
                                text = if (newGameCustomIcon != null) stringResource(R.string.icon_selected) else stringResource(R.string.btn_choose_icon),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

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
                                selectedEngine = engine.version
                                showEngineSelectorDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = if (selectedEngine == engine.version) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            elevation = 0.dp,
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                Text(
                                    text = stringResource(R.string.engine_version_format, engine.version),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (selectedEngine == engine.version) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selectedEngine == engine.version) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

private val CardGradientBrush = Brush.verticalGradient(
    colors = listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.85f))
)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SeamlessGameCard(
    project: Project,
    iconCache: Map<String, Bitmap>,
    isGridView: Boolean,
    advancedAnimationsEnabled: Boolean,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    activeProjectId: String?,
    useGameDetailsScreen: Boolean,
    searchQuery: String = "",
    isDragging: Boolean = false,
    onClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val gridHeight = (screenWidth - 48.dp) / 2
    val listHeight = 88.dp

    if (advancedAnimationsEnabled) {
        val scale by animateFloatAsState(if (isDragging) 1.05f else 1f, label = "drag_scale")
        val elevation by animateDpAsState(if (isDragging) 12.dp else if (isGridView) 4.dp else 2.dp, label = "drag_elevation")

        val transition = updateTransition(targetState = isGridView, label = "card_morph")

        val cardRadius by transition.animateDp(label = "card_radius") { if (it) 24.dp else 20.dp }
        val containerColor by transition.animateColor(label = "container_color") { 
            if (it) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer 
        }
        val height by transition.animateDp(label = "height") { if (it) gridHeight else listHeight }
        val imageSize by transition.animateDp(label = "img_size") { if (it) gridHeight else 64.dp }
        val imagePadding by transition.animateDp(label = "img_padding") { if (it) 0.dp else 12.dp }
        val imageRadius by transition.animateDp(label = "img_radius") { if (it) 24.dp else 14.dp }
        val gradientAlpha by transition.animateFloat(label = "gradient") { if (it) 1f else 0f }
        val verticalBias by transition.animateFloat(label = "v_bias") { if (it) 1f else 0f }
        val textStartPadding by transition.animateDp(label = "text_start") { if (it) 16.dp else 92.dp }
        val textBottomPadding by transition.animateDp(label = "text_bottom") { if (it) 16.dp else 0.dp }
        val titleColor by transition.animateColor(label = "title_color") { 
            if (it) Color.White else MaterialTheme.colorScheme.onSurface 
        }
        val subTitleColor by transition.animateColor(label = "sub_color") { 
            if (it) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant 
        }
        val borderColor by transition.animateColor(label = "border_color") {
            if (it) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f) else Color.Transparent
        }

        val infoVerticalBias by transition.animateFloat(label = "info_v") { if (it) -1f else 0f }
        val infoEndPadding by transition.animateDp(label = "info_end") { if (it) 8.dp else 16.dp }
        val infoTopPadding by transition.animateDp(label = "info_top") { if (it) 8.dp else 0.dp }

        GameCardContent(
            project = project, iconCache = iconCache, isGridView = isGridView,
            advancedAnimationsEnabled = true, sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope, scale = scale, elevation = elevation,
            cardRadius = cardRadius, containerColor = containerColor, height = height,
            imageSize = imageSize, imagePadding = imagePadding, imageRadius = imageRadius,
            gradientAlpha = gradientAlpha, verticalBias = verticalBias, textStartPadding = textStartPadding,
            textBottomPadding = textBottomPadding, titleColor = titleColor, subTitleColor = subTitleColor,
            borderColor = borderColor,
            infoVerticalBias = infoVerticalBias, infoEndPadding = infoEndPadding, infoTopPadding = infoTopPadding,
            activeProjectId = activeProjectId, useGameDetailsScreen = useGameDetailsScreen,
            searchQuery = searchQuery,
            onClick = onClick, onInfoClick = onInfoClick
        )
    } else {
        GameCardContent(
            project = project, iconCache = iconCache, isGridView = isGridView,
            advancedAnimationsEnabled = false, sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope, 
            scale = if (isDragging) 1.05f else 1f, 
            elevation = if (isDragging) 12.dp else if (isGridView) 4.dp else 2.dp,
            cardRadius = if (isGridView) 24.dp else 20.dp, 
            containerColor = if (isGridView) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer, 
            height = if (isGridView) gridHeight else listHeight,
            imageSize = if (isGridView) gridHeight else 64.dp, 
            imagePadding = if (isGridView) 0.dp else 12.dp, 
            imageRadius = if (isGridView) 24.dp else 14.dp,
            gradientAlpha = if (isGridView) 1f else 0f, 
            verticalBias = if (isGridView) 1f else 0f, 
            textStartPadding = if (isGridView) 16.dp else 92.dp,
            textBottomPadding = if (isGridView) 16.dp else 0.dp, 
            titleColor = if (isGridView) Color.White else MaterialTheme.colorScheme.onSurface, 
            subTitleColor = if (isGridView) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
            borderColor = if (isGridView) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f) else Color.Transparent,
            infoVerticalBias = if (isGridView) -1f else 0f,
            infoEndPadding = if (isGridView) 8.dp else 16.dp,
            infoTopPadding = if (isGridView) 8.dp else 0.dp,
            activeProjectId = activeProjectId,
            useGameDetailsScreen = useGameDetailsScreen,
            searchQuery = searchQuery,
            onClick = onClick,
            onInfoClick = onInfoClick
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun GameCardContent(
    project: Project,
    iconCache: Map<String, Bitmap>,
    isGridView: Boolean,
    advancedAnimationsEnabled: Boolean,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    scale: Float,
    elevation: androidx.compose.ui.unit.Dp,
    cardRadius: androidx.compose.ui.unit.Dp,
    containerColor: Color,
    height: androidx.compose.ui.unit.Dp,
    imageSize: androidx.compose.ui.unit.Dp,
    imagePadding: androidx.compose.ui.unit.Dp,
    imageRadius: androidx.compose.ui.unit.Dp,
    gradientAlpha: Float,
    verticalBias: Float,
    textStartPadding: androidx.compose.ui.unit.Dp,
    textBottomPadding: androidx.compose.ui.unit.Dp,
    titleColor: Color,
    subTitleColor: Color,
    borderColor: Color,
    infoVerticalBias: Float,
    infoEndPadding: androidx.compose.ui.unit.Dp,
    infoTopPadding: androidx.compose.ui.unit.Dp,
    activeProjectId: String?,
    useGameDetailsScreen: Boolean,
    searchQuery: String = "",
    onClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    val useSharedTransition = advancedAnimationsEnabled && (activeProjectId == null || activeProjectId == project.id)

    val blurActive = LocalAppBlurState.current.blurEnabled
    val cardBlurState = rememberAppBlurState()
    cardBlurState.blurEnabled = blurActive

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        AppCard(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().height(height),
            containerColor = containerColor,
            shape = RoundedCornerShape(cardRadius),
            elevation = elevation,
            border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxSize().appBlurSource(cardBlurState)) {
                    with(sharedTransitionScope) {
                        ProjectIcon(
                        bitmap = iconCache[project.customIconPath ?: project.iconPath ?: ""],
                        projectName = project.name,
                        modifier = Modifier
                            .padding(start = imagePadding, top = imagePadding)
                            .size(imageSize)
                            .then(
                                if (useSharedTransition) {
                                    Modifier.sharedElement(
                                        rememberSharedContentState(key = "image-${project.id}"),
                                        animatedVisibilityScope = animatedVisibilityScope,
                                        boundsTransform = { _, _ -> tween(durationMillis = 350, easing = FastOutSlowInEasing) }
                                    )
                                } else Modifier
                            ),
                        shape = RoundedCornerShape(imageRadius)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = gradientAlpha }
                        .background(CardGradientBrush)
                )

                Column(
                    modifier = Modifier
                        .align(BiasAlignment(-1f, verticalBias))
                        .padding(start = textStartPadding, bottom = textBottomPadding, end = 16.dp)
                ) {
                    with(sharedTransitionScope) {
                        val primaryColor = MaterialTheme.colorScheme.primary
                        val annotatedName = remember(project.name, searchQuery, primaryColor) {
                            androidx.compose.ui.text.buildAnnotatedString {
                                val str = project.name
                                append(str)
                                if (searchQuery.isNotBlank()) {
                                    val index = str.indexOf(searchQuery, ignoreCase = true)
                                    if (index >= 0) {
                                        addStyle(
                                            style = androidx.compose.ui.text.SpanStyle(color = primaryColor),
                                            start = index,
                                            end = index + searchQuery.length
                                        )
                                    }
                                }
                            }
                        }

                        Text(
                            text = annotatedName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = titleColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = if (useSharedTransition) {
                                Modifier.sharedBounds(
                                    rememberSharedContentState(key = "title-${project.id}"),
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(),
                                    boundsTransform = { _, _ -> tween(durationMillis = 350, easing = FastOutSlowInEasing) }
                                )
                            } else Modifier
                        )
                    }
                    if (project.version.isNotEmpty()) {
                        Spacer(Modifier.height(if (isGridView) 2.dp else 4.dp))
                        with(sharedTransitionScope) {
                            AppText(
                                text = stringResource(R.string.version_prefix, project.version),
                                style = if (isGridView) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall,
                                color = subTitleColor,
                                modifier = if (useSharedTransition) {
                                    Modifier.sharedBounds(
                                        rememberSharedContentState(key = "version-${project.id}"),
                                        animatedVisibilityScope = animatedVisibilityScope,
                                        resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(),
                                        boundsTransform = { _, _ -> tween(durationMillis = 350, easing = FastOutSlowInEasing) }
                                    )
                                } else Modifier
                            )
                        }
                    }
                }
                }

                if (!useGameDetailsScreen && activeProjectId != project.id) {
                    AppIconButton(
                        onClick = onInfoClick,
                        modifier = Modifier
                            .align(androidx.compose.ui.BiasAlignment(1f, infoVerticalBias))
                            .padding(end = infoEndPadding, top = infoTopPadding)
                            .zIndex(1f)
                            .appBlurEffect(
                                state = cardBlurState,
                                shape = androidx.compose.foundation.shape.CircleShape,
                                blurRadius = 16.dp,
                                tint = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f),
                                forceInvalidation = true
                            ),
                        backgroundColor = if (blurActive) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.8f),
                        iconTint = titleColor,
                        size = 36.dp,
                        iconSize = 20.dp,
                        shape = androidx.compose.foundation.shape.CircleShape
                    ) {
                        AppIcon(painterResource(R.drawable.ic_info), null)
                    }
                }
            }
        }
    }
}



@Composable
fun ProjectIcon(
    bitmap: Bitmap?,
    projectName: String,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp)
) {
    if (bitmap != null) {
        val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
        Image(
            bitmap = imageBitmap,
            contentDescription = projectName,
            modifier = modifier.clip(shape),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center
        ) {
            AppIcon(
                painter = painterResource(id = R.drawable.ic_no_icon),
                contentDescription = projectName,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

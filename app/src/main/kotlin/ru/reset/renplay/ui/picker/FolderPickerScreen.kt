package ru.reset.renplay.ui.picker

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import ru.reset.renplay.R
import ru.reset.renplay.di.AppViewModelProvider
import ru.reset.renplay.ui.components.layout.*
import ru.reset.renplay.ui.components.inputs.*
import ru.reset.renplay.ui.components.feedback.*
import ru.reset.renplay.ui.components.typography.*
import ru.reset.renplay.ui.components.appbar.*
import ru.reset.renplay.ui.components.icons.*
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderPickerScreen(
    navController: NavController,
    requestKey: String,
    mode: String
) {
    val viewModel: FolderPickerViewModel = viewModel(factory = AppViewModelProvider.Factory)
    val uiState by viewModel.uiState.collectAsState()
    val hasPermission by viewModel.hasPermission.collectAsState()

    LaunchedEffect(requestKey, mode) {
        viewModel.initMode(requestKey, mode)
    }

    BackHandler {
        if (viewModel.canNavigateUp()) {
            viewModel.navigateUp()
        } else {
            navController.popBackStack()
        }
    }

    val listStates = remember { mutableMapOf<String, androidx.compose.foundation.lazy.LazyListState>() }
    val activeListState = listStates.getOrPut(uiState.path.absolutePath) { androidx.compose.foundation.lazy.LazyListState() }

    LaunchedEffect(uiState.path.absolutePath) {
        if (listStates.size > 20) {
            val keysToDrop = listStates.keys.take(10)
            keysToDrop.forEach { listStates.remove(it) }
        }
    }

    val density = androidx.compose.ui.platform.LocalDensity.current
    val maxScrollPx = remember(density) { with(density) { 56.dp.toPx() } }
    val targetScrollProgress by remember(activeListState) { 
        derivedStateOf { 
            if (activeListState.firstVisibleItemIndex > 0) 1f 
            else (activeListState.firstVisibleItemScrollOffset / maxScrollPx).coerceIn(0f, 1f) 
        } 
    }
    val scrollProgress by animateFloatAsState(
        targetValue = targetScrollProgress,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "scrollProgressAnim"
    )

    val blurActive = LocalAppBlurState.current.blurEnabled
    val localBlurState = rememberAppBlurState()
    localBlurState.blurEnabled = blurActive

    val titleRes = when (mode) {
        "zip" -> stringResource(R.string.picker_title_zip)
        "image" -> stringResource(R.string.picker_title_image)
        else -> stringResource(R.string.picker_title)
    }

    val buttonBgColor = if (blurActive) Color.Transparent else androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0f), MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f), scrollProgress)

    CompositionLocalProvider(LocalAppBlurState provides localBlurState) {
        AppScaffold(
            topBar = {
            RenPlayAppBar(
                title = titleRes,
                scrollProgress = scrollProgress,
                navigationIcon = {
                    AppIconButton(
                        onClick = { navController.popBackStack() },
                        backgroundColor = buttonBgColor,
                        shape = androidx.compose.foundation.shape.CircleShape,
                        modifier = Modifier.appBlurEffect(
                            state = localBlurState,
                            shape = androidx.compose.foundation.shape.CircleShape,
                            blurRadius = 16.dp * scrollProgress,
                            tint = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f * scrollProgress),
                            forceInvalidation = true
                        )
                    ) {
                        AppIcon(painterResource(R.drawable.ic_close), null)
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .appBlurSource(localBlurState)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                if (!hasPermission) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AppText(
                        stringResource(R.string.picker_access_denied),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                AnimatedContent(
                    targetState = uiState,
                    contentKey = { it.transitionId },
                    transitionSpec = {
                        val isMovingForward = targetState.path.absolutePath.length > initialState.path.absolutePath.length
                        val enterSpec = spring<Float>(stiffness = Spring.StiffnessMediumLow)
                        val exitSpec = tween<Float>(durationMillis = 150)

                        if (isMovingForward) {
                            (slideInHorizontally(initialOffsetX = { it }, animationSpec = IntOffsetSpring) + fadeIn(enterSpec))
                                .togetherWith(slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = IntOffsetSpring) + fadeOut(exitSpec))
                        } else {
                            (slideInHorizontally(initialOffsetX = { -it }, animationSpec = IntOffsetSpring) + fadeIn(enterSpec))
                                .togetherWith(slideOutHorizontally(targetOffsetX = { it / 3 }, animationSpec = IntOffsetSpring) + fadeOut(exitSpec))
                        }
                    },
                    label = "FileListAnimation"
                ) { currentState ->
                    if (currentState.items.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            AppText(
                                stringResource(R.string.picker_empty),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listStates.getOrPut(currentState.path.absolutePath) { androidx.compose.foundation.lazy.LazyListState() },
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 124.dp, bottom = padding.calculateBottomPadding() + 24.dp)
                        ) {
                            items(items = currentState.items, key = { it.file.absolutePath }) { item ->
                                PickerItemRow(
                                    item = item,
                                    onClick = { 
                                        if (item.isGame || !item.isDirectory) {
                                            navController.previousBackStackEntry?.savedStateHandle?.set(requestKey, item.file.absolutePath)
                                            navController.popBackStack()
                                        } else {
                                            viewModel.navigateTo(item.file) 
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
            }

            val pathInteractionSource = remember { MutableInteractionSource() }
            val pathScale = remember { Animatable(1f) }
            
            LaunchedEffect(pathInteractionSource) {
                pathInteractionSource.interactions.collect { interaction ->
                    when (interaction) {
                        is PressInteraction.Press -> {
                            launch {
                                pathScale.animateTo(
                                    0.96f,
                                    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                                )
                            }
                        }
                        is PressInteraction.Release, is PressInteraction.Cancel -> {
                            launch {
                                pathScale.animateTo(
                                    1f,
                                    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                                )
                            }
                        }
                    }
                }
            }

            val pillTopPadding = androidx.compose.ui.unit.lerp(60.dp, 4.dp, scrollProgress)
            val pillStartPadding = androidx.compose.ui.unit.lerp(16.dp, 56.dp, scrollProgress)
            val pillEndPadding = androidx.compose.ui.unit.lerp(16.dp, 8.dp, scrollProgress)
            val pillVerticalInnerPadding = androidx.compose.ui.unit.lerp(12.dp, 8.dp, scrollProgress)

            AppCard(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = pillTopPadding, start = pillStartPadding, end = pillEndPadding)
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = pathScale.value
                        scaleY = pathScale.value
                    }
                    .appBlurEffect(
                        state = localBlurState,
                        shape = RoundedCornerShape(24.dp),
                        blurRadius = 16.dp,
                        tint = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                        forceInvalidation = true
                    )
                    .clickable(
                        interactionSource = pathInteractionSource,
                        indication = null,
                        onClick = {}
                    ),
                containerColor = if (blurActive) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(24.dp),
                elevation = if (blurActive) 0.dp else 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = pillVerticalInnerPadding),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIconButton(
                        onClick = { viewModel.navigateUp() },
                        enabled = viewModel.canNavigateUp(),
                        size = 32.dp,
                        iconSize = 20.dp,
                        backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                    ) {
                        AppIcon(painterResource(R.drawable.ic_arrow_back), null)
                    }
                    Spacer(Modifier.width(12.dp))

                    AnimatedContent(
                        targetState = uiState,
                        modifier = Modifier.weight(1f),
                        transitionSpec = {
                            val isMovingForward = targetState.path.absolutePath.length > initialState.path.absolutePath.length
                            if (isMovingForward) {
                                (slideInVertically { height -> height } + fadeIn()).togetherWith(slideOutVertically { height -> -height } + fadeOut())
                            } else {
                                (slideInVertically { height -> -height } + fadeIn()).togetherWith(slideOutVertically { height -> height } + fadeOut())
                            }
                        },
                        label = "PathAnimation"
                    ) { state ->
                        AppStartEllipsisText(
                            text = state.path.absolutePath,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

        }
    }
    }
}

private val IntOffsetSpring = spring<IntOffset>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessLow
)

@Composable
fun PickerItemRow(
    item: PickerItem,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    scope.launch {
                        scale.animateTo(
                            0.96f,
                            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                        )
                    }
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    scope.launch {
                        scale.animateTo(
                            1f,
                            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                        )
                    }
                }
            }
        }
    }

    val hasBg = item.backgroundPath != null
    val bgScale by animateFloatAsState(
        targetValue = if (hasBg) 1f else 0.8f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "bgScale"
    )
    val bgAlpha by animateFloatAsState(
        targetValue = if (hasBg) 1f else 0f,
        animationSpec = tween(350),
        label = "bgAlpha"
    )

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(0.dp),
        interactionSource = interactionSource
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (hasBg) {
                var bmp by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
                LaunchedEffect(item.backgroundPath) {
                    item.backgroundPath?.let {
                        val loaded = ru.reset.renplay.utils.GameAssetExtractor.loadBitmap(it, 600, 200)
                        bmp = loaded?.asImageBitmap()
                    }
                }
                val currentBmp = bmp
                if (currentBmp != null) {
                    Image(
                        bitmap = currentBmp,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .matchParentSize()
                            .graphicsLayer {
                                scaleX = bgScale
                                scaleY = bgScale
                                alpha = bgAlpha
                            }
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = 0.65f))
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                var iconVisible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { iconVisible = true }
                val iconScale by animateFloatAsState(
                    targetValue = if (iconVisible) 1f else 0f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "IconPop"
                )

                if (item.isGame) {
                    var iconBmp by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
                    LaunchedEffect(item.iconPath) {
                        item.iconPath?.let {
                            val loaded = ru.reset.renplay.utils.GameAssetExtractor.loadBitmap(it, 100, 100)
                            iconBmp = loaded?.asImageBitmap()
                        }
                    }
                    val currentIconBmp = iconBmp
                    if (currentIconBmp != null) {
                        Image(
                            bitmap = currentIconBmp,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(36.dp)
                                .scale(iconScale)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    } else {
                        AppIcon(
                            painter = painterResource(R.drawable.ic_gamepad),
                            contentDescription = null,
                            tint = if (hasBg) Color.White else MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(36.dp)
                                .scale(iconScale)
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                } else {
                    val isZip = item.file.extension.lowercase() == "zip"
                    val iconRes = if (item.isDirectory) R.drawable.ic_folder else if (isZip) R.drawable.ic_folder_zip else R.drawable.ic_description
                    val tint = if (item.isDirectory || isZip) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)

                    AppIcon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier
                            .size(24.dp)
                            .scale(iconScale)
                    )
                    Spacer(Modifier.width(16.dp))
                }

                AppText(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (hasBg) Color.White else if (item.isDirectory) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

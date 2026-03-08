package ru.reset.renplay.ui.picker

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderPickerScreen(
    navController: NavController,
    requestKey: String
) {
    val viewModel: FolderPickerViewModel = viewModel(factory = AppViewModelProvider.Factory)
    val uiState by viewModel.uiState.collectAsState()
    val hasPermission by viewModel.hasPermission.collectAsState()

    LaunchedEffect(requestKey) {
        viewModel.initMode(requestKey)
    }

    BackHandler {
        if (viewModel.canNavigateUp()) {
            viewModel.navigateUp()
        } else {
            navController.popBackStack()
        }
    }

    val blurActive = LocalAppBlurState.current.blurEnabled
    val localBlurState = rememberAppBlurState()
    localBlurState.blurEnabled = blurActive

    AppScaffold(
        topBar = {
            RenPlayAppBar(
                title = stringResource(R.string.picker_title),
                navigationIcon = {
                    AppIconButton(onClick = { navController.popBackStack() }) {
                        AppIcon(painterResource(R.drawable.ic_close), null)
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .appBlurSource(localBlurState)
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
                        val listState = rememberLazyListState()
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 72.dp, bottom = 100.dp)
                        ) {
                            items(items = currentState.items, key = { it.file.absolutePath }) { item ->
                                PickerItemRow(
                                    item = item,
                                    onClick = { 
                                        if (item.isDirectory) {
                                            viewModel.navigateTo(item.file) 
                                        } else {
                                            val returnPath = item.file.parentFile?.absolutePath ?: item.file.absolutePath
                                            navController.previousBackStackEntry?.savedStateHandle?.set(requestKey, returnPath)
                                            navController.popBackStack()
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

            AppCard(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 4.dp, start = 16.dp, end = 16.dp)
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
                    modifier = Modifier.padding(12.dp),
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

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = if (blurActive) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = if (blurActive) 0.dp else 3.dp,
                shadowElevation = if (blurActive) 0.dp else 6.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .appBlurEffect(
                        state = localBlurState,
                        shape = RoundedCornerShape(24.dp),
                        blurRadius = 16.dp,
                        tint = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                        forceInvalidation = true
                    )
            ) {
                Box(modifier = Modifier.padding(6.dp)) {
                    AppButton(
                        text = stringResource(R.string.picker_select_btn),
                        onClick = {
                            navController.previousBackStackEntry?.savedStateHandle?.set(requestKey, uiState.path.absolutePath)
                            navController.popBackStack()
                        },
                        cornerRadius = 16.dp
                    )
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val iconRes = if (item.isDirectory) R.drawable.ic_folder else R.drawable.ic_description
            val tint = if (item.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            
            var iconVisible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { iconVisible = true }
            val iconScale by animateFloatAsState(
                targetValue = if (iconVisible) 1f else 0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                label = "IconPop"
            )

            AppIcon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = tint,
                modifier = Modifier
                    .size(24.dp)
                    .scale(iconScale)
            )
            
            Spacer(Modifier.width(16.dp))
            
            AppText(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (item.isDirectory) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
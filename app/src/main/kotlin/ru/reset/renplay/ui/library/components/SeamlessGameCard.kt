package ru.reset.renplay.ui.library.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import ru.reset.renplay.R
import ru.reset.renplay.domain.models.Project
import ru.reset.renplay.ui.components.feedback.LocalAppBlurState
import ru.reset.renplay.ui.components.feedback.appBlurEffect
import ru.reset.renplay.ui.components.feedback.appBlurSource
import ru.reset.renplay.ui.components.feedback.rememberAppBlurState
import ru.reset.renplay.ui.components.icons.AppIcon
import ru.reset.renplay.ui.components.icons.AppIconButton
import ru.reset.renplay.ui.components.layout.AppCard
import ru.reset.renplay.ui.components.typography.AppText

private val CardGradientBrush = Brush.verticalGradient(
    colors = listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.85f))
)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SeamlessGameCard(
    project: Project,
    iconCache: Map<String, Bitmap>,
    bgCache: Map<String, Bitmap>,
    isGridView: Boolean,
    advancedAnimationsEnabled: Boolean,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    activeProjectId: String?,
    useGameDetailsScreen: Boolean,
    showListBg: Boolean,
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
        val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)

        val infoVerticalBias by transition.animateFloat(label = "info_v") { if (it) -1f else 0f }
        val infoEndPadding by transition.animateDp(label = "info_end") { if (it) 8.dp else 16.dp }
        val infoTopPadding by transition.animateDp(label = "info_top") { if (it) 8.dp else 0.dp }
        val rightImageAlpha by transition.animateFloat(label = "right_alpha") { if (it) 0f else 1f }

        GameCardContent(
            project = project, iconCache = iconCache, bgCache = bgCache, isGridView = isGridView,
            advancedAnimationsEnabled = true, sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope, scale = scale, elevation = elevation,
            cardRadius = cardRadius, containerColor = containerColor, height = height,
            imageSize = imageSize, imagePadding = imagePadding, imageRadius = imageRadius,
            gradientAlpha = gradientAlpha, verticalBias = verticalBias, textStartPadding = textStartPadding,
            textBottomPadding = textBottomPadding, titleColor = titleColor, subTitleColor = subTitleColor,
            borderColor = borderColor,
            infoVerticalBias = infoVerticalBias, infoEndPadding = infoEndPadding, infoTopPadding = infoTopPadding,
            rightImageAlpha = rightImageAlpha,
            activeProjectId = activeProjectId, useGameDetailsScreen = useGameDetailsScreen,
            showListBg = showListBg,
            searchQuery = searchQuery,
            onClick = onClick, onInfoClick = onInfoClick
        )
    } else {
        GameCardContent(
            project = project, iconCache = iconCache, bgCache = bgCache, isGridView = isGridView,
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
            borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
            infoVerticalBias = if (isGridView) -1f else 0f,
            infoEndPadding = if (isGridView) 8.dp else 16.dp,
            infoTopPadding = if (isGridView) 8.dp else 0.dp,
            rightImageAlpha = if (isGridView) 0f else 1f,
            activeProjectId = activeProjectId,
            useGameDetailsScreen = useGameDetailsScreen,
            showListBg = showListBg,
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
    bgCache: Map<String, Bitmap>,
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
    rightImageAlpha: Float,
    activeProjectId: String?,
    useGameDetailsScreen: Boolean,
    showListBg: Boolean,
    searchQuery: String = "",
    onClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    val useSharedTransition = advancedAnimationsEnabled && (activeProjectId == null || activeProjectId == project.id)

    val blurActive = LocalAppBlurState.current.blurEnabled
    val cardBlurState = rememberAppBlurState()
    cardBlurState.blurEnabled = blurActive

    val bgPathToLoad = project.customBackgroundPath ?: project.backgroundPath?.takeIf { it.isNotBlank() }
    val iconPathToLoad = project.customIconPath ?: project.iconPath?.takeIf { it.isNotBlank() }
    val rightBgBmp = if (showListBg) {
        bgPathToLoad?.let { bgCache[it] } ?: iconPathToLoad?.let { iconCache[it] }
    } else null

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
            border = androidx.compose.foundation.BorderStroke(0.5.dp, borderColor)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxSize().appBlurSource(cardBlurState)) {
                    if (showListBg && rightImageAlpha > 0f) {
                        Box(
                            modifier = Modifier
                                .align(androidx.compose.ui.Alignment.CenterEnd)
                                .fillMaxWidth(0.45f)
                                .fillMaxHeight()
                                .graphicsLayer { alpha = rightImageAlpha }
                        ) {
                            val currentBmp = rightBgBmp
                            if (currentBmp != null) {
                                androidx.compose.foundation.Image(
                                    bitmap = currentBmp.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(
                                                containerColor,
                                                containerColor.copy(alpha = 0.3f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                            )
                        }
                    }

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
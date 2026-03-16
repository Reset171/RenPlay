package ru.reset.renplay.ui.components.inputs

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }
    var preventBounce by remember { mutableStateOf(false) }
    var isInitialized by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    val trackWidth = 42.dp
    val trackHeight = 26.dp
    val thumbPadding = 3.dp

    val thumbSizeDefault = 20.dp
    val thumbWidthStretched = 25.dp
    val thumbHeightSquashed = 15.dp
    val thumbSizeShrunken = 18.dp

    val minCenter: Dp
    val maxCenter: Dp
    val minDragCenter: Dp
    val maxDragCenter: Dp

    with(density) {
        minCenter = thumbPadding + (thumbSizeDefault / 2)
        maxCenter = trackWidth - thumbPadding - (thumbSizeDefault / 2)
        minDragCenter = thumbPadding + (thumbSizeShrunken / 2)
        maxDragCenter = trackWidth - thumbPadding - (thumbSizeShrunken / 2)
    }
    val halfway = (minCenter + maxCenter) / 2

    val thumbCenter = remember { Animatable(if (checked) maxCenter else minCenter, Dp.VectorConverter) }
    val thumbWidth = remember { Animatable(thumbSizeDefault, Dp.VectorConverter) }
    val thumbHeight = remember { Animatable(thumbSizeDefault, Dp.VectorConverter) }
    val colorProgress = remember { Animatable(if (checked) 1f else 0f) }

    val bouncySpringSpec = spring<Dp>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    )

    LaunchedEffect(Unit) {
        isInitialized = true
    }

    LaunchedEffect(checked) {
        if (!isDragging) {
            launch {
                thumbCenter.animateTo(if (checked) maxCenter else minCenter, animationSpec = bouncySpringSpec)
            }
            launch {
                colorProgress.animateTo(if (checked) 1f else 0f, animationSpec = tween(400))
            }

            if (isInitialized) {
                if (preventBounce) {
                    preventBounce = false
                } else {
                    launch {
                        val deformationJobs = listOf(
                            launch { thumbWidth.animateTo(thumbWidthStretched, tween(100)) },
                            launch { thumbHeight.animateTo(thumbHeightSquashed, tween(100)) }
                        )
                        deformationJobs.joinAll()
                        launch { thumbWidth.animateTo(thumbSizeDefault, spring(dampingRatio = Spring.DampingRatioLowBouncy)) }
                        launch { thumbHeight.animateTo(thumbSizeDefault, spring(dampingRatio = Spring.DampingRatioLowBouncy)) }
                    }
                }
            }
        }
    }

    val isDarkTheme = isSystemInDarkTheme()
    
    val trackColorUnchecked = if (isDarkTheme) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.outlineVariant
    val trackColorChecked = MaterialTheme.colorScheme.primaryContainer
    val thumbColor = Color.White

    val trackColor = lerp(trackColorUnchecked, trackColorChecked, colorProgress.value)

    Box(
        modifier = modifier
            .width(trackWidth)
            .height(trackHeight)
            .clip(CircleShape)
            .background(trackColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    onCheckedChange(!checked)
                }
            )
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        isDragging = true
                        scope.launch {
                            launch { thumbWidth.animateTo(thumbSizeShrunken, tween(100)) }
                            launch { thumbHeight.animateTo(thumbSizeShrunken, tween(100)) }
                        }
                    },
                    onDragEnd = {
                        isDragging = false
                        preventBounce = true
                        val shouldBeChecked = thumbCenter.value > halfway
                        onCheckedChange(shouldBeChecked)
                        
                        if (shouldBeChecked == checked) {
                           scope.launch {
                               thumbCenter.animateTo(if (shouldBeChecked) maxCenter else minCenter, bouncySpringSpec)
                               colorProgress.animateTo(if (shouldBeChecked) 1f else 0f, tween(300))
                           }
                        }

                        scope.launch {
                            launch { thumbWidth.animateTo(thumbSizeDefault, spring(dampingRatio = Spring.DampingRatioLowBouncy)) }
                            launch { thumbHeight.animateTo(thumbSizeDefault, spring(dampingRatio = Spring.DampingRatioLowBouncy)) }
                        }
                    },
                    onDragCancel = {
                        isDragging = false
                        scope.launch {
                             thumbCenter.animateTo(if (checked) maxCenter else minCenter, bouncySpringSpec)
                             colorProgress.animateTo(if (checked) 1f else 0f, tween(300))
                             launch { thumbWidth.animateTo(thumbSizeDefault, spring()) }
                             launch { thumbHeight.animateTo(thumbSizeDefault, spring()) }
                         }
                    },
                    onDrag = { _, dragAmount ->
                        scope.launch {
                            val newCenter = thumbCenter.value + with(density) { dragAmount.x.toDp() }
                            thumbCenter.snapTo(newCenter.coerceIn(minDragCenter, maxDragCenter))
                            
                            val currentDragProgress = ((thumbCenter.value - minCenter).value / (maxCenter - minCenter).value).coerceIn(0f, 1f)
                            colorProgress.animateTo(currentDragProgress, animationSpec = tween(durationMillis = 50))
                        }
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbCenter.value - (thumbWidth.value / 2))
                .size(width = thumbWidth.value, height = thumbHeight.value)
                .background(thumbColor, CircleShape)
        )
    }
}

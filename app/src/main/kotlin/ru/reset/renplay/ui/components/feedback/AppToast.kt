package ru.reset.renplay.ui.components.feedback

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.reset.renplay.ui.components.icons.AppIcon
import kotlin.math.abs

data class ToastData(
    val id: Long,
    val message: String,
    val iconRes: Int?,
    val duration: Long
)

@Stable
class AppToastState {
    var currentData by mutableStateOf<ToastData?>(null)
        private set

    fun show(message: String, iconRes: Int? = null, duration: Long = 3000L) {
        currentData = ToastData(System.currentTimeMillis(), message, iconRes, duration)
    }

    fun dismiss() {
        currentData = null
    }
}

val LocalAppToast = compositionLocalOf { AppToastState() }

@Composable
fun AppToastHost(state: AppToastState) {
    val currentData = state.currentData
    var displayedData by remember { mutableStateOf<ToastData?>(null) }
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(currentData) {
        if (currentData != null) {
            displayedData = currentData
            isVisible = true
            delay(currentData.duration)
            if (state.currentData == currentData) {
                state.dismiss()
            }
        } else {
            isVisible = false
        }
    }

    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(0.8f) }
    val offsetY = remember { Animatable(50f) }
    val swipeOffsetX = remember { Animatable(0f) }

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    LaunchedEffect(isVisible) {
        if (isVisible) {
            swipeOffsetX.snapTo(0f)
            launch { alpha.animateTo(1f, tween(200)) }
            launch { scale.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 300f)) }
            launch { offsetY.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 400f)) }
        } else {
            launch { alpha.animateTo(0f, tween(200)) }
            launch { scale.animateTo(0.9f, tween(200)) }
            launch { offsetY.animateTo(50f, tween(200)) }
        }
    }

    if (displayedData != null && (isVisible || alpha.value > 0f)) {
        val blurState = LocalAppBlurState.current
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        val dragAlpha = 1f - (abs(swipeOffsetX.value) / 500f).coerceIn(0f, 1f)
                        this.alpha = alpha.value * dragAlpha
                        this.scaleX = scale.value
                        this.scaleY = scale.value
                        this.translationY = with(density) { offsetY.value.dp.toPx() }
                        this.translationX = swipeOffsetX.value
                    }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (abs(swipeOffsetX.value) > size.width / 3f) {
                                    val direction = if (swipeOffsetX.value > 0) 1 else -1
                                    scope.launch {
                                        swipeOffsetX.animateTo(
                                            targetValue = size.width.toFloat() * direction,
                                            animationSpec = tween(200)
                                        )
                                        state.dismiss()
                                    }
                                } else {
                                    scope.launch {
                                        swipeOffsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
                                    }
                                }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                scope.launch {
                                    swipeOffsetX.snapTo(swipeOffsetX.value + dragAmount)
                                }
                            }
                        )
                    }
            ) {
                val shape = CircleShape
                val tintColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.45f)
                val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

                Box(
                    modifier = Modifier
                        .shadow(
                            elevation = 16.dp,
                            shape = shape,
                            spotColor = Color.Black.copy(alpha = 0.15f),
                            ambientColor = Color.Black.copy(alpha = 0.05f)
                        )
                        .clip(shape)
                        .then(
                            if (blurState.blurEnabled) {
                                Modifier.appBlurEffect(
                                    state = blurState,
                                    shape = shape,
                                    blurRadius = 24.dp,
                                    tint = tintColor,
                                    forceInvalidation = true
                                )
                            } else {
                                Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            }
                        )
                        .border(0.5.dp, borderColor, shape)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    AnimatedContent(
                        targetState = displayedData,
                        transitionSpec = {
                            (fadeIn() + scaleIn(initialScale = 0.9f)).togetherWith(fadeOut() + scaleOut(targetScale = 0.9f))
                        },
                        label = "toast_content_morph"
                    ) { data ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (data?.iconRes != null) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AppIcon(
                                        painter = painterResource(data.iconRes),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                            Text(
                                text = data?.message ?: "",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
package ru.reset.renplay.ui.components.layout

import androidx.activity.compose.PredictiveBackHandler
import ru.reset.renplay.ui.components.feedback.LocalAppBlurState
import ru.reset.renplay.ui.components.feedback.appBlurEffect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AppBottomPanel(
    onDismissRequest: () -> Unit,
    title: String? = null,
    icon: Painter? = null,
    buttons: (@Composable (dismiss: () -> Unit) -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val dismissThresholdPx = screenHeightPx * 0.25f

    val scale = remember { Animatable(0.85f) }
    val alpha = remember { Animatable(0f) }
    val translateY = remember { Animatable(screenHeightPx) }

    val bouncySpring = spring<Float>(dampingRatio = 0.75f, stiffness = 300f)
    val tightSpring = spring<Float>(dampingRatio = 0.85f, stiffness = 400f)

    LaunchedEffect(Unit) {
        coroutineScope {
            launch { scale.animateTo(1f, bouncySpring) }
            launch { alpha.animateTo(1f, tween(200)) }
            launch { translateY.animateTo(0f, bouncySpring) }
        }
    }

    val animateExit: () -> Unit = {
        scope.launch {
            launch { scale.animateTo(0.9f, tightSpring) }
            launch { alpha.animateTo(0f, tween(150)) }
            launch { translateY.animateTo(screenHeightPx, tightSpring) }
            delay(150)
            onDismissRequest()
        }
    }

    Dialog(
        onDismissRequest = { animateExit() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        PredictiveBackHandler { progress ->
            try {
                progress.collect { backEvent ->
                    val p = backEvent.progress
                    translateY.snapTo(p * dismissThresholdPx)
                    scale.snapTo(1f - (p * 0.05f))
                }
                animateExit()
            } catch (e: CancellationException) {
                scope.launch {
                    translateY.animateTo(0f, bouncySpring)
                    scale.animateTo(1f, bouncySpring)
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { animateExit() },
            contentAlignment = Alignment.BottomCenter
        ) {
            val panelShape = RoundedCornerShape(32.dp)
            val isBlurActive = LocalAppBlurState.current.blurEnabled

            val baseCardModifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 24.dp)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    this.alpha = alpha.value
                    this.translationY = translateY.value
                }

            val glassModifier = if (isBlurActive) {
                Modifier
                    .appBlurEffect(
                        shape = panelShape,
                        blurRadius = 12.dp,
                        tint = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f),
                        forceInvalidation = true
                    )
                    .border(
                        width = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        shape = panelShape
                    )
            } else Modifier

            Card(
                modifier = baseCardModifier
                    .then(glassModifier)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { },
                shape = panelShape,
                colors = CardDefaults.cardColors(
                    containerColor = if (isBlurActive) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = if (isBlurActive) 0.dp else 12.dp)
            ) {
                Column {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onDragEnd = {
                                        if (translateY.value > dismissThresholdPx) {
                                            animateExit()
                                        } else {
                                            scope.launch { translateY.animateTo(0f, bouncySpring) }
                                        }
                                    },
                                    onVerticalDrag = { _, dragAmount ->
                                        val newY = (translateY.value + dragAmount).coerceAtLeast(0f)
                                        scope.launch { translateY.snapTo(newY) }
                                    }
                                )
                            },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 12.dp, bottom = 4.dp)
                                .width(36.dp)
                                .height(5.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        )

                        if (!title.isNullOrEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (icon != null) {
                                    Icon(
                                        painter = icon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                }
                                Text(
                                    text = title,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            androidx.compose.material3.HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                            Spacer(Modifier.height(16.dp))
                        } else {
                            Spacer(Modifier.height(8.dp))
                        }
                    }

                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        content()
                    }

                    if (buttons != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 12.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            buttons(animateExit)
                        }
                    } else {
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

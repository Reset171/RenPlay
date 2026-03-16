package ru.reset.renplay.ui.settings.components

import androidx.compose.animation.EnterExitState
import ru.reset.renplay.ui.components.typography.AppText
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.lerp

@Stable
class SettingsTransitionState {
    var activeRoute by mutableStateOf<String?>(null)
    var text by mutableStateOf("")

    var sourceOffset by mutableStateOf(Offset.Unspecified)
    var targetOffset by mutableStateOf(Offset.Unspecified)

    var sourceFontSize by mutableStateOf(TextUnit.Unspecified)
    var targetFontSize by mutableStateOf(TextUnit.Unspecified)

    var activeTransition by mutableStateOf<Transition<EnterExitState>?>(null)
}

val LocalSettingsTransition = compositionLocalOf { SettingsTransitionState() }

@Composable
private fun getTransitionProgress(transition: Transition<EnterExitState>?): Float {
    if (transition == null) return 0f
    val progress by transition.animateFloat(
        transitionSpec = { tween(350, easing = FastOutSlowInEasing) },
        label = "settings_transition_progress"
    ) { state ->
        if (state == EnterExitState.Visible) 1f else 0f
    }
    return progress
}

@Composable
fun Modifier.settingsTransitionSource(route: String, fontSize: TextUnit): Modifier {
    val state = LocalSettingsTransition.current
    val progress = getTransitionProgress(state.activeTransition)
    val isAnimating = state.activeRoute == route && progress > 0.001f && progress < 0.999f
    
    return this
        .onGloballyPositioned {
            if (state.activeRoute == null || state.activeRoute == route) {
                state.sourceOffset = it.positionInRoot()
                state.sourceFontSize = fontSize
            }
        }
        .graphicsLayer { alpha = if (isAnimating) 0f else 1f }
}

@Composable
fun Modifier.settingsTransitionTarget(
    route: String, 
    fontSize: TextUnit, 
    transition: Transition<EnterExitState>? = null
): Modifier {
    val state = LocalSettingsTransition.current

    DisposableEffect(transition, route) {
        if (transition != null && state.activeRoute == route) {
            state.activeTransition = transition
        }
        onDispose {
            if (state.activeTransition == transition) {
                state.activeTransition = null
                state.activeRoute = null
            }
        }
    }

    val progress = getTransitionProgress(state.activeTransition)
    val isAnimating = state.activeRoute == route && progress > 0.001f && progress < 0.999f
    
    return this
        .onGloballyPositioned {
            if (state.activeRoute == route) {
                state.targetOffset = it.positionInRoot()
                state.targetFontSize = fontSize
            }
        }
        .graphicsLayer { alpha = if (isAnimating) 0f else 1f }
}

@Composable
fun SettingsTransitionOverlay() {
    val state = LocalSettingsTransition.current
    val progress = getTransitionProgress(state.activeTransition)

    if (state.activeRoute != null && state.sourceOffset.isSpecified && state.targetOffset.isSpecified) {
        if (progress > 0.001f && progress < 0.999f) {
            val currentX = androidx.compose.ui.util.lerp(state.sourceOffset.x, state.targetOffset.x, progress)
            val currentY = androidx.compose.ui.util.lerp(state.sourceOffset.y, state.targetOffset.y, progress)
            val currentSize = lerp(state.sourceFontSize, state.targetFontSize, progress)

            AppText(
                text = state.text,
                fontSize = currentSize,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.graphicsLayer {
                    translationX = currentX
                    translationY = currentY
                }
            )
        }
    }
}

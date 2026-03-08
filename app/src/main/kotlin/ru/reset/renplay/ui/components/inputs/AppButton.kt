package ru.reset.renplay.ui.components.inputs

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun AppButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: String? = null,
    cornerRadius: Dp = 24.dp,
    containerColor: Color? = null,
    contentColor: Color? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    elevation: Dp = 2.dp,
    content: (@Composable RowScope.() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "ButtonScale"
    )

    val defaultContainerColor = MaterialTheme.colorScheme.primaryContainer
    val actualContainerColor = containerColor ?: defaultContainerColor

    val defaultContentColor = MaterialTheme.colorScheme.onPrimaryContainer
    val actualContentColor = contentColor ?: defaultContentColor

    val pressedColor = remember(actualContainerColor) {
        Color(
            red = actualContainerColor.red * 0.9f,
            green = actualContainerColor.green * 0.9f,
            blue = actualContainerColor.blue * 0.9f,
            alpha = actualContainerColor.alpha
        )
    }

    val animatedBackgroundColor by animateColorAsState(
        targetValue = if (isPressed) pressedColor else actualContainerColor,
        animationSpec = tween(durationMillis = 75),
        label = "ButtonBackgroundColor"
    )

    Surface(
        onClick = { if (enabled) onClick() },
        modifier = modifier.graphicsLayer { 
            scaleX = scale
            scaleY = scale
            alpha = if (enabled) 1f else 0.6f 
        },
        enabled = enabled,
        shape = RoundedCornerShape(cornerRadius),
        shadowElevation = elevation,
        interactionSource = interactionSource,
        color = animatedBackgroundColor
    ) {
        Row(
            modifier = Modifier.padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (content != null) {
                content()
            } else if (text != null) {
                Text(
                    text = text,
                    color = actualContentColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
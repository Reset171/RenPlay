package ru.reset.renplay.ui.settings.components

import androidx.compose.animation.animateContentSize
import ru.reset.renplay.ui.components.layout.AppCard
import ru.reset.renplay.ui.components.icons.AppIcon
import ru.reset.renplay.ui.components.typography.AppText
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun SettingsGroup(
    title: String? = null,
    cardModifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (title != null) {
            AppText(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 24.dp, bottom = 8.dp, top = 20.dp)
            )
        }

        AppCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (title == null) 12.dp else 0.dp)
                .animateContentSize()
                .then(cardModifier),
            containerColor = containerColor,
            elevation = 0.dp
        ) {
            content()
        }
    }
}

@Composable
fun SettingsItem(
    title: String,
    description: String? = null,
    icon: Painter? = null,
    onClick: (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    showDivider: Boolean = true,
    titleModifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scope = rememberCoroutineScope()

    val pressScale = remember { Animatable(1f) }
    val clickScale = remember { Animatable(1f) }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    launch { pressScale.animateTo(0.96f, animationSpec = spring(stiffness = Spring.StiffnessLow)) }
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    launch { pressScale.animateTo(1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
                }
            }
        }
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    enabled = onClick != null,
                    onClick = {
                        onClick?.invoke()
                        scope.launch {
                            clickScale.animateTo(targetValue = 0.98f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                            clickScale.animateTo(targetValue = 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
                        }
                    }
                )
                .graphicsLayer {
                    val combinedScale = pressScale.value * clickScale.value
                    scaleX = combinedScale
                    scaleY = combinedScale
                }
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    AppIcon(
                        painter = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
            }
            
            Column(modifier = Modifier.weight(1f)) {
                AppText(
                    text = title, 
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = titleModifier
                )
                if (description != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    AppText(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            if (trailingContent != null) {
                Spacer(modifier = Modifier.width(12.dp))
                Box(modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )) {
                    trailingContent()
                }
            }
        }
        
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(
                    start = if (icon != null) 68.dp else 20.dp, 
                    end = 20.dp
                ),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        }
    }
}

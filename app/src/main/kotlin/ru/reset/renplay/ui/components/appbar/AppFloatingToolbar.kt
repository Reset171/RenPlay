package ru.reset.renplay.ui.components.appbar

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ru.reset.renplay.ui.components.feedback.LocalAppBlurState
import ru.reset.renplay.ui.components.feedback.appBlurEffect

@Composable
fun AppFloatingToolbar(
    scrollProgress: Float,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val blurState = LocalAppBlurState.current
    val blurActive = blurState.blurEnabled

    Surface(
        shape = CircleShape,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f * scrollProgress)),
        color = if (blurActive) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f * scrollProgress),
        modifier = modifier
            .appBlurEffect(
                state = blurState,
                shape = CircleShape,
                blurRadius = 16.dp * scrollProgress,
                tint = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f * scrollProgress),
                forceInvalidation = true
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp * scrollProgress, vertical = 4.dp * scrollProgress),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}
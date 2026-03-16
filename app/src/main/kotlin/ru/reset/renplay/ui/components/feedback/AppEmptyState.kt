package ru.reset.renplay.ui.components.feedback

import androidx.compose.foundation.layout.Arrangement
import ru.reset.renplay.ui.components.typography.AppText
import ru.reset.renplay.ui.components.icons.AppIcon
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun AppEmptyState(
    modifier: Modifier = Modifier.fillMaxSize(),
    icon: Painter? = null,
    iconSize: Dp = 72.dp,
    title: String? = null,
    titleStyle: TextStyle? = null,
    titleColor: Color? = null,
    description: String? = null,
    actionButton: @Composable (() -> Unit)? = null
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            AppIcon(
                painter = icon,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = MaterialTheme.colorScheme.outlineVariant
            )
            Spacer(Modifier.height(24.dp))
        }
        
        if (title != null) {
            AppText(
                text = title,
                style = titleStyle ?: MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = titleColor ?: MaterialTheme.colorScheme.onSurface
            )
            if (description != null) Spacer(Modifier.height(8.dp))
        }
        
        if (description != null) {
            AppText(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        if (actionButton != null) {
            Spacer(Modifier.height(32.dp))
            actionButton()
        }
    }
}

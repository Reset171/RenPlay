package ru.reset.renplay.ui.components.feedback

import androidx.compose.foundation.background
import ru.reset.renplay.ui.components.typography.AppText
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun AppLogConsole(
    logs: List<String>,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val scrollState = rememberScrollState()
    
    LaunchedEffect(logs.size) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        SelectionContainer {
            AppText(
                text = logs.joinToString("\n"),
                style = MaterialTheme.typography.bodySmall,
                color = textColor,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            )
        }
    }
}

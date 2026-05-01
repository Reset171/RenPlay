package ru.reset.renplay.ui.components.layout

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun AdaptiveButtonLayout(
    modifier: Modifier = Modifier,
    spacing: Dp = 12.dp,
    content: @Composable () -> Unit
) {
    Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        val spacingPx = spacing.roundToPx()

        val totalIntrinsicWidth = measurables.sumOf { it.maxIntrinsicWidth(constraints.maxHeight) } + 
                                  ((measurables.size - 1).coerceAtLeast(0)) * spacingPx

        if (totalIntrinsicWidth > constraints.maxWidth) {
            val placeables = measurables.map { 
                it.measure(constraints.copy(minWidth = constraints.maxWidth, maxWidth = constraints.maxWidth)) 
            }
            val height = placeables.sumOf { it.height } + ((placeables.size - 1).coerceAtLeast(0)) * spacingPx
            layout(constraints.maxWidth, height) {
                var y = 0
                placeables.forEach {
                    it.placeRelative(0, y)
                    y += it.height + spacingPx
                }
            }
        } else {
            val childWidth = if (measurables.isNotEmpty()) {
                (constraints.maxWidth - (measurables.size - 1) * spacingPx) / measurables.size
            } else 0

            val placeables = measurables.map { 
                it.measure(constraints.copy(minWidth = childWidth, maxWidth = childWidth)) 
            }
            val height = placeables.maxOfOrNull { it.height } ?: 0
            layout(constraints.maxWidth, height) {
                var x = 0
                placeables.forEach {
                    it.placeRelative(x, 0)
                    x += it.width + spacingPx
                }
            }
        }
    }
}
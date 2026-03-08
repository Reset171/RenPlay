package ru.reset.renplay.ui.library

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import ru.reset.renplay.domain.models.Project

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MorphingLibraryList(
    projects: List<Project>,
    iconCache: Map<String, Bitmap>,
    isGridView: Boolean,
    sortOrder: LibraryViewModel.SortOrder,
    searchQuery: String,
    advancedAnimationsEnabled: Boolean,
    useGameDetailsScreen: Boolean,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    activeProjectId: String?,
    onMove: (String, String) -> Unit,
    onGameClick: (Project) -> Unit,
    onInfoClick: (Project) -> Unit
) {
    var draggingId by remember { mutableStateOf<String?>(null) }
    val gridState = rememberLazyGridState()

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(if (isGridView) 2 else 1),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(if (isGridView) 16.dp else 12.dp),
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(sortOrder, searchQuery) {
                if (sortOrder != LibraryViewModel.SortOrder.MANUAL || searchQuery.isNotEmpty()) return@pointerInput
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        val item = gridState.layoutInfo.visibleItemsInfo.find {
                            offset.x >= it.offset.x && offset.x <= it.offset.x + it.size.width &&
                            offset.y >= it.offset.y && offset.y <= it.offset.y + it.size.height
                        }
                        item?.let { draggingId = it.key as? String }
                    },
                    onDrag = { change, _ ->
                        val currentPos = change.position
                        val hoveredItem = gridState.layoutInfo.visibleItemsInfo.find {
                            currentPos.x >= it.offset.x && currentPos.x <= it.offset.x + it.size.width &&
                            currentPos.y >= it.offset.y && currentPos.y <= it.offset.y + it.size.height
                        }
                        if (hoveredItem != null && hoveredItem.key != draggingId) {
                            draggingId?.let { id ->
                                onMove(id, hoveredItem.key as String)
                            }
                        }
                    },
                    onDragEnd = { draggingId = null },
                    onDragCancel = { draggingId = null }
                )
            }
    ) {
        items(projects, key = { it.id }) { project ->
            val isDragging = project.id == draggingId
            Box(modifier = if (advancedAnimationsEnabled) Modifier.animateItem() else Modifier) {
                SeamlessGameCard(
                    project = project,
                    iconCache = iconCache,
                    isGridView = isGridView,
                    advancedAnimationsEnabled = advancedAnimationsEnabled,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    activeProjectId = activeProjectId,
                    isDragging = isDragging,
                    useGameDetailsScreen = useGameDetailsScreen,
                    onClick = { onGameClick(project) },
                    onInfoClick = { onInfoClick(project) }
                )
            }
        }
    }
}
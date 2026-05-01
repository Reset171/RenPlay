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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.lazy.grid.LazyGridState
import ru.reset.renplay.domain.models.Project
import ru.reset.renplay.ui.library.components.SeamlessGameCard

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MorphingLibraryList(
    projects: List<Project>,
    gridState: LazyGridState,
    topPadding: androidx.compose.ui.unit.Dp,
    iconCache: Map<String, Bitmap>,
    bgCache: Map<String, Bitmap>,
    isGridView: Boolean,
    sortOrder: LibraryViewModel.SortOrder,
    searchQuery: String,
    advancedAnimationsEnabled: Boolean,
    useGameDetailsScreen: Boolean,
    showListBg: Boolean,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    activeProjectId: String?,
    onMove: (String, String) -> Unit,
    onGameClick: (Project) -> Unit,
    onInfoClick: (Project) -> Unit
) {
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var draggedItemSize by remember { mutableStateOf(IntSize.Zero) }
    var draggedItemInitialOffset by remember { mutableStateOf(IntOffset.Zero) }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(if (isGridView) 2 else 1),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(if (isGridView) 16.dp else 12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }, key = "top_spacer") {
            val spacing = if (isGridView) 16.dp else 12.dp
            androidx.compose.foundation.layout.Spacer(
                modifier = androidx.compose.ui.Modifier
                    .height(topPadding - spacing)
                    .fillMaxWidth()
            )
        }
        items(projects, key = { it.id }) { project ->
            val isDragging = project.id == draggingId
            val zIndex = if (isDragging) 1f else 0f
            val modifier = if (advancedAnimationsEnabled && !isDragging) Modifier.animateItem() else Modifier

            Box(
                modifier = modifier
                    .zIndex(zIndex)
                    .graphicsLayer {
                        if (isDragging) {
                            translationX = dragOffset.x
                            translationY = dragOffset.y
                        }
                    }
                    .pointerInput(sortOrder, searchQuery) {
                        if (sortOrder != LibraryViewModel.SortOrder.MANUAL || searchQuery.isNotEmpty()) return@pointerInput
                        detectDragGesturesAfterLongPress(
                            onDragStart = { _ ->
                                draggingId = project.id
                                dragOffset = Offset.Zero
                                val itemInfo = gridState.layoutInfo.visibleItemsInfo.find { it.key == project.id }
                                itemInfo?.let {
                                    draggedItemSize = it.size
                                    draggedItemInitialOffset = it.offset
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffset += dragAmount

                                val currentDraggedCenter = Offset(
                                    x = draggedItemInitialOffset.x + dragOffset.x + draggedItemSize.width / 2f,
                                    y = draggedItemInitialOffset.y + dragOffset.y + draggedItemSize.height / 2f
                                )

                                val hoveredItem = gridState.layoutInfo.visibleItemsInfo.find {
                                    currentDraggedCenter.x >= it.offset.x && currentDraggedCenter.x <= it.offset.x + it.size.width &&
                                    currentDraggedCenter.y >= it.offset.y && currentDraggedCenter.y <= it.offset.y + it.size.height
                                }

                                if (hoveredItem != null && hoveredItem.key != draggingId && hoveredItem.key != "top_spacer") {
                                    draggingId?.let { id ->
                                        onMove(id, hoveredItem.key as String)
                                        val newOffset = hoveredItem.offset
                                        val diffX = newOffset.x - draggedItemInitialOffset.x
                                        val diffY = newOffset.y - draggedItemInitialOffset.y
                                        dragOffset -= Offset(diffX.toFloat(), diffY.toFloat())
                                        draggedItemInitialOffset = newOffset
                                    }
                                }
                            },
                            onDragEnd = { 
                                draggingId = null 
                                dragOffset = Offset.Zero
                            },
                            onDragCancel = { 
                                draggingId = null 
                                dragOffset = Offset.Zero
                            }
                        )
                    }
            ) {
                SeamlessGameCard(
                    project = project,
                    iconCache = iconCache,
                    bgCache = bgCache,
                    isGridView = isGridView,
                    advancedAnimationsEnabled = advancedAnimationsEnabled,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    activeProjectId = activeProjectId,
                    isDragging = isDragging,
                    useGameDetailsScreen = useGameDetailsScreen,
                    showListBg = showListBg,
                    searchQuery = searchQuery,
                    onClick = { onGameClick(project) },
                    onInfoClick = { onInfoClick(project) }
                )
            }
        }
    }
}

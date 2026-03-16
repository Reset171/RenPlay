package ru.reset.renplay.ui.components.feedback

import android.os.Build
import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

@Stable
class AppBlurState {
    internal var sourceLayer: GraphicsLayer? = null
    internal var sourcePosition: Offset = Offset.Unspecified
    internal val activeObservers = mutableListOf<() -> Unit>()
    var blurEnabled by mutableStateOf(true)

    internal fun dispatchUpdate() {
        activeObservers.forEach { it.invoke() }
    }
}

val LocalAppBlurState = compositionLocalOf { AppBlurState() }

@Composable
fun rememberAppBlurState(): AppBlurState = remember { AppBlurState() }

fun Modifier.appBlurSource(state: AppBlurState? = null): Modifier = this then AppBlurSourceElement(state)

private data class AppBlurSourceElement(val state: AppBlurState?) : ModifierNodeElement<AppBlurSourceNode>() {
    override fun create(): AppBlurSourceNode = AppBlurSourceNode(state)
    override fun update(node: AppBlurSourceNode) {
        node.targetState = state
    }
    override fun InspectorInfo.inspectableProperties() {
        name = "appBlurSource"
    }
}

class AppBlurSourceNode(var targetState: AppBlurState?) : Modifier.Node(),
    CompositionLocalConsumerModifierNode,
    GlobalPositionAwareModifierNode,
    DrawModifierNode,
    ObserverModifierNode {

    private var activeState: AppBlurState? = null
    private var preDrawListenerAdded = false
    private var viewForPreDraw: View? = null

    private val preDrawListener = ViewTreeObserver.OnPreDrawListener {
        if (activeState?.activeObservers?.isNotEmpty() == true) {
            invalidateDraw()
        }
        true
    }

    override val shouldAutoInvalidate: Boolean = false

    override fun onAttach() {
        activeState = targetState ?: currentValueOf(LocalAppBlurState)
        onObservedReadsChanged()
    }

    override fun onObservedReadsChanged() {
        observeReads {
            val view = currentValueOf(LocalView)
            if (!preDrawListenerAdded) {
                viewForPreDraw = view
                view.viewTreeObserver.addOnPreDrawListener(preDrawListener)
                preDrawListenerAdded = true
            }
        }
    }

    override fun onDetach() {
        if (preDrawListenerAdded) {
            viewForPreDraw?.viewTreeObserver?.removeOnPreDrawListener(preDrawListener)
            preDrawListenerAdded = false
            viewForPreDraw = null
        }
        activeState?.sourceLayer?.let { layer ->
            currentValueOf(LocalGraphicsContext).releaseGraphicsLayer(layer)
        }
        activeState?.sourceLayer = null
        activeState = null
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        activeState?.let { state ->
            val newPosition = coordinates.positionOnScreen()
            if (state.sourcePosition != newPosition) {
                state.sourcePosition = newPosition
                state.dispatchUpdate()
            }
        }
    }

    override fun ContentDrawScope.draw() {
        val state = activeState
        
        if (state == null || !state.blurEnabled || size.width < 1f || size.height < 1f) {
            drawContent()
            return
        }

        val layer = state.sourceLayer ?: currentValueOf(LocalGraphicsContext).createGraphicsLayer().also {
            state.sourceLayer = it
        }

        layer.record(IntSize(size.width.toInt(), size.height.toInt())) {
            this@draw.drawContent()
        }
        drawLayer(layer)
        state.dispatchUpdate()
    }
}

fun Modifier.appBlurEffect(
    state: AppBlurState? = null,
    shape: Shape = RectangleShape,
    blurRadius: Dp = 20.dp,
    tint: Color = Color.Unspecified,
    forceInvalidation: Boolean = false
): Modifier = this then AppBlurEffectElement(state, shape, blurRadius, tint, forceInvalidation)

private data class AppBlurEffectElement(
    val state: AppBlurState?,
    val shape: Shape,
    val blurRadius: Dp,
    val tint: Color,
    val forceSync: Boolean
) : ModifierNodeElement<AppBlurEffectNode>() {
    override fun create() = AppBlurEffectNode(state, shape, blurRadius, tint, forceSync)
    override fun update(node: AppBlurEffectNode) {
        node.targetState = state
        node.shape = shape
        node.blurRadius = blurRadius
        node.tint = tint
        node.forceSync = forceSync
        node.invalidateDraw()
    }
    override fun InspectorInfo.inspectableProperties() {
        name = "appBlurEffect"
    }
}

class AppBlurEffectNode(
    var targetState: AppBlurState?,
    var shape: Shape,
    var blurRadius: Dp,
    var tint: Color,
    var forceSync: Boolean
) : Modifier.Node(),
    CompositionLocalConsumerModifierNode,
    GlobalPositionAwareModifierNode,
    DrawModifierNode,
    ObserverModifierNode {

    private var activeState: AppBlurState? = null
    private var effectLayer: GraphicsLayer? = null
    private var nodePosition: Offset = Offset.Unspecified

    private var preDrawListenerAdded = false
    private var viewForPreDraw: View? = null

    private val syncCallback: () -> Unit = {
        invalidateDraw()
    }

    private val preDrawListener = ViewTreeObserver.OnPreDrawListener {
        if (forceSync) {
            invalidateDraw()
        }
        true
    }

    override val shouldAutoInvalidate: Boolean = false

    override fun onAttach() {
        activeState = targetState ?: currentValueOf(LocalAppBlurState)
        activeState?.activeObservers?.add(syncCallback)
        if (forceSync) {
            onObservedReadsChanged()
        }
    }

    override fun onObservedReadsChanged() {
        observeReads {
            if (forceSync) {
                val view = currentValueOf(LocalView)
                if (!preDrawListenerAdded) {
                    viewForPreDraw = view
                    view.viewTreeObserver.addOnPreDrawListener(preDrawListener)
                    preDrawListenerAdded = true
                }
            }
        }
    }

    override fun onDetach() {
        if (preDrawListenerAdded) {
            viewForPreDraw?.viewTreeObserver?.removeOnPreDrawListener(preDrawListener)
            preDrawListenerAdded = false
            viewForPreDraw = null
        }
        activeState?.activeObservers?.remove(syncCallback)
        effectLayer?.let { layer ->
            currentValueOf(LocalGraphicsContext).releaseGraphicsLayer(layer)
        }
        effectLayer = null
        activeState = null
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        nodePosition = coordinates.positionOnScreen()
    }

    override fun ContentDrawScope.draw() {
        val state = activeState
        
        if (state == null || !state.blurEnabled) {
            drawContent()
            return
        }

        val sourceLayer = state.sourceLayer
        val sourcePos = state.sourcePosition

        val isAccelerated = drawContext.canvas.nativeCanvas.isHardwareAccelerated
        val radiusPx = with(currentValueOf(LocalDensity)) { blurRadius.toPx() }

        val outline = shape.createOutline(size, layoutDirection, this)
        val clipPath = when (outline) {
            is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
            is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
            is Outline.Generic -> outline.path
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isAccelerated && radiusPx > 0f &&
            sourceLayer != null && !sourceLayer.isReleased && sourcePos.isSpecified && nodePosition.isSpecified) {

            val paddedWidth = (size.width + radiusPx * 2).toInt()
            val paddedHeight = (size.height + radiusPx * 2).toInt()

            if (paddedWidth > 0 && paddedHeight > 0) {
                val layer = effectLayer ?: currentValueOf(LocalGraphicsContext).createGraphicsLayer().also {
                    effectLayer = it
                }

                layer.record(IntSize(paddedWidth, paddedHeight)) {
                    val delta = sourcePos - nodePosition + Offset(radiusPx, radiusPx)
                    translate(delta.x, delta.y) {
                        drawLayer(sourceLayer)
                    }
                }

                layer.renderEffect = BlurEffect(radiusPx, radiusPx, TileMode.Clamp)

                clipPath(clipPath) {
                    translate(-radiusPx, -radiusPx) {
                        drawLayer(layer)
                    }
                    if (tint != Color.Unspecified && tint != Color.Transparent) {
                        drawRect(tint)
                    }
                }
            } else {
                drawFallback(clipPath)
            }
        } else {
            drawFallback(clipPath)
        }
        drawContent()
    }

    private fun ContentDrawScope.drawFallback(path: Path) {
        clipPath(path) {
            val fallbackTint = if (tint != Color.Unspecified && tint != Color.Transparent) tint else Color.Black.copy(alpha = 0.6f)
            drawRect(fallbackTint)
        }
    }
}

@Composable
fun AppBlur(
    modifier: Modifier = Modifier,
    state: AppBlurState? = null,
    shape: Shape = RoundedCornerShape(28.dp),
    blurRadius: Dp = 24.dp,
    tint: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
    forceInvalidation: Boolean = false,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit
) {
    val activeState = state ?: LocalAppBlurState.current
    
    Box(
        modifier = modifier
            .clip(shape)
            .then(
                if (activeState.blurEnabled) {
                    Modifier.appBlurEffect(activeState, shape, blurRadius, tint, forceInvalidation)
                } else Modifier
            ),
        contentAlignment = contentAlignment,
        content = content
    )
}

package ru.reset.renplay.utils

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.View
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import androidx.core.graphics.ColorUtils
import dev.oneuiproject.oneui.utils.supports3DTransitionFlag

fun isBlurSupported(context: Context): Boolean {
    if (supports3DTransitionFlag) return true
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return true
    return false
}

fun getActivity(context: Context): Activity? {
    var ctx = context
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

object GlobalBlurState {
    var sourceNode: Any? = null
    val sourceLocation = IntArray(2)
    val listeners = mutableSetOf<() -> Unit>()
    var windowBackgroundColor: Int = Color.BLACK

    fun dispatchUpdate() {
        listeners.forEach { it.invoke() }
    }
}

class BlurSourceFrameLayout(context: Context) : FrameLayout(context) {
    private var renderNode: Any? = null
    private var isSelfInvalidating = false
    private var preDrawListener: ViewTreeObserver.OnPreDrawListener? = null

    init {
        val a = context.obtainStyledAttributes(intArrayOf(android.R.attr.colorBackground))
        GlobalBlurState.windowBackgroundColor = a.getColor(0, Color.BLACK)
        a.recycle()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            renderNode = RenderNode("BlurSourceNode")
            preDrawListener = ViewTreeObserver.OnPreDrawListener {
                if (GlobalBlurState.listeners.isNotEmpty()) {
                    if (!isSelfInvalidating) {
                        isSelfInvalidating = true
                        invalidate()
                    } else {
                        isSelfInvalidating = false
                    }
                }
                true
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            viewTreeObserver.addOnPreDrawListener(preDrawListener)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            viewTreeObserver.removeOnPreDrawListener(preDrawListener)
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dispatchDrawApi31(canvas)
        } else {
            super.dispatchDraw(canvas)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun dispatchDrawApi31(canvas: Canvas) {
        if (GlobalBlurState.listeners.isNotEmpty() && canvas.isHardwareAccelerated && width > 0 && height > 0) {
            val node = renderNode as RenderNode
            node.setPosition(0, 0, width, height)
            val recordingCanvas = node.beginRecording()
            super.dispatchDraw(recordingCanvas)
            node.endRecording()
            
            canvas.drawRenderNode(node)
            
            getLocationOnScreen(GlobalBlurState.sourceLocation)
            GlobalBlurState.sourceNode = node
            GlobalBlurState.dispatchUpdate()
        } else {
            super.dispatchDraw(canvas)
        }
    }
}

fun Dialog.applyCrossWindowBlur(context: Context, targetView: View?) {
    val prefs = context.getSharedPreferences("RenPlayPrefs", Context.MODE_PRIVATE)
    val enableBlur = prefs.getBoolean("enable_blur", true)
    if (!enableBlur) return

    if (supports3DTransitionFlag) return

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && targetView != null) {
        window?.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)

        window?.setBackgroundDrawableResource(android.R.color.transparent)

        val typedValue = android.util.TypedValue()
        context.theme.resolveAttribute(androidx.appcompat.R.attr.roundedCornerColor, typedValue, true)
        val themeBgColor = typedValue.data
        val isDark = ColorUtils.calculateLuminance(themeBgColor) < 0.5
        val fallbackTintColor = if (isDark) Color.parseColor("#111111") else Color.parseColor("#F9F9F9")

        val originalBg = targetView.background
        targetView.background = BlurEffectDrawable(targetView, originalBg, fallbackTintColor)
        targetView.clipToOutline = true
    }
}

@RequiresApi(Build.VERSION_CODES.S)
class BlurEffectDrawable(
    private val targetView: View,
    private val originalBg: Drawable?,
    private val fallbackColor: Int,
    private val blurRadius: Float = 120f
) : Drawable() {
    private val effectNode = RenderNode("BlurEffectNode").apply {
        setRenderEffect(RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP))
    }
    private val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val location = IntArray(2)

    private val updateListener: () -> Unit = { targetView.invalidate() }
    private val dialogPreDrawListener = ViewTreeObserver.OnPreDrawListener {
        targetView.invalidate()
        true
    }

    init {
        targetView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                GlobalBlurState.listeners.add(updateListener)
                v.viewTreeObserver.addOnPreDrawListener(dialogPreDrawListener)
            }
            override fun onViewDetachedFromWindow(v: View) {
                GlobalBlurState.listeners.remove(updateListener)
                v.viewTreeObserver.removeOnPreDrawListener(dialogPreDrawListener)
            }
        })
        if (targetView.isAttachedToWindow) {
            GlobalBlurState.listeners.add(updateListener)
            targetView.viewTreeObserver.addOnPreDrawListener(dialogPreDrawListener)
        }
    }

    override fun draw(canvas: Canvas) {
        val sourceNode = GlobalBlurState.sourceNode as? RenderNode
        if (sourceNode != null && canvas.isHardwareAccelerated) {
            targetView.getLocationOnScreen(location)
            val w = bounds.width()
            val h = bounds.height()
            if (w > 0 && h > 0) {
                val pad = blurRadius.toInt()
                val paddedW = w + pad * 2
                val paddedH = h + pad * 2

                val dx = location[0] - GlobalBlurState.sourceLocation[0]
                val dy = location[1] - GlobalBlurState.sourceLocation[1]

                effectNode.setPosition(0, 0, paddedW, paddedH)
                val rc = effectNode.beginRecording()
                rc.drawColor(GlobalBlurState.windowBackgroundColor)
                rc.translate(-dx.toFloat() + pad, -dy.toFloat() + pad)
                rc.drawRenderNode(sourceNode)
                effectNode.endRecording()
                
                canvas.save()
                canvas.clipRect(bounds)
                canvas.translate(-pad.toFloat(), -pad.toFloat())
                canvas.drawRenderNode(effectNode)
                canvas.restore()
            }
        }
        
        if (originalBg != null) {
            originalBg.bounds = bounds
            val oldAlpha = originalBg.alpha
            originalBg.alpha = (oldAlpha * 0.88f).toInt()
            originalBg.draw(canvas)
            originalBg.alpha = oldAlpha
        } else {
            fallbackPaint.color = ColorUtils.setAlphaComponent(fallbackColor, 224)
            val r = 24f * targetView.context.resources.displayMetrics.density
            canvas.drawRoundRect(RectF(bounds), r, r, fallbackPaint)
        }
    }

    override fun getOutline(outline: Outline) {
        if (originalBg != null) {
            originalBg.getOutline(outline)
        } else {
            val r = 24f * targetView.context.resources.displayMetrics.density
            outline.setRoundRect(bounds, r)
        }
    }

    override fun setAlpha(alpha: Int) {
        originalBg?.alpha = alpha
        fallbackPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        originalBg?.colorFilter = colorFilter
        fallbackPaint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
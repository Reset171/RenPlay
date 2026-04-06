package ru.reset.renplay.utils

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.widget.FrameLayout
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.savedstate.*
import ru.reset.renplay.R
import ru.reset.renplay.ui.settings.ThemeOption
import ru.reset.renplay.ui.theme.MyComposeApplicationTheme

@SuppressLint("ViewConstructor")
class StandaloneComposeView(context: Context) : FrameLayout(context), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    init {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun attachToWindowRoot() {
        val activity = context as? android.app.Activity
        val rootView = activity?.window?.decorView ?: this
        rootView.setViewTreeLifecycleOwner(this)
        rootView.setViewTreeSavedStateRegistryOwner(this)
        rootView.setViewTreeViewModelStoreOwner(this)
    }

    fun detachFromWindowRoot() {
        val activity = context as? android.app.Activity
        val rootView = activity?.window?.decorView ?: this
        rootView.setViewTreeLifecycleOwner(null)
        rootView.setViewTreeSavedStateRegistryOwner(null)
        rootView.setViewTreeViewModelStoreOwner(null)
    }

    fun start() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun destroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }
}

object LoadingOverlayHelper {
    @JvmStatic
    fun createLoadingOverlay(context: Context, gameName: String?, engineVersion: String?, gamePath: String?): View {
        val host = StandaloneComposeView(context)
        val composeView = ComposeView(context).apply {
            setContent {
                MyComposeApplicationTheme(dynamicColor = true, themeOption = ThemeOption.DARK) {
                    var iconBmp by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
                    var bgBmp by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

                    LaunchedEffect(gamePath) {
                        gamePath?.let { path ->
                            val assets = GameAssetExtractor.getGameAssets(context, path)
                            assets.iconPath?.let { iconPath ->
                                val bmp = GameAssetExtractor.loadBitmap(iconPath, 300, 300)
                                iconBmp = bmp?.asImageBitmap()
                            }
                            assets.backgroundPath?.let { bgPath ->
                                val bmp = GameAssetExtractor.loadBitmap(bgPath, 600, 400)
                                bgBmp = bmp?.asImageBitmap()
                            }
                        }
                    }

                    val infiniteTransition = rememberInfiniteTransition(label = "breathing_icon")
                    val scale by infiniteTransition.animateFloat(
                        initialValue = 0.95f,
                        targetValue = 1.05f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1500, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "icon_scale"
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                    ) {
                        (bgBmp ?: iconBmp)?.let {
                            Image(
                                bitmap = it,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .blur(80.dp, BlurredEdgeTreatment.Unbounded)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.3f),
                                            Color.Black.copy(alpha = 0.85f)
                                        )
                                    )
                                )
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            val currentIcon = iconBmp
                            if (currentIcon != null) {
                                Image(
                                    bitmap = currentIcon,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(120.dp)
                                        .graphicsLayer {
                                            scaleX = scale
                                            scaleY = scale
                                        }
                                        .clip(RoundedCornerShape(28.dp))
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(120.dp)
                                        .graphicsLayer {
                                            scaleX = scale
                                            scaleY = scale
                                        }
                                        .clip(RoundedCornerShape(28.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_gamepad),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(56.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(32.dp))

                            Text(
                                text = gameName ?: "...",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = context.getString(R.string.loading_game_title),
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(48.dp))

                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth(0.5f)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            )

                            if (!engineVersion.isNullOrEmpty()) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = context.getString(R.string.loading_engine_version, engineVersion),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }
        }
        host.addView(composeView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        host.attachToWindowRoot()
        host.start()
        return host
    }

    @JvmStatic
    fun destroyLoadingOverlay(view: View?) {
        if (view is StandaloneComposeView) {
            view.detachFromWindowRoot()
            view.destroy()
        }
    }
}
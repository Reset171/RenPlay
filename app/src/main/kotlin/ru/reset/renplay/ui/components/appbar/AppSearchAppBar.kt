package ru.reset.renplay.ui.components.appbar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.reset.renplay.R
import ru.reset.renplay.ui.components.icons.AppIcon
import ru.reset.renplay.ui.components.icons.AppIconButton

@Composable
fun AppSearchAppBar(
    query: String,
    onQueryChange: (String) -> Unit,
    isActive: Boolean,
    onActiveChange: (Boolean) -> Unit,
    placeholder: String
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val widthFraction = remember { Animatable(0f) }
    val contentAlpha = remember { Animatable(0f) }

    LaunchedEffect(isActive) {
        if (isActive) {
            launch {
                contentAlpha.animateTo(1f, tween(200, delayMillis = 50))
            }
            launch {
                widthFraction.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = 300f))
            }
            delay(50)
            focusRequester.requestFocus()
        } else {
            keyboardController?.hide()
            focusManager.clearFocus()
            launch {
                contentAlpha.animateTo(0f, tween(150))
            }
            widthFraction.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 400f))
        }
    }

    Box(
        modifier = Modifier
            .statusBarsPadding()
            .fillMaxWidth()
            .height(74.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        val containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    clip = true
                    shape = object : Shape {
                        override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
                            val w = size.width * widthFraction.value
                            if (w <= 0f) return Outline.Rectangle(androidx.compose.ui.geometry.Rect.Zero)
                            val left = (size.width - w) / 2f
                            return Outline.Rounded(
                                RoundRect(
                                    left = left,
                                    top = 0f,
                                    right = left + w,
                                    bottom = size.height,
                                    cornerRadius = CornerRadius(size.height / 2f)
                                )
                            )
                        }
                    }
                }
                .background(containerColor)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp)
                    .graphicsLayer { alpha = contentAlpha.value },
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIconButton(
                    onClick = { onActiveChange(false) },
                    size = 42.dp
                ) {
                    AppIcon(painterResource(R.drawable.ic_arrow_back), null)
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                keyboardController?.hide()
                                focusManager.clearFocus()
                            }
                        )
                    )
                }

                AnimatedVisibility(
                    visible = query.isNotEmpty(),
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    AppIconButton(
                        onClick = { 
                            onQueryChange("")
                            focusRequester.requestFocus()
                        },
                        size = 42.dp
                    ) {
                        AppIcon(painterResource(R.drawable.ic_close), null)
                    }
                }

                if (query.isEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
        }
    }
}

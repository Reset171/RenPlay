package ru.reset.renplay.ui.components.appbar

import androidx.compose.foundation.layout.fillMaxWidth
import ru.reset.renplay.ui.components.icons.AppIcon
import ru.reset.renplay.ui.components.icons.AppIconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import ru.reset.renplay.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSearchAppBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    placeholder: String
) {
    val focusRequester = remember { FocusRequester() }
    
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    TopAppBar(
        title = {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(placeholder) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
            )
        },
        navigationIcon = {
            AppIconButton(onClick = onClose) {
                AppIcon(painterResource(R.drawable.ic_arrow_back), null)
            }
        },
        actions = {
            if (query.isNotEmpty()) {
                AppIconButton(onClick = { onQueryChange("") }) {
                    AppIcon(painterResource(R.drawable.ic_close), null)
                }
            }
        }
    )
}
package dev.pikaia.android.feature.components.toolbar.icons

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun CloseIcon(modifier: Modifier = Modifier) {
    val onBackPressedDispatcher =
        LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    IconButton(modifier = modifier, onClick = { onBackPressedDispatcher?.onBackPressed() }) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Back"
        )
    }
}
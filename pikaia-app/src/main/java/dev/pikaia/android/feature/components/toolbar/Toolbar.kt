package dev.pikaia.android.feature.components.toolbar

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.pikaia.android.feature.components.toolbar.icons.DefaultNavigationIcon
import dev.pikaia.android.theme.semiBold16

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Toolbar(
    title: String?,
    modifier: Modifier = Modifier,
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    navigationIcon: @Composable () -> Unit = { DefaultNavigationIcon() },
    actionButton: @Composable RowScope.() -> Unit = { }
) {
    CenterAlignedTopAppBar(
        modifier = modifier,
        title = {
            if (title != null) {
                Text(text = title, style = MaterialTheme.typography.semiBold16)
            }
        },
        navigationIcon = navigationIcon,
        actions = actionButton,
        colors = colors
    )
}
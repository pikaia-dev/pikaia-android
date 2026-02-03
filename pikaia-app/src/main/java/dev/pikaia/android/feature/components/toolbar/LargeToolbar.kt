package dev.pikaia.android.feature.components.toolbar

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.pikaia.android.feature.components.toolbar.icons.DefaultNavigationIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LargeToolbar(
    title: String?,
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    navigationIcon: @Composable () -> Unit = { DefaultNavigationIcon() },
    actionButton: @Composable RowScope.() -> Unit = { }
) {
    MediumTopAppBar(
        modifier = modifier,
        title = {
            if (title != null) {
                Text(text = title)
            }
        },
        navigationIcon = navigationIcon,
        actions = actionButton,
        colors = colors,
        scrollBehavior = scrollBehavior
    )
}
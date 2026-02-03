package dev.pikaia.android.navigation.bottombar

import androidx.annotation.DrawableRes
import dev.pikaia.android.navigation.graphs.MainGraph

data class NavigationItem(
    val title: String,
    @DrawableRes val iconResource: Int,
    val screen: MainGraph,
    val counter: Int = 0
)

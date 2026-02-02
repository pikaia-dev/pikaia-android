package dev.pikaia.android.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import dev.pikaia.android.navigation.graphs.Graphs
import dev.pikaia.android.navigation.graphs.mainGraph

@Composable
fun AppNavigation(
    navController: NavHostController
) {
    NavHost(
        navController = navController,
        startDestination = Graphs.MainGraph
    ) {
        mainGraph(navController)
    }
}
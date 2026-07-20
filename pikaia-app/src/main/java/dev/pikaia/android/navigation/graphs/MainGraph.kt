package dev.pikaia.android.navigation.graphs

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import dev.pikaia.android.feature.devices.ui.DevicesScreen
import dev.pikaia.android.feature.home.ui.HomeScreen
import dev.pikaia.android.feature.login.ui.LoginScreen
import dev.pikaia.android.feature.profile.ui.ProfileScreen
import kotlinx.serialization.Serializable

sealed interface MainGraph {
    @Serializable
    data object Home : MainGraph

    @Serializable
    data object Profile : MainGraph

    @Serializable
    data object Login : MainGraph

    @Serializable
    data object Devices : MainGraph
}

internal fun NavGraphBuilder.mainGraph(
    navController: NavHostController
) {
    navigation<Graphs.MainGraph>(startDestination = MainGraph.Home) {
        composable<MainGraph.Home> {
            HomeScreen(navController = navController)
        }

        composable<MainGraph.Profile> {
            ProfileScreen(
                navController = navController,
                goToLogin = { navController.navigate(MainGraph.Login) },
                goToDevices = { navController.navigate(MainGraph.Devices) }
            )
        }

        composable<MainGraph.Login> {
            LoginScreen(navController = navController)
        }

        composable<MainGraph.Devices> {
            DevicesScreen(navController = navController)
        }
    }
}

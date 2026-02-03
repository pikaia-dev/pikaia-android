package dev.pikaia.android.feature.profile.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import dev.pikaia.android.R
import dev.pikaia.android.feature.components.toolbar.Toolbar
import dev.pikaia.android.feature.profile.redux.ProfileAction
import dev.pikaia.android.feature.profile.redux.ProfileNavigationEffect
import dev.pikaia.android.feature.profile.redux.ProfileState
import dev.pikaia.android.lib.base.ObserveEffects
import dev.pikaia.android.navigation.bottombar.BottomNavigationBar
import dev.pikaia.android.navigation.graphs.MainGraph
import dev.pikaia.android.theme.bold32
import dev.pikaia.android.theme.normal18
import dev.pikaia.android.theme.spacings
import org.koin.androidx.compose.koinViewModel

@Composable
fun ProfileScreen(
    navController: NavHostController,
    goToLogin: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = koinViewModel()
) {
    val state by viewModel.viewState.collectAsStateWithLifecycle()

    ObserveEffects(viewModel.effect) { effect ->
        when (effect) {
            ProfileNavigationEffect.GoToLogin -> goToLogin()
        }

    }

    ProfileScreenContent(
        navController = navController,
        state = state,
        actionHandler = viewModel::handleAction,
        modifier = modifier
    )
}

@Composable
private fun ProfileScreenContent(
    navController: NavHostController,
    state: ProfileState,
    actionHandler: (ProfileAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            Toolbar(
                title = stringResource(R.string.profile_screen_title),
                navigationIcon = {}
            )
        },
        bottomBar = {
            BottomNavigationBar(
                navController = navController,
                currentScreen = MainGraph.Profile
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = MaterialTheme.spacings.d16),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            state.profile?.let { profile ->
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = MaterialTheme.spacings.d16),
                    text = profile.name,
                    style = MaterialTheme.typography.bold32
                )
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = MaterialTheme.spacings.d8),
                    text = profile.email,
                    style = MaterialTheme.typography.normal18
                )
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = profile.phone,
                    style = MaterialTheme.typography.normal18
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                modifier = Modifier.padding(bottom = MaterialTheme.spacings.d32),
                onClick = {
                    if (state.profile != null) {
                        actionHandler(ProfileAction.Logout)
                    } else {
                        actionHandler(ProfileAction.Login)
                    }
                }
            ) {
                Text(
                    text = stringResource(
                        if (state.profile != null) {
                            R.string.profile_logout_btn
                        } else {
                            R.string.profile_login_btn
                        }
                    )
                )
            }
        }
    }
}
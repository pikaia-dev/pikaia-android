package dev.pikaia.android.activity

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.navigation.NavHostController
import dev.pikaia.android.R
import dev.pikaia.android.lib.base.ObserveEffects
import dev.pikaia.android.lib.snackbar.ActionNavigationEffect
import dev.pikaia.android.lib.snackbar.ErrorNavigationEffect
import dev.pikaia.android.lib.snackbar.InfoNavigationEffect
import dev.pikaia.android.navigation.AppNavigation
import dev.pikaia.android.theme.spacings
import org.koin.androidx.compose.koinViewModel

@Composable
internal fun PikaiaAppComponent(
    navController: NavHostController,
    viewModel: MainViewModel = koinViewModel(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    val snackBarEvents: MutableState<SnackBarViewEvent?> = remember { mutableStateOf(null) }
    val localContext = LocalContext.current

    ObserveEffects(viewModel.effect) { effect ->
        when (effect) {
            is ErrorNavigationEffect -> {
                snackBarEvents.value = SnackBarViewEvent(
                    message = effect.ex.message
                        ?: localContext.getString(R.string.default_error_message)
                )
            }

            is InfoNavigationEffect -> {
                snackBarEvents.value = SnackBarViewEvent(effect.message)
            }

            is ActionNavigationEffect -> {
                snackBarEvents.value = SnackBarViewEvent(
                    message = effect.message,
                    actionLabel = effect.actionLabel,
                    action = effect.action,
                    duration = SnackbarDuration.Long
                )
            }
        }
    }
    Box(
        modifier = Modifier
            .semantics { testTagsAsResourceId = true }
            .imePadding()
    ) {
        AppNavigation(navController = navController)

        SnackbarHost(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = MaterialTheme.spacings.d32),
            hostState = snackbarHostState
        )
    }
}

data class SnackBarViewEvent(
    val message: String,
    val id: Long = System.currentTimeMillis(),
    val duration: SnackbarDuration = SnackbarDuration.Short,
    val actionLabel: String? = null,
    val action: () -> Unit = {},
)
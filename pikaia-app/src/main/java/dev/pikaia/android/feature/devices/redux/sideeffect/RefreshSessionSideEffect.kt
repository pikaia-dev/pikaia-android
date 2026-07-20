package dev.pikaia.android.feature.devices.redux.sideeffect

import dev.pikaia.android.feature.devices.domain.RefreshDeviceSession
import dev.pikaia.android.feature.devices.redux.DevicesAction
import dev.pikaia.android.lib.snackbar.ErrorNavigationEffect
import dev.pikaia.android.lib.snackbar.InfoNavigationEffect
import dev.pikaia.android.lib.store.Action
import dev.pikaia.android.lib.store.SideEffect
import dev.pikaia.android.lib.store.Store
import dev.pikaia.android.state.AppState
import kotlinx.coroutines.flow.catch

/**
 * Demonstrates an explicit device-session refresh. In normal operation this is not
 * needed — the API client refreshes automatically on 401 — but it makes the mechanism
 * visible in the reference app.
 */
class RefreshSessionSideEffect(
    private val store: Store<AppState>,
    private val refreshDeviceSession: RefreshDeviceSession,
    private val refreshedMessage: String
) : SideEffect {

    override suspend fun invoke(action: Action) {
        if (action != DevicesAction.RefreshSession) return

        refreshDeviceSession(Unit)
            .catch {
                store.postNavigationEffect(ErrorNavigationEffect(it))
                store.dispatch(DevicesAction.OperationFailed)
            }
            .collect {
                store.dispatch(DevicesAction.SessionRefreshed)
                store.postNavigationEffect(InfoNavigationEffect(refreshedMessage))
            }
    }
}

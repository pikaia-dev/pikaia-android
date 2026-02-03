package dev.pikaia.android.feature.profile.redux.sideeffect

import dev.pikaia.android.feature.profile.domain.LoadProfile
import dev.pikaia.android.feature.profile.redux.ProfileAction
import dev.pikaia.android.lib.snackbar.ErrorNavigationEffect
import dev.pikaia.android.lib.store.Action
import dev.pikaia.android.lib.store.SideEffect
import dev.pikaia.android.lib.store.Store
import dev.pikaia.android.state.AppState
import kotlinx.coroutines.flow.catch

class LoadProfileSideEffect(
    private val store: Store<AppState>,
    private val loadProfile: LoadProfile
) : SideEffect {

    override suspend fun invoke(action: Action) {
        if (action != ProfileAction.LoadProfile) {
            return
        }
        loadProfile(Unit)
            .catch { store.postNavigationEffect(ErrorNavigationEffect(it)) }
            .collect { profile ->
                store.dispatch(ProfileAction.ProfileLoaded(profile))
            }
    }
}
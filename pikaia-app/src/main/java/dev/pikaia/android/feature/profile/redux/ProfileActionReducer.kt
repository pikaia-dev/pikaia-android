package dev.pikaia.android.feature.profile.redux

import dev.pikaia.android.lib.store.Action
import dev.pikaia.android.lib.store.Reducer
import dev.pikaia.android.state.AppState

class ProfileActionReducer : Reducer<AppState> {
    override fun invoke(action: Action, state: AppState) = when (action) {
        ProfileAction.Logout -> state.copy(profile = null)
        is ProfileAction.ProfileLoaded -> state.copy(profile = action.profile)
        else -> state
    }
}
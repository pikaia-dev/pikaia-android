package dev.pikaia.android.feature.profile.ui

import dev.pikaia.android.feature.profile.redux.ProfileAction
import dev.pikaia.android.feature.profile.redux.ProfileState
import dev.pikaia.android.feature.profile.redux.ProfileStoreProxy
import dev.pikaia.android.lib.base.BaseViewModel

class ProfileViewModel(
    storeProxy: ProfileStoreProxy
) : BaseViewModel<ProfileState, ProfileAction>(storeProxy, ProfileState()) {

    init {
        handleAction(ProfileAction.LoadProfile)
    }
}

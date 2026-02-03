package dev.pikaia.android.feature.profile.redux

import dev.pikaia.android.feature.profile.data.Profile
import dev.pikaia.android.lib.store.Action

sealed interface ProfileAction : Action{
    data object LoadProfile : ProfileAction
    data class ProfileLoaded(val profile: Profile?) : ProfileAction
    data object Logout : ProfileAction
    data object Login : ProfileAction
}
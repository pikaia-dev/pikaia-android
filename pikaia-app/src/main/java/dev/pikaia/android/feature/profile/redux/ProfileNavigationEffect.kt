package dev.pikaia.android.feature.profile.redux

import dev.pikaia.android.lib.store.NavigationEffect

sealed interface ProfileNavigationEffect : NavigationEffect {
    data object GoToLogin : ProfileNavigationEffect
}

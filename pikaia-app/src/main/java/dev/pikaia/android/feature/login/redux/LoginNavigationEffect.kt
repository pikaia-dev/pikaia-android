package dev.pikaia.android.feature.login.redux

import dev.pikaia.android.lib.store.NavigationEffect

sealed interface LoginNavigationEffect : NavigationEffect {
    data object LoginComplete : LoginNavigationEffect
}

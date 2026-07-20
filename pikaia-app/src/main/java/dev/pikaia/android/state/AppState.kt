package dev.pikaia.android.state

import dev.pikaia.android.feature.devices.redux.DevicesState
import dev.pikaia.android.feature.login.redux.LoginState
import dev.pikaia.android.feature.profile.data.Profile

data class AppState(
    val isLoading: Boolean = false,

    val homeMessage: String = "Sample home message",

    val profile: Profile? = null,

    val login: LoginState = LoginState(),

    val devices: DevicesState = DevicesState()
)

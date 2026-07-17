package dev.pikaia.android.feature.login.ui

import dev.pikaia.android.feature.login.redux.LoginAction
import dev.pikaia.android.feature.login.redux.LoginState
import dev.pikaia.android.feature.login.redux.LoginStoreProxy
import dev.pikaia.android.lib.base.BaseViewModel

class LoginViewModel(
    storeProxy: LoginStoreProxy
) : BaseViewModel<LoginState, LoginAction>(storeProxy, LoginState())

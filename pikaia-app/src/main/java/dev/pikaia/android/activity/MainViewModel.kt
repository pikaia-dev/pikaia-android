package dev.pikaia.android.activity

import dev.pikaia.android.activity.redux.MainAction
import dev.pikaia.android.activity.redux.MainState
import dev.pikaia.android.activity.redux.MainStoreProxy
import dev.pikaia.android.lib.base.BaseViewModel

class MainViewModel(
    storeProxy: MainStoreProxy
) : BaseViewModel<MainState, MainAction>(storeProxy, MainState())

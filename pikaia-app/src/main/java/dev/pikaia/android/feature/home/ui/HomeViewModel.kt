package dev.pikaia.android.feature.home.ui

import dev.pikaia.android.feature.home.redux.HomeAction
import dev.pikaia.android.feature.home.redux.HomeState
import dev.pikaia.android.feature.home.redux.HomeStoreProxy
import dev.pikaia.android.lib.base.BaseViewModel

class HomeViewModel(
    storeProxy: HomeStoreProxy
) : BaseViewModel<HomeState, HomeAction>(storeProxy, HomeState())

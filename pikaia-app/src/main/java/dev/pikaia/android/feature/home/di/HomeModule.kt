package dev.pikaia.android.feature.home.di

import dev.pikaia.android.feature.home.redux.HomeStoreProxy
import dev.pikaia.android.feature.home.ui.HomeViewModel
import dev.pikaia.android.state.AppStore
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val homeModule = module {
    single {
        val store: AppStore = get()

        HomeStoreProxy(store)
    }
    viewModelOf(::HomeViewModel)
}
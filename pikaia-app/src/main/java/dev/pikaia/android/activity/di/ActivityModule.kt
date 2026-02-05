package dev.pikaia.android.activity.di

import dev.pikaia.android.activity.MainViewModel
import dev.pikaia.android.activity.redux.MainStoreProxy
import dev.pikaia.android.state.AppStore
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val activityModule = module {
    single {
        val store: AppStore = get()

        MainStoreProxy(store)
    }
    viewModelOf(::MainViewModel)
}

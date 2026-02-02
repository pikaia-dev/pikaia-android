package dev.pikaia.android.di

import dev.pikaia.android.lib.dispatcher.DefaultDispatcherProvider
import dev.pikaia.android.lib.dispatcher.DispatcherProvider
import dev.pikaia.android.state.AppStore
import org.koin.dsl.module

val appModule = module {
    single { AppStore(listOf()) }
    single<DispatcherProvider> { DefaultDispatcherProvider() }
}
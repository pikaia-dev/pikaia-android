package dev.pikaia.android.feature.devices.di

import dev.pikaia.android.R
import dev.pikaia.android.feature.devices.data.repository.DevicesRepository
import dev.pikaia.android.feature.devices.data.repository.DevicesRepositoryImpl
import dev.pikaia.android.feature.devices.domain.InitiateDeviceLink
import dev.pikaia.android.feature.devices.domain.LoadDevices
import dev.pikaia.android.feature.devices.domain.RefreshDeviceSession
import dev.pikaia.android.feature.devices.domain.RevokeDevice
import dev.pikaia.android.feature.devices.redux.DevicesActionReducer
import dev.pikaia.android.feature.devices.redux.DevicesStoreProxy
import dev.pikaia.android.feature.devices.redux.sideeffect.InitiateLinkSideEffect
import dev.pikaia.android.feature.devices.redux.sideeffect.LoadDevicesSideEffect
import dev.pikaia.android.feature.devices.redux.sideeffect.RefreshSessionSideEffect
import dev.pikaia.android.feature.devices.redux.sideeffect.RevokeDeviceSideEffect
import dev.pikaia.android.feature.devices.ui.DevicesViewModel
import dev.pikaia.android.state.AppStore
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val devicesModule = module {
    single<DevicesRepository> { DevicesRepositoryImpl(get(), get()) }
    factory { LoadDevices(get()) }
    factory { InitiateDeviceLink(get()) }
    factory { RevokeDevice(get()) }
    factory { RefreshDeviceSession(get()) }

    single {
        val store: AppStore = get()

        store.addReducer(DevicesActionReducer())

        store.addSideEffect(LoadDevicesSideEffect(store, get()))
        store.addSideEffect(InitiateLinkSideEffect(store, get()))
        store.addSideEffect(RevokeDeviceSideEffect(store, get()))
        store.addSideEffect(
            RefreshSessionSideEffect(
                store = store,
                refreshDeviceSession = get(),
                refreshedMessage = androidContext().getString(R.string.devices_session_refreshed)
            )
        )

        DevicesStoreProxy(store)
    }

    viewModelOf(::DevicesViewModel)
}

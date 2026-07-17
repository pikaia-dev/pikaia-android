package dev.pikaia.android.feature.login.di

import dev.pikaia.android.feature.login.data.repository.AuthRepository
import dev.pikaia.android.feature.login.data.repository.AuthRepositoryImpl
import dev.pikaia.android.feature.login.domain.AuthenticateMagicLink
import dev.pikaia.android.feature.login.domain.CreateOrganization
import dev.pikaia.android.feature.login.domain.EnterOrganization
import dev.pikaia.android.feature.login.domain.SendMagicLink
import dev.pikaia.android.feature.login.redux.LoginActionReducer
import dev.pikaia.android.feature.login.redux.LoginStoreProxy
import dev.pikaia.android.feature.login.redux.sideeffect.AuthenticateMagicLinkSideEffect
import dev.pikaia.android.feature.login.redux.sideeffect.EnterOrganizationSideEffect
import dev.pikaia.android.feature.login.redux.sideeffect.SendMagicLinkSideEffect
import dev.pikaia.android.feature.login.ui.LoginViewModel
import dev.pikaia.android.state.AppStore
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val loginModule = module {
    single<AuthRepository> { AuthRepositoryImpl(get(), get()) }
    factory { SendMagicLink(get()) }
    factory { AuthenticateMagicLink(get()) }
    factory { EnterOrganization(get()) }
    factory { CreateOrganization(get()) }

    single {
        val store: AppStore = get()

        store.addReducer(LoginActionReducer())

        store.addSideEffect(SendMagicLinkSideEffect(store, get()))
        store.addSideEffect(AuthenticateMagicLinkSideEffect(store, get()))
        store.addSideEffect(EnterOrganizationSideEffect(store, get(), get()))

        LoginStoreProxy(store)
    }

    viewModelOf(::LoginViewModel)
}

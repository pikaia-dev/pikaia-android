package dev.pikaia.android.feature.profile.di

import dev.pikaia.android.feature.profile.data.repository.ProfileRepository
import dev.pikaia.android.feature.profile.data.repository.ProfileRepositoryImpl
import dev.pikaia.android.feature.profile.domain.LoadProfile
import dev.pikaia.android.feature.profile.domain.Logout
import dev.pikaia.android.feature.profile.redux.ProfileActionReducer
import dev.pikaia.android.feature.profile.redux.ProfileStoreProxy
import dev.pikaia.android.feature.profile.redux.sideeffect.LoadProfileSideEffect
import dev.pikaia.android.feature.profile.redux.sideeffect.LoginSideEffect
import dev.pikaia.android.feature.profile.redux.sideeffect.LogoutSideEffect
import dev.pikaia.android.feature.profile.ui.ProfileViewModel
import dev.pikaia.android.state.AppStore
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val profileModule = module {
    single<ProfileRepository> { ProfileRepositoryImpl(get(), get()) }
    factory { LoadProfile(get()) }
    factory { Logout(get()) }

    single {
        val store: AppStore = get()

        val reducer = ProfileActionReducer()
        store.addReducer(reducer)

        val loadProfileSideEffect = LoadProfileSideEffect(store, get())
        store.addSideEffect(loadProfileSideEffect)
        val loginSideEffect = LoginSideEffect(store)
        store.addSideEffect(loginSideEffect)
        val logoutSideEffect = LogoutSideEffect(store, get())
        store.addSideEffect(logoutSideEffect)

        ProfileStoreProxy(store)
    }

    viewModelOf(::ProfileViewModel)
}
